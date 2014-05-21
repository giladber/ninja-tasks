package org.ninjatasks.mgmt

import akka.actor._
import scala.collection.mutable
import org.ninjatasks.work.ManagedJob
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.utils.ManagementConsts.{MGMT_TOPIC_NAME, WORK_TOPIC_NAME, JOBS_TOPIC_PREFIX, config}
import java.util.concurrent.atomic.AtomicLong
import akka.contrib.pattern.DistributedPubSubMediator.Publish

object JobDelegator
{
	val JOB_QUEUE_MAX_LENGTH: Long = config.getLong("ninja.delegator.job-queue-length")
}

/**
 * Delegates work to remote worker managers
 * Created by Gilad Ber on 4/16/14.
 */
class JobDelegator extends TopicAwareActor(receiveTopic = MGMT_TOPIC_NAME, targetTopic = WORK_TOPIC_NAME)
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
		super.receive orElse myReceive
	}

	override def postRegister() =
	{
		println("Registration successful")
//		println("Sending job messages")
//		jobRequestQueue foreach (ref => self ! JobMessage(SleepJob(time = 5000, id = 1, priority = 5, workId = 1010100)))
//		context.system.scheduler.scheduleOnce(3 seconds)
//		{
//			println("Sent cancel msg")
//			self ! WorkCancelRequest(1010100)
//		}
	}

	private[this] def addJob(job: ManagedJob[_,_]) =
	{
		job.serial = serialProvider.getAndIncrement
		jobQueue += job
	}

	private[this] def myReceive: Actor.Receive =
	{
		case AggregateJobMessage(jobs) =>
			log.info("Received aggregate job message with {} jobs", jobs.size)
			jobs foreach addJob
			log.info("request q size: {}, job q size: {}", jobRequestQueue.size, jobQueue.size)
			jobRequestQueue.
				take(Math.min(jobRequestQueue.size, jobQueue.size)).
				map(_ => (jobRequestQueue.dequeue(), jobQueue.dequeue())).
				foreach(r => sendJob(r._1, r._2))

		case JobMessage(job) =>
			log.info("Received job message with job: {}", job)
			addJob(job)
			jobRequestQueue.headOption foreach(_ => sendJob())

		case JobRequest =>
			log.info("Received job request from {}", sender())
			jobRequestQueue += sender()
			jobQueue.headOption foreach(_ => sendJob())

		case res: JobResult =>
			log.info("received job result for job id {}", res.jobId)
			mediator ! Publish(JOBS_TOPIC_PREFIX + res.workId, res)

		case JobCapacityRequest =>
			val answer = availableTaskSpace
			log.debug("sending available task space {}", answer)
			sender() ! JobCapacity(answer)

		case wcr: WorkCancelRequest =>
			log.info("Received cancel message for work id {}", wcr.workId)
			publish(wcr)

		case msg =>
			throw new IllegalArgumentException("Unknown message type received: " + msg + " from sender " + sender)
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
		log.info("sending job id {}", job.id)
		to ! JobMessage(job)
	}

}
