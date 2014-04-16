package org.ninjatasks.work

/**
 * A work object consists of possibly multiple (at least 1) job objects.
 * Created by Gilad Ber on 4/15/14.
 */
trait Work
{
	def split[T]: Stream[Set[Job[T]]]

	def id: Long
}
