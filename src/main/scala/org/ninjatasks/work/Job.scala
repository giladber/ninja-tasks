package org.ninjatasks.work

/**
 * General trait for objects which can be executed.
 * Created by Gilad Ber on 4/15/14.
 */
trait Job[R] extends Ordered[Job[_]]
{
	def id: Long

	def priority: Int

	def execute: R

	override def compare(that: Job[_]) = this.priority - that.priority
}