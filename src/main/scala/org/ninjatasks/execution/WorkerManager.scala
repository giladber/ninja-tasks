package org.ninjatasks.execution

import akka.actor._

import scala.collection.mutable
import org.ninjatasks.taskmanagement._
import org.ninjatasks.cluster.TopicAwareActor
import org.ninjatasks.taskmanagement.WorkDataMessage
import akka.actor.SupervisorStrategy.{Resume, Stop, Restart, Escalate}
import scala.concurrent.duration._
import scala.language.postfixOps
import org.ninjatasks.utils.ManagementConsts
import org.ninjatasks.spi.ManagedJob
import java.util.UUID

object WorkerManager
{
	import ManagementConsts.config

	val workersAmountPath: String = "ninja.workers.amount"
	val WORKER_NUM = if (config.hasPath(workersAmountPath)) {
		config.getInt(workersAmountPath)
	} else {
		Runtime.getRuntime.availableProcessors()
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
	private[this] val workData = new mutable.HashMap[UUID, Any]

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

	/**
	 * Before starting this worker, we must creats its child actors.
	 * The number of child actors is determined either by configuration, or, as default value,
	 * by the number of available processors as returned by Runtime.availableProcessors
	 * Upon starting each child actor, a job request is sent on its behalf to this actor,
	 * seeing as it will have no jobs to process.
	 */
	override def preStart() =
	{
		super.preStart()
		contexts.clear()
		val childActors = (1 to WORKER_NUM) map (i => context.actorOf(Props[Worker], s"worker-$i"))
		childActors map (a => (a, WorkerContext(a, UUID.randomUUID()))) foreach contexts.+=
		childActors foreach requestQueue.+=
	}

	override def receive = super.receive orElse myReceive

	override def postRegister() =
	{
		requestQueue foreach (_ => publish(JobRequest))
		log.debug("Sent {} job requests to job delegator", requestQueue.size)
	}

	override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2,
																																					withinTimeRange = 2 seconds)
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
			requestQueue.headOption foreach(_ => send(jobQueue.dequeue(), requestQueue.dequeue()))
 
		case res: JobResult =>
			val s = sender()
			contexts -= s
			requestQueue += s
			jobQueue.headOption foreach(_ => send(jobQueue.dequeue(), requestQueue.dequeue()))
			publish(res)
			publish(JobRequest)

		case WorkCancelRequest(id) =>
			log.info("Received cancel message for work id {}", id)
			contexts.values filter (ctx => ctx.workId == id) foreach (_.signalStop())

		case WorkDataMessage(wId, data) =>
			log.info("received work data message for work {}", wId)
			workData.put(wId, data)

		case WorkDataRemoval(wId) =>
			log.info("Received work data remove msg for work {}", wId)
		  workData -= wId

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

	private[this] def putWorkData[D, R](workId: UUID, job: ManagedJob[R, D])
	{
		val dataOption = workData.get(workId)
		dataOption foreach(work => job.workData = work.asInstanceOf[D])
	}
}
