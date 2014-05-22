package org.ninjatasks.mgmt

import akka.actor.{Props, ActorLogging, Actor}
import org.ninjatasks.work.{ManagedWork, ManagedJob, JobSetIterator, Work}
import org.ninjatasks.utils.ManagementConsts.{WORK_TOPIC_NAME, JOBS_TOPIC_PREFIX, WORK_TOPIC_PREFIX, JOB_EXTRACTOR_ACTOR_NAME, JOB_DELEGATOR_ACTOR_NAME}
import scala.collection.mutable
import akka.contrib.pattern.DistributedPubSubExtension
import java.util.concurrent.atomic.AtomicLong
import akka.contrib.pattern.DistributedPubSubMediator._
import scala.annotation.tailrec
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.contrib.pattern.DistributedPubSubMediator.UnsubscribeAck
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.contrib.pattern.DistributedPubSubMediator.SubscribeAck

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
	private[this] val workData = new mutable.HashMap[Long, (ManagedWork[_, _, _], Long)]

	/**
	 * Atomic object which produces serial numbers for incoming work objects.
	 * The serial numbers are needed for sorting in the priority queue.
	 */
	private[this] val serialProducer = new AtomicLong()

	context.actorOf(Props(classOf[JobExtractor], self, delegator), JOB_EXTRACTOR_ACTOR_NAME)

	override def receive =
	{
		case work: Work[_, _, _] =>
			val wrapped = ManagedWork(work)
			workData.put(wrapped.id, (wrapped, wrapped.jobNum))
			pendingWork += JobSetIterator(wrapped.creator, serialProducer.getAndIncrement)
			mediator ! Subscribe(JOBS_TOPIC_PREFIX + wrapped.id, self)
			mediator ! Publish(WORK_TOPIC_NAME, WorkDataMessage(wrapped.id, wrapped.data))
			sender() ! WorkStarted(wrapped.id)

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
			log.warning("Job {} failed! reason: {}", jf.jobId, jf.reason)
			delegator ! WorkCancelRequest(jf.workId)
			removeWork(jf.workId, WorkFailed(jf.workId, jf.reason))

		case js: JobSuccess[_] =>
			val valueOption = workData.get(js.workId) map (v => (v._1, v._2 - 1))
			val entryOption = valueOption map (v => (v._1.id, (v._1, v._2)))
			entryOption foreach workData.+=
			valueOption map (v => v._1) foreach (w => receiveResult(w, js))
			valueOption filter (v => v._2 == 0) map (v => v._1) foreach (work => removeWork(work.id, WorkFinished(work.id,
																																																						work.result)))

		case SubscribeAck(s) => log.info("Subscribed to topic {}", s.topic)

		case UnsubscribeAck(u) => log.info("Unsubscribed from topic {}", u.topic)
	}

	private[this] def receiveResult[T](work: ManagedWork[T, _, _], js: JobSuccess[_]) =
		work.update(js.res.asInstanceOf[T])

	private[this] def removeWork(workId: Long, msg: WorkResult) =
	{
		filterWorkQueue(workId)
		mediator ! Publish(WORK_TOPIC_PREFIX + workId, msg)
		mediator ! Publish(WORK_TOPIC_NAME, WorkDataRemoval(workId))
		mediator ! Unsubscribe(JOBS_TOPIC_PREFIX + workId, self)
		workData -= workId
	}


	private[this] def filterWorkQueue(workId: Long)
	{
		val tempWorkQueue = mutable.PriorityQueue[JobSetIterator[_, _]]()
		tempWorkQueue ++= pendingWork
		pendingWork.clear()
		tempWorkQueue filter (it => it.producer.work.id != workId) foreach pendingWork.+=
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