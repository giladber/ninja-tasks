package org.ninjatasks.taskmanagement

import akka.actor._

import akka.actor.SupervisorStrategy.{Restart, Escalate}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.pattern.ask
import akka.routing.RoundRobinPool
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import org.ninjatasks.utils.ManagementConsts._
import org.ninjatasks.api.ManagementNotification
import org.ninjatasks.spi._
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


object WorkManager
{
	private[ninjatasks] val routerStrategy = OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 2 seconds)
	{
		case _: ActorInitializationException => Escalate
		case _: Exception => Restart
	}
	private[ninjatasks] val numCombiners = config.getInt("ninja.work-manager.num-combiners")
	private[ninjatasks] val maxWorkQueueSize = config.getLong("ninja.work-manager.max-work-queue-size")
	private[ninjatasks] val maxCombineLatency = config.getLong("ninja.work-manager.max-combine-latency")
	private[ninjatasks] val combineDuration = maxCombineLatency.millis
}

import WorkManager._

/**
 * Class responsible for managing all work-related data - storing this data and producing job sets for processing.
 * Created by Gilad Ber on 4/17/14.
 */
private[ninjatasks] class WorkManager extends Actor with ActorLogging
{

	/**
	 * Job delegator, responsible for delegating job requests to remote worker managers.
	 */
	private[this] val delegator = context.actorOf(Props[JobDelegator], JOB_DELEGATOR_ACTOR_NAME)
	context.actorOf(Props(classOf[JobExtractor], self, delegator), JOB_EXTRACTOR_ACTOR_NAME)


	private[this] val combineRouter = context.actorOf(RoundRobinPool(numCombiners).
																											withSupervisorStrategy(routerStrategy).
																											props(Props[CombinePerformer]), "combiners-router")

	private[this] val mediator = DistributedPubSubExtension(context.system).mediator

	/**
	 * Priority queue which holds all job-producing iterators which are waiting to be processed.
	 * These iterators are sorted by work priority and then insertion order.
	 */
	private[this] val pendingWork = new mutable.PriorityQueue[JobSetIterator[_, _]]

	/**
	 * Map holding all work objects which have been sent for execution, along with their
	 * number of tasks remaining to be processed.
	 * The key of the map is the work object's id.
	 */
	private[this] val workData = new mutable.HashMap[UUID, (Work[_, _, _], Long)]

	/**
	 * Atomic object which produces serial numbers for incoming work objects.
	 * The serial numbers are needed for sorting in the priority queue.
	 */
	private[this] val serialProducer = new AtomicLong()


	override def preStart() {
		lookupBus.subscribe(self, JOBS_TOPIC_PREFIX)
	}

	override def postStop() {
		lookupBus.unsubscribe(self)
	}

	override def receive =
	{
		case work: Work[_, _, _] =>
			if (pendingWork.size >= maxWorkQueueSize) {
				rejectWork(work.id)
			}	else {
				acceptWork(work)
			}

		case JobSetRequest(amount) =>
			pendingWork.headOption foreach (head => delegator ! AggregateJobMessage(take(amount)))

		case wcm: WorkCancelRequest =>
			delegator ! wcm
			removeWork(wcm.workId, WorkCancelled(wcm.workId, wcm.reason))

		case jf: JobFailure =>
			delegator ! WorkCancelRequest(jf.workId, s"Job ${jf.jobId} failed: ${jf.reason}")
			removeWork(jf.workId, WorkFailed(jf.workId, jf.reason))

		case js: JobSuccess[_] =>
			log.debug("Received job success for job {}", js.jobId)
			val valueOption = workData.get(js.workId) map (v => (v._1, v._2 - 1)) //decrease rem. job count
			val entryOption = valueOption map (v => (v._1.id, (v._1, v._2))) //rebuild work entry
			entryOption foreach workData.+=
			valueOption map (v => v._1) foreach (w => receiveResult(w, js))

		case CombineAck(wId) =>
			workData.get(wId) filter(_._2 == 0) map(_._1) foreach (work => removeWork(work.id, WorkFinished(work.id, work.result)))

		case other =>
			throw new IllegalArgumentException(s"Received unexpected message: $other")
	}

	def publishData(work: Work[_, _, _]): Unit = {
		work.data foreach {
			x => mediator ! Publish(WORK_TOPIC_NAME, WorkDataMessage(work.id, x))
		}
	}

	def acceptWork(work: Work[_, _, _])
	{
		val creator = work.creator
		workData.put(work.id, (work, creator.jobNum))
		pendingWork += JobSetIterator(creator, serialProducer.getAndIncrement, work.id, work.priority)
		publishData(work)
		sender() ! WorkStarted(work.id)
		log.info("received work with id {}", work.id)
	}

	private[this] def receiveResult(work: Work[_, _, _], js: JobSuccess[_]) =
	{
		import scala.concurrent.ExecutionContext.Implicits.global

		val f = combineRouter.ask(CombineRequest(work, js))(combineDuration)
		f foreach {
			case ack: CombineAck => self ! ack
			case e: Exception => self ! WorkCancelRequest(work.id, s"Failed combining job ${js.jobId}: $e")
		}
	}

	private[this] def removeWork(workId: UUID, msg: WorkResult) =
	{
		filterWorkQueue(workId)
		lookupBus.publish(ManagementNotification(WORK_TOPIC_PREFIX, msg))
		mediator ! Publish(WORK_TOPIC_NAME, WorkDataRemoval(workId))
		workData -= workId
	}

	private[this] def rejectWork(workId: UUID)
	{
		log.warning("Rejecting work {} due to capacity max", workId)
		sender() ! WorkRejected(workId)
	}


	private[this] def filterWorkQueue(workId: UUID)
	{
		val tempWorkQueue = mutable.PriorityQueue[JobSetIterator[_, _]]()
		tempWorkQueue ++= pendingWork
		pendingWork.clear()
		tempWorkQueue filter (it => it.workId != workId) foreach pendingWork.+=
	}

	import scala.collection.immutable

	/**
	 * Creates and returns at most n new, unprocessed job objects to be processed.
	 * @param maxTasks maximum number of objects to process
	 * @return Seq consisting of at most n unprocessed job objects
	 */
	private[this] def take(maxTasks: Long) = takeRec(maxTasks, immutable.Seq.empty[ManagedJob[_, _]])

	@tailrec
	private[this] def takeRec(n: Long, jobs: immutable.Seq[ManagedJob[_, _]]): immutable.Seq[ManagedJob[_, _]] =
	{
		if (n <= 0 || pendingWork.isEmpty)
		{
			jobs
		}
		else
		{
			val head = pendingWork.head
			val nextJobs = Try(head.next(n))
			if (!head.hasNext)
			{
				pendingWork.dequeue()
			}

			nextJobs match {
				case Success(more) =>
					val currentJobs = jobs ++ more
					takeRec(n - currentJobs.size, currentJobs)

				case Failure(ex) =>
					self ! WorkCancelRequest(head.workId, s"Failed creating jobs: $ex}")
					jobs.filter(_.workId != head.workId)
			}
		}
	}

}