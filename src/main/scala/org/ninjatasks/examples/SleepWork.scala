package org.ninjatasks.examples

import scala.collection.immutable

import org.ninjatasks.spi._
import org.ninjatasks.api.WorkConfig

/**
 * Example sleep work object consisting of sleep jobs.
 * Created by Gilad Ber on 5/18/2014.
 */
class SleepWork(val jobNum: Long, val priority: Int)
{
	self =>

	val data: Unit = ()

	val combine: (Int, Int) => Int = (a, b) => a + b

	val initialResult: Int = 0 //initial value

	val creator: JobCreator[Int, Unit] = new AbstractJobCreator[Int, Unit]
	{
		override val jobNum = self.jobNum
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
				SleepJob(1000, i.toInt, priority)
			}
		}
	}

	def make(): FuncWork[Int, Unit, Int] = {
		new WorkConfig(creator, data, combine, initialResult).withPriority(priority).build
	}

}
