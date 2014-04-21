package org.ninjatasks.exec

import akka.actor._
import scala.collection.mutable
import org.ninjatasks.work.Job
import org.ninjatasks.mgmt._
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.mgmt.JobMessage
import org.ninjatasks.mgmt.ResultMessage
import org.ninjatasks.mgmt.WorkDataMessage

object WorkerManager
{
	val WORKER_NUM = System.getProperty("workerNum", "-1") match
	{
		case "-1" => Runtime.getRuntime.availableProcessors()
		case _ => System.getProperty("workerNum").toInt
	}
}


import org.ninjatasks.utils.ManagementConsts.{WORK_TOPIC_NAME, MGMT_TOPIC_NAME}
import WorkerManager._

/**
 * The WorkerManager manages job executing workers.
 * There should only be one WorkerManager per machine.
 * Created by Gilad Ber on 4/15/14.
 */
class WorkerManager extends TopicAwareActor(receiveTopic = WORK_TOPIC_NAME, targetTopic = MGMT_TOPIC_NAME)
{
	private[this] val workData = new mutable.HashMap[Long, Any]()
	private[this] val requestQueue = new mutable.Queue[ActorRef]()
	private[this] val jobQueue = new mutable.PriorityQueue[Job[_, _]]()

	override def preStart() =
	{
		super.preStart()
		(1 to WORKER_NUM) map (s => context.actorOf(Props[Worker], s"worker-$i")) foreach requestQueue.+=
	}

	override def receive =
	{
		super.receive orElse myReceive
	}

	override def postRegister() = publish(JobRequest)

	private[this] def myReceive: Actor.Receive =
	{
		case JobMessage(job) =>
			jobQueue += job
			if (!requestQueue.isEmpty)
			{
				requestQueue.dequeue ! jobQueue.dequeue
			}

		case res: ResultMessage[_] =>
			requestQueue += sender
			if (!jobQueue.isEmpty)
			{
				requestQueue.dequeue ! jobQueue.dequeue
			}
			publish(res)

		case JobRequest =>
			requestQueue += sender
			publish(JobRequest)

		case WorkDataMessage(wId, data) => workData.put(wId, data)

		case msg =>
			throw new IllegalArgumentException("Unknown message type received: " + msg + " from sender " + sender)
	}
}
