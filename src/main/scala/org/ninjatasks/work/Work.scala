package org.ninjatasks.work

/**
 * A work object consists of multiple job objects which may be processed.
 *
 * Created by Gilad Ber on 4/15/14.
 */
trait Work[T, D] extends Iterable[Set[Job[T, D]]]
{
	override def iterator: Iterator[Set[Job[T, D]]]

	def id: Long

	def data: D

	def jobNum: Long
}