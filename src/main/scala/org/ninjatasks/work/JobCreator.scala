package org.ninjatasks.work

/**
 * General interface for a factory which batch-creates job objects on demand.
 * This class is meant for use in lazily-creating massive amounts of job objects,
 * however it can also be used for any work object with varying sizes of underlying jobs.
 * Created by Gilad Ber on 4/21/14.
 */
trait JobCreator[T, D]
{
	val work: Work[T, D]

	/**
	 * Returns a set of un-traversed jobs consisting of at most amount jobs
	 * @param amount maximum number of jobs to return
	 * @return set of un-traversed jobs
	 */
	def create(amount: Long): Set[ExecutableJob[T, D]]

	/**
	 * Returns the remaining number of jobs to be created
	 * @return remaining number of jobs that can possibly be created
	 */
	def remaining: Long
}

abstract class AbstractJobCreator[T, D](val work: Work[T, D]) extends JobCreator[T, D]
{
	protected var produced = work.jobNum

	override def remaining = work.jobNum - produced

	/**
	 * Updated the number of already produced jobs by this creator.
	 * This method is to be used in conjunction with the overriden create() method,
	 * in order to make sure that the number of already produced jobs is updated.
	 * @param created number of additionally created jobs.
	 */
	protected def updateProduced(created: Long) = produced += created
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
class JobSetIterator[T, D](val producer: JobCreator[T, D],
													 val serial: Long) extends Iterator[Set[ExecutableJob[T, D]]] with Ordered[JobSetIterator[_, _]]
{
	override def hasNext = producer.remaining > 0

	override def next() = producer.create(1)

	def next(amount: Long): Set[ExecutableJob[T, D]] = producer.create(amount)

	def priority: Int = producer.work.priority

	override def compare(that: JobSetIterator[_, _]) =
		this.priority - that.priority match
		{
			case x if x != 0 => x
			case x if x == 0 => (that.serial - this.serial).toInt
		}
}