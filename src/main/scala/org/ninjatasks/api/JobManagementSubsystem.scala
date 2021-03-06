package org.ninjatasks.api

import akka.actor._

import akka.pattern.ask
import org.ninjatasks.utils.ManagementConsts._
import org.ninjatasks.taskmanagement._
import scala.concurrent.{Await, Promise, Future}
import scala.concurrent.duration._
import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import akka.event.{EventBus, LookupClassification}
import org.ninjatasks.spi.Work
import java.util.UUID
import org.ninjatasks.utils.ManagementConsts
import org.ninjatasks.taskmanagement.WorkCancelRequest
import org.ninjatasks.taskmanagement.WorkCancelled
import org.ninjatasks.taskmanagement.WorkFinished
import org.ninjatasks.taskmanagement.WorkFailed


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

	type WorkResultFuture[R] = Future[Either[WorkResult, R]]
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
	def execute[T, D, R](work: Work[T, D, R])(implicit timeout: Duration): Future[Either[WorkResult, R]] =
	{
		val message: (Work[T, D, R], Duration) = (work, timeout)
		val submitFuture: Future[Any] = executor.ask(message)(50 millis)

		type TypedWorkResult = WorkResultFuture[R]
		val result = submitFuture map
			{
				case workResultFuture: TypedWorkResult => workResultFuture
				case other => Future.failed(new IllegalArgumentException(s"Did not expect $other"))
			}

		Await.result(result, 50 millis)
	}

	def cancel(workId: UUID): Unit = executor ! WorkCancelRequest(workId, "Cancelled by user")
}

/**
 * Entry point into the job management subsystem.
 *
 */
class WorkExecutor extends Actor with ActorLogging
{

	import JobManagementSubsystem.workManager

	private[this] val cancels = mutable.Map[UUID, Cancellable]()
	private[this] val promises = mutable.Map[UUID, WorkPromise[_ <: Any]]()
	private[this] val lookupBus = ManagementConsts.lookupBus

	private[this] type WorkAndTimeout = (Work[_, _, _], FiniteDuration)

	override def preStart() {
		lookupBus.subscribe(self, WORK_TOPIC_PREFIX)
	}

	override def postStop() {
		lookupBus.unsubscribe(self)
	}

	override def receive: Receive =
	{

		case workWithTimeout: WorkAndTimeout =>
			log.info("Received work with timeout {}", workWithTimeout._2)
			val future = send(workWithTimeout._1)(workWithTimeout._2)
			sender() ! future

		case WorkCancelRequest(id, reason) =>
			log.info("Received cancel request for work {}", id)
			cancelWork(id, reason)

		case res: WorkResult =>
			log.debug("received work result")
			acceptResult(res)
	}

	private[this] def cancelWork(id: UUID, reason: String): Unit =
	{
		promises.get(id) foreach(p => p.success(Left(WorkCancelled(id, reason))))
		clearWorkData(id)
		workManager ! WorkCancelRequest(id, reason)
	}

	type WorkPromise[A] = Promise[Either[WorkResult, A]]
	private[this] def acceptResult(result: WorkResult): Unit =
	{
		val wId = result.workId
		result match
		{
			case WorkFinished(id, res) =>
				def applyResult[T: ClassTag](p: WorkPromise[T]): Unit = p.success(Right(res.asInstanceOf[T]))
				promises.get(wId) foreach (p => applyResult(p))

			case WorkFailed(id, reason) =>
				promises.get(wId) foreach (p => p.failure(reason))

			case other: WorkResult =>
				promises.get(wId) foreach (p => p.success(Left(other)))
		}

		cancels.get(wId) foreach (_.cancel())
		clearWorkData(wId)
	}

	private[this] def clearWorkData(id: UUID): Unit =
	{
		cancels -= id
		promises -= id
	}


	private[this] def send[R](work: Work[_, _, R])(implicit timeout: FiniteDuration): Future[Either[WorkResult, R]] =
	{
		def scheduler = context.system.scheduler
		val id = work.id
		workManager ! work
		log.debug("sent work {} to manager", id)

		if (timeout > (0 seconds))
		{
			val cancellable = scheduler.scheduleOnce(delay = timeout)
			{
				log.debug("Sending cancel request for work {}", id)
				self ! WorkCancelRequest(workId = id, reason = s"Timeout of $timeout for work has passsed")
			}
			cancels.put(id, cancellable)
			log.debug("Added cancellable to work id {}", id)
		}

		val p: WorkPromise[R] = Promise[Either[WorkResult, R]]()
		promises.put(id, p)
		p.future
	}
}

final case class ManagementNotification(topic: String, payload: Any)
class ManagementLookupBus extends EventBus with LookupClassification
{
	type Event = ManagementNotification
	type Classifier = String
	type Subscriber = ActorRef

	// is used for extracting the classifier from the incoming events
	override protected def classify(event: Event): Classifier = event.topic

	// will be invoked for each event for all subscribers which registered themselves
	// for the event’s classifier
	override protected def publish(event: Event, subscriber: Subscriber): Unit = {
		subscriber ! event.payload
	}

	// must define a full order over the subscribers, expressed as expected from
	// `java.lang.Comparable.compare`
	override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = {
		a.compareTo(b)
	}

	// determines the initial size of the index data structure
	// used internally (i.e. the expected number of different classifiers)
	override protected def mapSize(): Int = 256


}
