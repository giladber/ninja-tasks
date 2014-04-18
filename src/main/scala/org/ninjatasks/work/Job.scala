package org.ninjatasks.work

/**
 * General trait for objects which can be executed.
 * Created by Gilad Ber on 4/15/14.
 */
trait Job[R, D] extends Ordered[Job[_, _]]
{
	@transient val workData: D

	def id: Long

	def workId: Long

	def priority: Int

	override def compare(that: Job[_, _]) = this.priority - that.priority

	def execute(): R

}