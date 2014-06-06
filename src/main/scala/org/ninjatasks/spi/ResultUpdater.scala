package org.ninjatasks.spi

/**
 * Result updater for work objects.
 * Created by Gilad Ber on 6/5/2014.
 */
trait ResultUpdater[BaseJobT, JobT, BaseResT, ResT] extends WorkOps[JobT, ResT]
{
	self =>

	val jobs: Jobs[BaseJobT, JobT]

	/**
	 * A reduce function to compute the final result of this computation.
	 * This function will be given as input, for a finished job J with result X,
	 * (currentResult, X), and this value will be the new currentResult of this work.
	 *
	 * For example, if our work emits String result and we wished to combine them all into a list,
	 * we would supply the following combine function:
	 * (xs: List[String], x: String) => xs + x
	 *
	 * In another example, if we wanted to take the sum of all our Long results,
	 * we would supply the following combine function:
	 * (sum: Long, x: Long) => sum + x
	 *
	 * If for example we would like to take an average of all our Long results, then the combine function
	 * will need to have an inner counter of how many results it had processed so far. Assuming that counter is n,
	 * our combine function would be:
	 * (avg: Long, x: Long) => n = n + 1; (x + (n-1)*avg)/n
	 */
	val combine: (BaseResT, BaseJobT) => ResT

	/**
	 * The initial result of the computation.
	 */
	val initialResult: BaseResT

	var result: ResT

	val baseUpdater: BaseResultUpdater[BaseJobT, BaseResT]

	def update(added: BaseJobT): Unit = {
		if (jobs.accept(added)) {
			result = combine(baseUpdater.result, added)
			baseUpdater.update(added)
		}
	}

	def map[U](f: ResT => U): ResultUpdater[BaseJobT, JobT, BaseResT, U] = {
		val mapCombine: (BaseResT, BaseJobT) => U = (r, j) => f(self.combine(r, j))
		Updater(jobs, mapCombine, f(result), this)
	}

	def mapJobs[U](f: JobT => U)(combiner: (ResT, U) => ResT): ResultUpdater[BaseJobT, U, BaseResT, ResT] = {
		Updater.mapped(jobs.map(f), combiner, result, this)
	}

	def filter(p: JobT => Boolean): ResultUpdater[BaseJobT, JobT, BaseResT, ResT] = {
		Updater(jobs.filter(p), combine, result, this)
	}

	def fold[U](f: (U, JobT) => U)(acc: U): ResultUpdater[BaseJobT, JobT, BaseResT, U] = {
		Updater.mapped(jobs, f, acc, this)
	}

	def foreach[U](f: JobT => U): ResultUpdater[BaseJobT, JobT, BaseResT, ResT] = {
		val foreachCombiner: (BaseResT, BaseJobT) => ResT = (r,j) => {
			f(jobs.transform(j))
			self.combine(r, j)
		}
		Updater(jobs, foreachCombiner, result, this)
	}
}

object ResultUpdater
{
	def apply[J, R](combine: (R, J) => R, initial: R) = new BaseResultUpdater(combine, initial)
}

object Updater {
	def apply[J, JF, R, RF](jobs: Jobs[J, JF],
													combine: (R, J) => RF,
													initialMappedResult: RF,
													other: ResultUpdater[J, _, R, _]): Updater[J, JF, R, RF] =
		new Updater(jobs, combine, other.initialResult, other.baseUpdater, initialMappedResult)

	def mapped[J, JF, R, RF](jobs: Jobs[J, JF],
													jobCombine: (RF, JF) => RF,
													initialMappedResult: RF,
													other: ResultUpdater[J, _, R, _]): JobMappedUpdater[J, JF, R, RF] =
		new JobMappedUpdater(jobs, other.initialResult, other.baseUpdater, initialMappedResult, jobCombine)

}

class Updater[J, JF, R, RF](override val jobs: Jobs[J, JF],
														 override val combine: (R, J) => RF,
														 override val initialResult: R,
														 override val baseUpdater: BaseResultUpdater[J, R],
														 override var result: RF)
														 extends ResultUpdater[J, JF, R, RF]
{

}

class JobMappedUpdater[J, JF, R, RF](override val jobs: Jobs[J, JF],
																			override val initialResult: R,
																			override val baseUpdater: BaseResultUpdater[J, R],
																			override var result: RF,
																			val jobCombine: (RF, JF) => RF)
																			extends ResultUpdater[J, JF, R, RF]
{

	override val combine: (R, J) => RF = (r, j) => {
		result = jobCombine(result, jobs.transform(j))
		result
	}
}

class BaseResultUpdater[JobT, ResT](override val combine: (ResT, JobT) => ResT,
																		override val initialResult: ResT)
																		extends ResultUpdater[JobT, JobT, ResT, ResT]
{
	self =>

	override val baseUpdater = self
	override val jobs: Jobs[JobT, JobT] = Jobs[JobT]
	override var result: ResT = initialResult

	override def update(j: JobT) = {
		result = combine(result, j)
	}
}
