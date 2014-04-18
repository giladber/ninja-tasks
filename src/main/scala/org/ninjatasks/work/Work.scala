package org.ninjatasks.work

/**
 * A work object consists of possibly multiple (at least 1) job objects.
 * Created by Gilad Ber on 4/15/14.
 */
trait Work[T, D] extends Iterable[Set[Job[T, D]]]
{
	override def iterator: Iterator[Set[Job[T, D]]]

	def id: Long

	def data: D
}
