package org.ninjatasks.exec

import akka.actor._
import scala.collection.mutable
import org.ninjatasks.work.ManagedJob
import org.ninjatasks.mgmt._
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.mgmt.WorkDataMessage
import akka.actor.SupervisorStrategy.{Resume, Stop, Restart, Escalate}
import scala.concurrent.duration._

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
	/**
	 * Map used to store work data objects.
	 * Since work data objects may be of any kind, the value type here is Any.
	 */
	private[this] val workData = new mutable.HashMap[Long, Any]

	/**
	 * Queue of pending job requests made by this actor's children.
	 * At any time, there should be at most WORKER_NUM pending requests in this queue.
	 */
	private[this] val requestQueue = new mutable.Queue[ActorRef]

	/**
	 * Queue of job objects waiting to be processed by this actor's children.
	 * This queue should consist of at most WORKER_NUM pending jobs at any time.
	 */
	private[this] val jobQueue = new mutable.Queue[ManagedJob[_, _]]

	/**
	 * Map of WorkerContext objects, indexed by their owning ActorRef.
	 * WorkerContext objects are used to stop execution of currently running jobs,
	 * and are replaced every time a new job begins execution in the actor.
	 */
	private[this] val contexts = new mutable.HashMap[ActorRef, WorkerContext]

	override def preStart() =
	{
		super.preStart()
		contexts.clear()
		val childActors = (1 to WORKER_NUM) map (s => context.actorOf(Props[Worker], s"worker-$s"))
		childActors map (a => (a, WorkerContext(a, 0))) foreach contexts.+=
		childActors foreach requestQueue.+=
	}

	override def receive = super.receive orElse myReceive

	override def postRegister() =
	{
		requestQueue foreach (_ => publish(JobRequest))
		log.info("Sent {} job requests to job delegator", requestQueue.size)
	}

	override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2,
																																					withinTimeRange = 10 seconds)
	{
		case _: ActorInitializationException => Stop
		case _: ActorKilledException => Restart
		case _: DeathPactException => Restart
		case _: IllegalArgumentException => Resume
		case _: Exception => Restart
		case _: Throwable => Escalate
	}

	private[this] def myReceive: Actor.Receive =
	{
		case JobMessage(job) =>
			jobQueue += job
			if (!requestQueue.isEmpty)
			{
				send(jobQueue.dequeue(), requestQueue.dequeue())
			}

		case res: JobResultMessage =>
			val s = sender()
			contexts -= s
			requestQueue += s
			if (!jobQueue.isEmpty)
			{
				send(jobQueue.dequeue(), requestQueue.dequeue())
			}
			publish(res)

		case WorkCancelMessage(id) =>
			log.info("Received cancel message for work id {}", id)
			contexts.values foreach (ctx => ctx.signalStop(id))

		case WorkDataMessage(wId, data) => workData.put(wId, data)

		case msg =>
			throw new IllegalArgumentException("Unknown message type received: " + msg + " from sender " + sender)
	}

	private[this] def send[R, D](job: ManagedJob[R, D], target: ActorRef)
	{
		putWorkData(job.workId, job)
		val ctx = WorkerContext(target, job.workId)
		contexts update(target, ctx)
		target ! JobExecution(job, ctx.promise.future)
	}

	private[this] def putWorkData[D, R](workId: Long, job: ManagedJob[R, D])
	{
		val dataOption = workData.get(job.workId)
		dataOption map (work => job.workData = work.asInstanceOf[D])
	}
}
