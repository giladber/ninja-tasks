package org.ninjatasks

import akka.actor._

import akka.pattern.ask
import org.ninjatasks.utils.ManagementConsts.{system, WORK_MGR_ACTOR_NAME, WORK_EXECUTOR_ACTOR_NAME}
import org.ninjatasks.mgmt._
import org.ninjatasks.work.Work
import scala.concurrent.{Await, Promise, Future}
import scala.concurrent.duration._
import scala.collection.mutable
import org.ninjatasks.mgmt.WorkFailed
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{SubscribeAck, UnsubscribeAck, Unsubscribe, Subscribe}
import org.ninjatasks.utils.ManagementConsts
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

case class WorkCancelledException(workId: Long) extends RuntimeException

/**
 * This class initiates the ninja tasks work\job management system.
 * Created by Gilad Ber on 5/18/2014.
 */
object JobManagementSubsystem
{
	val executor: ActorRef = system.actorOf(Props[WorkExecutor], WORK_EXECUTOR_ACTOR_NAME)
	private[ninjatasks] val workManager = system.actorOf(Props[WorkManager], WORK_MGR_ACTOR_NAME)

	def start(): Unit =
	{
		/*just init this class*/
	}

	/**
	 * Public non-actor API.
	 * This call may introduce blocking.
	 * @param work work to be executed
	 * @param timeout timeout until the work is to be cancelled
	 * @tparam T work intermediate result type (result type of jobs)
	 * @tparam D work data type
	 * @tparam R work final result type
	 * @return a future indicating either the failure reason or the work's result
	 */
	def execute[T, D, R](work: Work[T, D, R])(implicit timeout: Duration): Future[R] =
	{
		val message: (Work[T, D, R], Duration) = (work, timeout)
		val submitFuture: Future[Any] = executor.ask(message)(50 millis)

		val result = submitFuture map
			{
				case workResultFuture: Future[R] => workResultFuture
				case other => Future.failed(new IllegalArgumentException(s"Did not expect $other"))
			}

		Await.result(result, 50 millis)
	}

	def cancel(workId: Long): Unit = executor ! WorkCancelRequest(workId)
}

/**
 * Entry point into the job management subsystem.
 *
 */
class WorkExecutor extends Actor with ActorLogging
{

	import JobManagementSubsystem.workManager

	private[this] val cancels = mutable.Map[Long, Cancellable]()
	private[this] val promises = mutable.Map[Long, Promise[_ <: Any]]()
	private[this] val mediator = DistributedPubSubExtension(system).mediator

	private[this] type workTO = (Work[_, _, _], FiniteDuration)

	override def receive: Receive =
	{

		case workWithTimeout: workTO =>
			log.info("Received work {} with timeout {}", workWithTimeout._1.id, workWithTimeout._2)
			val future = send(workWithTimeout._1)(workWithTimeout._2)
			sender() ! future

		case WorkCancelRequest(id) =>
			log.info("Received cancel request for work {}", id)
			cancelWork(id)

		case res: WorkResult =>
			log.info("received work result")
			acceptWork(res)

		case UnsubscribeAck(from) => log.info("Received unsubscribe ack from {}", from)

		case SubscribeAck(to) => log.info("Received subscribe ack to {}", to)
	}

	private[this] def cancelWork(id: Long): Unit =
	{
		promises.get(id) foreach(p => p.failure(WorkCancelledException(id)))
		clearWorkData(id)
		workManager ! WorkCancelRequest(id)
	}

	private[this] def acceptWork(result: WorkResult): Unit =
	{
		val resId = result match
		{
			case WorkFinished(id, res) =>
				def applyResult[T: ClassTag](p: Promise[T]): Unit = p.success(res.asInstanceOf[T])
				promises.get(id) foreach (p => applyResult(p))
				id

			case WorkCancelled(wId) =>
				promises.get(wId) foreach (p => p.failure(WorkCancelledException(wId)))
				wId

			case WorkFailed(wId, reason) =>
				promises.get(wId) foreach (p => p.failure(reason))
				wId
		}

		cancels.get(resId) foreach (_.cancel())
		clearWorkData(resId)
	}

	private[this] def clearWorkData(id: Long): Unit =
	{
		cancels -= id
		promises -= id
		mediator ! Unsubscribe(ManagementConsts.WORK_TOPIC_PREFIX + id, self)
	}


	private[this] def send[T, D, R](work: Work[T, D, R])(implicit timeout: FiniteDuration): Future[R] =
	{
		def scheduler = context.system.scheduler
		val id = work.id
		mediator ! Subscribe(ManagementConsts.WORK_TOPIC_PREFIX + id, self)
		workManager ! work
		log.info("sent work {} to manager", id)

		if (timeout > (0 seconds))
		{
			val cancellable = scheduler.scheduleOnce(delay = timeout)
			{
				log.info("Sending cancel request for work {}", id)
				self ! WorkCancelRequest(workId = id)
			}
			cancels.put(id, cancellable)
			log.info("Added cancellable to work id {}", id)
		}

		val p = Promise[R]()
		promises.put(id, p)
		p.future
	}
}
