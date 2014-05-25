package org.ninjatasks.work

import scala.collection.immutable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * General interface for a factory which batch-creates job objects on demand.
 * This class is meant for use in lazily-creating massive amounts of job objects,
 * however it can also be used for any work object with varying sizes of underlying jobs.
 * Created by Gilad Ber on 4/21/14.
 */
trait JobCreator[T, D] extends Serializable
{
	self =>

	val workId: Long
	val priority: Int
	val jobNum: Long

	/**
	 * Returns a set of un-traversed jobs consisting of at most amount jobs
	 * This method is expected to have side-effects, in order to keep track of
	 * which jobs it should produce next (assuming not all jobs are identical).
	 * @param amount maximum number of jobs to return
	 * @return set of un-traversed jobs
	 */
	def create(amount: Long): immutable.Seq[ExecutableJob[T, D]]

	/**
	 * Returns the remaining number of jobs to be created
	 * @return remaining number of jobs that can possibly be created
	 */
	def remaining: Long
}

abstract class AbstractJobCreator[T, D](val workId: Long, val priority: Int, val jobNum: Long) extends JobCreator[T, D]
{

	def this(work: Work[T, D, _]) = this(work.id, work.priority, work.jobNum)
	def this(creator: JobCreator[_, _]) = this(creator.workId, creator.priority, creator.jobNum)

	protected var produced: Long = 0

	override def remaining = jobNum - produced

	/**
	 * Updated the number of already produced jobs by this creator.
	 * This method is to be used in conjunction with the overriden create() method,
	 * in order to make sure that the number of already produced jobs is updated.
	 * @param created number of additionally created jobs.
	 */
	protected def updateProduced(created: Long): Unit = produced = produced + created
}

abstract class WrappedJobCreator[T, D](val base: JobCreator[_, D]) extends AbstractJobCreator[T, D](base) {
	override def remaining = base.remaining
	override val workId: Long = base.workId
	override val priority: Int = base.priority
	override val jobNum: Long = base.jobNum
}

object JobSetIterator
{
	def apply[T, D](producer: JobCreator[T, D], serial: Long) = new JobSetIterator(producer, serial)
}

/**
 * Batch-style iterator for lazily creating job batches from a work object.
 * This trait should be implemented by the client.
 * @tparam T Type returned by the work's underlying jobs
 * @tparam D Type of the work's data object
 */
class JobSetIterator[T, D](val producer: JobCreator[T, D], val serial: Long)
														extends Iterator[immutable.Seq[ExecutableJob[T, D]]]
														with Ordered[JobSetIterator[_, _]]
{
	override def hasNext = producer.remaining > 0

	override def next() = producer.create(1)

	def next(amount: Long): immutable.Seq[ManagedJob[T, D]] = producer.create(amount).map(ManagedJob(_))

	def priority: Int = producer.priority

	override def compare(that: JobSetIterator[_, _]) =
		this.priority - that.priority match
		{
			case x if x != 0 => x
			case x if x == 0 => (that.serial - this.serial).toInt
		}
}