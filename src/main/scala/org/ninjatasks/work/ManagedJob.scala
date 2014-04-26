package org.ninjatasks.work

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * General trait for objects which can be executed.
 * Each job will be executed by a single thread only, so there is no need for its execute method to be thread-safe.
 * Extra care should be taken, however, to make sure that the work data object is either thread safe or is not shared
 * with other job objects.
 *
 * It is the implementor's responsibility to
 * @tparam R type of result from the execution
 * @tparam D type of work data object
 */
trait ExecutableJob[R, D]
{
	def id: Long

	def workId: Long

	def priority: Int

	@transient var workData: D

	@volatile protected var stop = false

	def execute(): R
}

/**
 * Introduces management and execution related semantics and methods to ordinary job objects.
 * Created by Gilad Ber on 4/15/14.
 */
private[ninjatasks] trait ManagedJob[R, D] extends Ordered[ManagedJob[_, _]] with ExecutableJob[R, D]
{
	private[this] var cancel: Option[Future[_]] = None

	private[ninjatasks] var serial: Long = 0

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
			case _ => stop = true
		})
		this
	}

}