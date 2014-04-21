package org.ninjatasks.work

/**
 * General interface for a factory which batch-creates job objects on demand.
 * This class is meant for use in lazily-creating massive amounts of job objects,
 * however it can also be used for any work object with varying sizes of underlying jobs.
 * Created by Gilad Ber on 4/21/14.
 */
trait JobCreator[T, D]
{
	/**
	 * Returns a set of un-traversed jobs consisting of at most amount jobs
	 * @param amount maximum number of jobs to return
	 * @return set of un-traversed jobs
	 */
	def create(amount: Long): Set[Job[T, D]]

	def remaining: Long
}

abstract class AbstractJobCreator[T, D](work: Work)
{
	protected val produced = work.jobNum

	override def remaining = work.jobNum - produced

	protected def updateProduced(created: Long) = produced += created
}

trait JobSetIterator[T, D] extends Iterator[Set[Job[T, D]]]
{
	private val producer: JobCreator[T, D]

	override def hasNext = producer.remaining > 0

	override def next() = producer.create(1)

	def next(amount: Long) = producer.create(amount)
}