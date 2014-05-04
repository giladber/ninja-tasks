package org.ninjatasks.work

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.atomic.AtomicBoolean

object ExecutableJob
{
	implicit def toManaged[R, D](job: ExecutableJob[R, D]): ManagedJob[R, D] = new ManagedJob(job)
}

/**
 * General trait for objects which can be executed.
 * Each job will be executed by a single thread only, so there is no need for its execute method to be thread-safe.
 * Extra care should be taken, however, to make sure that the work data object is either thread safe or is not shared
 * with other job objects.
 * @tparam R type of result from the execution
 * @tparam D type of work data object
 */
trait ExecutableJob[R, D]
{
	val id: Long

	val workId: Long

	val priority: Int

	@transient var workData: D

	val shouldStop = new AtomicBoolean()

	def execute(): R
}

/**
 * Introduces management and execution related semantics and methods to ordinary job objects.
 * Created by Gilad Ber on 4/15/14.
 */
private[ninjatasks] class ManagedJob[R, D](val job: ExecutableJob[R, D])
	extends Ordered[ManagedJob[_, _]] with ExecutableJob[R, D] with Serializable
{
	private[this] var cancel: Option[Future[_]] = None

	override val id = job.id

	override val workId = job.workId

	override val priority = job.priority

	@transient override var workData = job.workData

	override def execute() = job.execute()

	var serial: Long = -1L

	override def compare(that: ManagedJob[_, _]): Int =
		this.priority - that.priority match
		{
			case x if x == 0 => (that.serial - this.serial).toInt
			case x if x != 0 => x.toInt
		}

	private[ninjatasks] def withFuture(cancelFuture: Future[_]): this.type =
	{
		cancel foreach (_ => throw new IllegalStateException("Already assigned a future"))
		cancel = Some(cancelFuture)
		cancelFuture.onComplete(
		{
			case _ =>
				println("changing stop to true!")
				job.shouldStop.compareAndSet(false, true)
				println("stop1 = " + job.shouldStop.get())
		})
		println("Added future " + cancelFuture + " to job " + this+", inner job is "+job)
		this
	}

}