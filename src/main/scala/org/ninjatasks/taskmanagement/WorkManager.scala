package org.ninjatasks.taskmanagement

import akka.actor._
import org.ninjatasks.utils.ManagementConsts._
import scala.collection.mutable
import akka.contrib.pattern.DistributedPubSubExtension
import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import org.ninjatasks.utils.ManagementConsts
import org.ninjatasks.spi._
import java.util.UUID
import akka.pattern.ask
import scala.concurrent.duration._
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.routing.RoundRobinPool
import org.ninjatasks.api.ManagementNotification
import akka.actor.SupervisorStrategy.{Restart, Escalate}

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

	private[this] val routerStrategy = OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 2 seconds)
	{
		case _: ActorInitializationException => Escalate
		case _: Exception => Restart
	}
	private[this] val combineRouter = context.actorOf(RoundRobinPool(2).
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
	private[this] val workData = new mutable.HashMap[UUID, (FuncWork[_, _, _], Long)]

	/**
	 * Atomic object which produces serial numbers for incoming work objects.
	 * The serial numbers are needed for sorting in the priority queue.
	 */
	private[this] val serialProducer = new AtomicLong()

	private[this] val maxWorkQueueSize = ManagementConsts.config.getLong("ninja.work-manager.max-work-queue-size")

	override def preStart() {
		lookupBus.subscribe(self, JOBS_TOPIC_PREFIX)
	}

	override def postStop() {
		lookupBus.unsubscribe(self)
	}

	override def receive =
	{
		case work: FuncWork[_, _, _] =>
			if (pendingWork.size >= maxWorkQueueSize) {
				rejectWork(work.id)
			}	else {
				acceptWork(work)
			}

		case JobSetRequest(amount) =>
			pendingWork.headOption foreach (head =>
			{
				val jobs = take(amount)
				delegator ! AggregateJobMessage(jobs)
			})

		case wcm: WorkCancelRequest =>
			delegator ! wcm
			removeWork(wcm.workId, WorkCancelled(wcm.workId))

		case jf: JobFailure =>
			delegator ! WorkCancelRequest(jf.workId)
			removeWork(jf.workId, WorkFailed(jf.workId, jf.reason))

		case js: JobSuccess[_] =>
			log.debug("Received job success for job {}", js.jobId)
			val valueOption = workData.get(js.workId) map (v => (v._1, v._2 - 1))
			val entryOption = valueOption map (v => (v._1.id, (v._1, v._2)))
			entryOption foreach workData.+=
			valueOption map (v => v._1) foreach (w => receiveResult(w, js))
			valueOption filter (v => v._2 == 0) map (v => v._1) foreach (work => removeWork(work.id, WorkFinished(work.id,
																																																						work.result)))
		case other =>
			throw new IllegalArgumentException(s"Received unexpected message: $other")
	}


	def acceptWork(work: FuncWork[_, _, _])
	{
		val creator = work.creator
		workData.put(work.id, (work, creator.jobNum))
		pendingWork += JobSetIterator(creator, serialProducer.getAndIncrement, work.id, work.priority)
		mediator ! Publish(WORK_TOPIC_NAME, WorkDataMessage(work.id, work.data))
		sender() ! WorkStarted(work.id)
		log.info("received work with id {}", work.id)
	}

	private[this] def receiveResult(work: FuncWork[_, _, _], js: JobSuccess[_]) =
	{
		import scala.concurrent.ExecutionContext.Implicits.global

		val f = combineRouter.ask(CombineRequest(work, js))(500.millis)
		f foreach {
			case ack: CombineAck => //do nothing, actually...
			case e: Exception => self ! WorkCancelRequest(work.id)
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
	 * @return Set consisting of at most n unprocessed job objects
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
			val nextJobs: immutable.Seq[ManagedJob[_, _]] = jobs ++ head.next(n)
			if (!head.hasNext)
			{
				pendingWork.dequeue()
			}
			takeRec(n - nextJobs.size, nextJobs)
		}
	}
}