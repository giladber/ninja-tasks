package org.ninjatasks.examples

import org.ninjatasks.work.ExecutableJob

object SleepJob
{
	def apply(time: Short, id: Long, priority: Int, workId: Long) = new SleepJob(time, id, priority, workId)
}

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
class SleepJob(val time: Short, val id: Long, val priority: Int, val workId: Long) extends ExecutableJob[Unit, Unit] with Serializable
{
	@transient override var workData = ()

	override def execute(): Unit =
	{
		var i = 0
		while (i < 3)
		{
			if (shouldStop.get)
			{
				println("Stopped!")
				return
			}
			println("sleeping...")
			Thread.sleep(time)
			println("slept!")
			i = i + 1
		}
	}
}