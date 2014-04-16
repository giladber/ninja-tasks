package org.ninjatasks.work

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
case class SleepJob(time: Short, id: Long, priority: Int) extends Job[Unit]
{
	override def execute =
	{
		println("sleeping...")
		Thread.sleep(time)
		println("slept!")
	}
}
