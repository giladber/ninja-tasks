package org.ninjatasks.examples

import org.ninjatasks.work._
import scala.collection.immutable

/**
 * Example sleep work object consisting of sleep jobs.
 * Created by Gilad Ber on 5/18/2014.
 */
class SleepWork(val id: Long, val jobNum: Long, val priority: Int) extends Work[Int, Unit, Int]
{
	override val data: Unit = ()

	override val combine: (Int, Int) => Int = (a, b) => a + b

	override val initialResult: Int = 0 //initial value

	override def creator: JobCreator[Int, Unit] = new AbstractJobCreator(this)
	{
		override def create(amount: Long): immutable.Seq[ExecutableJob[Int, Unit]] =
		{
			val res = createSeq(amount).toSeq
			updateProduced(res.size)
			res
		}

		def createSeq(amount: Long): immutable.Seq[ExecutableJob[Int, Unit]] =
		{
			for (i <- 1 to Math.min(amount.toInt, remaining.toInt)) yield
			{
				SleepJob(1000, i.toInt, priority, id)
			}
		}
	}

}
