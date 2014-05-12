package org.ninjatasks.mgmt

import akka.actor._
import scala.collection.mutable
import org.ninjatasks.work.ExecutableJob.toManaged
import org.ninjatasks.work.{SleepJob, ManagedJob}
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.utils.ManagementConsts.{MGMT_TOPIC_NAME, WORK_TOPIC_NAME}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object JobDelegator
{
	val JOB_QUEUE_MAX_LENGTH: Long = 1E6.toLong
}

/**
 * Delegates work to remote worker managers
 * Created by Gilad Ber on 4/16/14.
 */
class JobDelegator extends TopicAwareActor(receiveTopic = MGMT_TOPIC_NAME, targetTopic = WORK_TOPIC_NAME)
{

	import JobDelegator._

	private[this] val jobQueue = mutable.PriorityQueue[ManagedJob[_, _]]()
	private[this] val jobRequestQueue = mutable.Queue[ActorRef]()
	private[this] val serialProvider = new AtomicLong()

	private[this] def freeTasks = JOB_QUEUE_MAX_LENGTH - jobQueue.size

	override def receive =
	{
		super.receive orElse myReceive
	}

	override def postRegister() =
	{
		println("Sending job messages")
		jobRequestQueue foreach (ref => self ! JobMessage(SleepJob(time = 5000, id = 1, priority = 5, workId = 1010100)))
		context.system.scheduler.scheduleOnce(3 seconds)
		{
			println("Sent cancel msg")
			self ! WorkCancelMessage(1010100)
		}
	}

	private[this] def myReceive: Actor.Receive =
	{
		case AggregateJobMessage(jobs) =>
			log.info("Received aggregate job message with {} jobs", jobs.size)
			jobs foreach (job =>
			{
				job.serial = serialProvider.getAndIncrement
				jobQueue += job
			})
			jobRequestQueue.headOption foreach(_ => send(jobRequestQueue.dequeue(), jobQueue.dequeue()))

		case JobMessage(job) =>
			log.info("Received job message with job: {}", job)
			job.serial = serialProvider.getAndIncrement
			jobQueue += job
			jobRequestQueue.headOption foreach(_ => send(jobRequestQueue.dequeue(), jobQueue.dequeue()))

		case JobRequest =>
			log.info("Received job request from {}", sender())
			jobRequestQueue += sender()
			jobQueue.headOption foreach(_ => send(jobRequestQueue.dequeue(), jobQueue.dequeue()))

		case js: JobSuccess[_] =>
			log.debug("Received result: " + js.res)
			context.parent ! js

		case JobCapacityRequest => sender() ! JobCapacity(freeTasks)

		case wcm: WorkCancelMessage =>
			log.info("Received cancel message for work id {}", wcm.workId)
			publish(wcm)

		case msg =>
			throw new IllegalArgumentException("Unknown message type received: " + msg + " from sender " + sender)
	}

	private[ninjatasks] implicit def toJobMsg[R, D](job: ManagedJob[R, D]): JobMessage = JobMessage(job)

	/**
	 * The purpose of this method is to reduce clutter for sending job messages to workers.
	 * It uses the toJobMsg implicit conversion to avoid having to write JobMessage(...) each time.
	 * @param to target
	 * @param msg job msg
	 */
	private[this] def send(to: ActorRef, msg: JobMessage): Unit =
	{
		log.info("Sending job message {} to {}", msg, to)
		to ! msg
	}

}
