package org.ninjatasks.mgmt

import akka.actor.{Props, ActorLogging, Actor}
import org.ninjatasks.work.{ManagedJob, JobSetIterator, Work}
import org.ninjatasks.utils.ManagementConsts.WORK_TOPIC_NAME
import scala.collection.mutable
import akka.contrib.pattern.DistributedPubSubExtension
import java.util.concurrent.atomic.AtomicLong
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import scala.annotation.tailrec

/**
 * Class responsible for managing all work-related data - storing this data and producing job sets for processing.
 * Created by Gilad Ber on 4/17/14.
 */
private[ninjatasks] class WorkManager extends Actor with ActorLogging
{
	/**
	 * Job delegator, responsible for delegating job requests to remote worker managers.
	 */
	private[this] val delegator = context.actorOf(Props[JobDelegator])

	private[this] val mediator = DistributedPubSubExtension(context.system).mediator

	/**
	 * Priority queue which holds all job-producing iterators which are waiting to be processed.
	 * These iterators are sorted by work priority and then insertion order.
	 */
	private[this] val pendingWork = new mutable.PriorityQueue[JobSetIterator[_, _]]

	/**
	 * Map holding all work objects which have been sent for execution.
	 */
	private[this] val workData = new mutable.HashMap[Long, Work[_, _]]

	/**
	 * Atomic object which produces serial numbers for incoming work objects.
	 */
	private[this] val serialProducer = new AtomicLong()

	context.actorOf(Props(classOf[JobExtractor], self, delegator))

	override def receive =
	{
		case work: Work[_, _] =>
			workData put(work.id, work)
			pendingWork += JobSetIterator(work.creator, serialProducer.getAndIncrement)
			mediator ! Publish(WORK_TOPIC_NAME, WorkDataMessage(work.id, work.data))

		case JobSetRequest(amount) =>
			if (!pendingWork.isEmpty) sender() ! take(amount)

		case wcm: WorkCancelMessage =>
			filterCancelledWork(wcm.workId)
			delegator ! wcm
	}


	private[this] def filterCancelledWork(workId: Long)
	{
		val tempWorkQueue = mutable.PriorityQueue[JobSetIterator[_, _]]()
		tempWorkQueue ++= pendingWork
		pendingWork.clear()
		tempWorkQueue filter (it => it.producer.work.id != workId) foreach pendingWork.+=
	}

	/**
	 * Creates and returns at most n new, unprocessed job objects to be processed.
	 * @param maxTasks maximum number of objects to process
	 * @return Set consisting of at most n unprocessed job objects
	 */
	private[this] def take(maxTasks: Long): Set[_ >: ManagedJob[_, _]] = takeRec(maxTasks, Set.empty)

	@tailrec
	private[this] def takeRec(n: Long, jobs: Set[_ >: ManagedJob[_, _]]): Set[_ >: ManagedJob[_, _]] =
	{
		if (n <= 0 || pendingWork.isEmpty)
		{
			jobs
		}
		else
		{
			val head = pendingWork.head
			val nextJobs: Set[_ >: ManagedJob[_, _]] = jobs ++ head.next(n)
			if (!head.hasNext)
			{
				pendingWork.dequeue()
			}
			takeRec(n - nextJobs.size, nextJobs)
		}
	}
}