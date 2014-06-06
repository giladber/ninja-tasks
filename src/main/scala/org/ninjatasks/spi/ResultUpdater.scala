package org.ninjatasks.spi

/**
 * Result updater for work objects.
 * Created by Gilad Ber on 6/5/2014.
 */
trait ResultUpdater[BaseJobT, JobT, BaseResT, ResT]
{
	self =>

	val jobs: Jobs[BaseJobT, JobT]
	val combine: (BaseResT, BaseJobT) => ResT
	val initialResult: BaseResT
	var result: ResT
	val baseUpdater: BaseResultUpdater[BaseJobT, BaseResT]

	def update(added: BaseJobT): Unit = {
		if (jobs.accept(added)) {
			result = combine(baseUpdater.result, added)
			baseUpdater.update(added)
		}
	}

	private[ninjatasks] def convert(result: BaseResT): ResT

	def map[U](f: ResT => U): ResultUpdater[BaseJobT, JobT, BaseResT, U] = {
		val mapCombine: (BaseResT, BaseJobT) => U = (r, j) => f(self.combine(r, j))
		val mapConvert: BaseResT => U = r => f(convert(r))
		Updater(jobs, mapCombine, mapConvert, this)
	}

	def mapJobs[U](f: JobT => U)(combiner: (ResT, U) => ResT): ResultUpdater[BaseJobT, U, BaseResT, ResT] = {
		Updater.mapped(jobs.map(f), combiner, convert, this)
	}

	def filter(p: JobT => Boolean): ResultUpdater[BaseJobT, JobT, BaseResT, ResT] = {
		Updater(jobs.filter(p), combine, convert, this)
	}

//	def fold[U](f: (ResT, U) => U)(acc: U): ResultUpdater[BaseJobT, JobT, BaseResT, U]
	def foreach[U](f: JobT => U): ResultUpdater[BaseJobT, JobT, BaseResT, ResT] = {
		val foreachCombiner: (BaseResT, BaseJobT) => ResT = (r,j) => {
			val res = self.combine(r, j)
			f(jobs.transform(j))
			res
		}
		Updater(jobs, foreachCombiner, convert, this)
	}
}

object ResultUpdater
{
	def apply[J, R](combine: (R, J) => R, initial: R) = new BaseResultUpdater(combine, initial)
}

object Updater {
	def apply[J, JF, R, RF](jobs: Jobs[J, JF],
													combine: (R, J) => RF,
													convert: R => RF,
													other: ResultUpdater[J, _, R, _]): Updater[J, JF, R, RF] =
		new Updater(jobs, combine, other.initialResult, other.baseUpdater, convert)

	def mapped[J, JF, R, RF](jobs: Jobs[J, JF],
													jobCombine: (RF, JF) => RF,
													convert: R => RF,
													other: ResultUpdater[J, _, R, _]): JobMappedUpdater[J, JF, R, RF] =
		new JobMappedUpdater(jobs, other.initialResult, other.baseUpdater, convert, jobCombine)

}

class Updater[J, JF, R, RF](override val jobs: Jobs[J, JF],
														 override val combine: (R, J) => RF,
														 override val initialResult: R,
														 override val baseUpdater: BaseResultUpdater[J, R],
														 convertFunc: R => RF)
														 extends ResultUpdater[J, JF, R, RF]
{
	override def convert(r: R): RF = convertFunc(r)
	override var result: RF = convert(initialResult)
}

class JobMappedUpdater[J, JF, R, RF](override val jobs: Jobs[J, JF],
																			override val initialResult: R,
																			override val baseUpdater: BaseResultUpdater[J, R],
																			convertFunc: R => RF,
																			val jobCombine: (RF, JF) => RF)
																			extends ResultUpdater[J, JF, R, RF]
{
	override def convert(r: R): RF = convertFunc(r)
	override var result: RF = convert(initialResult)

	override val combine: (R, J) => RF = (r, j) => {
		val res = jobCombine(result, jobs.transform(j))
		result = res
		res
	}
}

class BaseResultUpdater[JobT, ResT](override val combine: (ResT, JobT) => ResT,
																		override val initialResult: ResT)
																		extends ResultUpdater[JobT, JobT, ResT, ResT]
{
	self =>

	override val baseUpdater = self
	override def convert(r: ResT) = r
	override val jobs: Jobs[JobT, JobT] = Jobs[JobT]
	override var result: ResT = initialResult

	override def update(j: JobT) = {
		result = combine(result, j)
	}
}
