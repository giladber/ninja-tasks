package org.ninjatasks.examples

import org.ninjatasks.work.ExecutableJob

object SleepJob
{
	def apply(time: Int, id: Long, priority: Int, workId: Long) = new SleepJob(time, id, priority, workId)
}

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
class SleepJob(val time: Int, val id: Long, val priority: Int, val workId: Long)
	extends ExecutableJob[Int, Unit]
	with Serializable
{
	@transient override var workData = ()

	override def execute(): Int =
	{
		var total = 0
		for (i <- 1 to 3 if !shouldStop.get())
		{
			Thread.sleep(time)
			total = total + time
		}
		println(s"time slept is $total, was stopped: "+shouldStop.get())
		total
	}
}
