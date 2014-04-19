package org.ninjatasks.mgmt

import akka.actor._
import scala.collection.mutable
import org.ninjatasks.work.{SleepJob, Job}
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.utils.ManagementConsts.{MGMT_TOPIC_NAME, WORK_TOPIC_NAME}

/**
 * Delegates work to remote worker managers
 * Created by Gilad Ber on 4/16/14.
 */
class JobDelegator extends TopicAwareActor(subscriptionTopic = MGMT_TOPIC_NAME, registrationTopic = WORK_TOPIC_NAME)
{
	private val jobQueue = new mutable.PriorityQueue[Job[_, _]]()
	private val jobRequestQueue = new mutable.Queue[ActorRef]()

	override def receive =
	{
		super.receive orElse myReceive
	}

	override def postRegister() = publish(JobMessage(SleepJob(5000, 1, 5, 1010100)))

	private[this] def myReceive: Actor.Receive =
	{
		case AggregateJobMessage(jobs) =>
			jobQueue ++= jobs
			if (!jobRequestQueue.isEmpty)
			{
				jobRequestQueue.dequeue ! JobMessage(jobQueue.dequeue())
			}

		case JobMessage(job) =>
			jobQueue += job
			if (!jobRequestQueue.isEmpty)
			{
				jobRequestQueue.dequeue() ! JobMessage(jobQueue.dequeue())
			}

		case JobRequest =>
			log.info("Received job request from {}", sender())
			jobRequestQueue += sender
			if (!jobQueue.isEmpty)
			{
				jobRequestQueue.dequeue ! JobMessage(jobQueue.dequeue())
			}

		case ResultMessage(result, id) =>
			println(result)

		case msg =>
			throw new IllegalArgumentException("Unknown message type received: " + msg + " from sender " + sender)
	}

}
