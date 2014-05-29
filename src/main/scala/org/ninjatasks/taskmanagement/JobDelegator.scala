package org.ninjatasks.taskmanagement

import akka.actor._

import scala.collection.mutable
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.utils.ManagementConsts.{MGMT_TOPIC_NAME, WORK_TOPIC_NAME, JOBS_TOPIC_PREFIX, config, lookupBus}
import java.util.concurrent.atomic.AtomicLong
import org.ninjatasks.spi.ManagedJob
import org.ninjatasks.api.ManagementNotification

object JobDelegator
{
	val JOB_QUEUE_MAX_LENGTH: Long = config.getLong("ninja.delegator.job-queue-length")


}

/**
 * Delegates work to remote worker managers
 * Created by Gilad Ber on 4/16/14.
 */
class JobDelegator extends TopicAwareActor(receiveTopic = MGMT_TOPIC_NAME, targetTopic = WORK_TOPIC_NAME) with Stash
{

	import JobDelegator._

	/**
	 * Queue of jobs that are pending processing.
	 * The jobs are sorted in order of:
	 * 	1. priority
	 * 	2. insertion
	 * respectively.
	 */
	private[this] val jobQueue = mutable.PriorityQueue[ManagedJob[_, _]]()

	/**
	 * Queue of pending job requests from processing actors.
	 */
	private[this] val jobRequestQueue = mutable.Queue[ActorRef]()

	/**
	 * Serial number provider for incoming jobs.
	 * Used to provide insertion-ordering for the priority job queue.
	 */
	private[this] val serialProvider = new AtomicLong()

	private[this] def availableTaskSpace = JOB_QUEUE_MAX_LENGTH - jobQueue.size

	override def receive =
	{
		super.receive orElse preRegisterReceive
	}

	override def postRegister() =
	{
		context.become(postRegisterReceive)
		unstashAll()
		log.debug("Registration successful")
	}

	private[this] def addJob(job: ManagedJob[_,_]) =
	{
		job.serial = serialProvider.getAndIncrement
		jobQueue += job
	}

	private[this] def myReceive: Actor.Receive =
	{
		case AggregateJobMessage(jobs) =>
			jobs foreach addJob
			jobRequestQueue.
				take(Math.min(jobRequestQueue.size, jobQueue.size)).
				map(_ => (jobRequestQueue.dequeue(), jobQueue.dequeue())).
				foreach(r => sendJob(r._1, r._2))

		case JobMessage(job) =>
			addJob(job)
			jobRequestQueue.headOption foreach(_ => sendJob())

		case JobRequest =>
			jobRequestQueue += sender()
			jobQueue.headOption foreach(_ => sendJob())

		case res: JobResult =>
			lookupBus.publish(ManagementNotification(JOBS_TOPIC_PREFIX, res))

		case JobCapacityRequest =>
			val answer = availableTaskSpace
			sender() ! JobCapacity(answer)

		case wcr: WorkCancelRequest =>
			log.info("Received cancel message for work id {}", wcr.workId)
			filterJobQueue(wcr.workId)
			publish(wcr)

		case msg =>
			throw new IllegalArgumentException("Unknown message type received: " + msg + " from sender " + sender)
	}

	private[this] def preRegisterReceive: Receive = {
		case JobCapacityRequest => sender() ! JobCapacity(availableTaskSpace)
		case msg =>	stash()
	}

	private[this] def postRegisterReceive: Receive = super.receive orElse myReceive

	private[this] def filterJobQueue(workId: Long)
	{
		val tempQueue = mutable.PriorityQueue[ManagedJob[_, _]]()
		tempQueue ++= jobQueue
		jobQueue.clear()
		tempQueue filter (job => job.workId != workId) foreach jobQueue.+=
	}


	/**
	 * The purpose of this method is to reduce clutter for sending job messages to workers.
	 */
	private[this] def sendJob(): Unit =
	{
		sendJob(jobRequestQueue.dequeue(), jobQueue.dequeue())
	}

	private[this] def sendJob(to: ActorRef, job: ManagedJob[_, _]): Unit =
	{
		to ! JobMessage(job)
	}

}
