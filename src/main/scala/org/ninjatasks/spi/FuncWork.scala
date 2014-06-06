package org.ninjatasks.spi

import java.util.UUID

/**
 * replacement for old work
 * Created by Gilad Ber on 6/4/2014.
 */
trait FuncWork[JobT, DataT, ResT]
{
	self =>

	private[ninjatasks] type baseJobType
	private[ninjatasks] type baseResType
	private[ninjatasks] val id: UUID = UUID.randomUUID()
	private[ninjatasks] val updater: ResultUpdater[baseJobType, JobT, baseResType, ResT]
	private[ninjatasks] def update(res: baseJobType) = updater.update(res)
	private[ninjatasks] def result: ResT = updater.result

	val priority: Int
	val data: DataT
	val creator: JobCreator[baseJobType, DataT]

	def mapJobs[U](f: JobT => U, combiner: (ResT, U) => ResT): FuncWork[U, DataT, ResT] = {
		val mappedUpdater = updater.mapJobs(f)(combiner)
		NinjaWork(this, mappedUpdater, creator)
	}

	def map[U](f: ResT => U): FuncWork[JobT, DataT, U] = {
		val mappedUpdater = updater.map(f)
		NinjaWork(this, mappedUpdater, creator)
	}

	def filter(p: JobT => Boolean): FuncWork[JobT, DataT, ResT] = {
		val filteredUpdater = updater.filter(p)
		NinjaWork(this, filteredUpdater, creator)
	}

	def foreach[U](f: JobT => U): FuncWork[JobT, DataT, ResT] = {
		val foreachUpdater = updater.foreach(f)
		NinjaWork(this, foreachUpdater, creator)
	}
}

object NinjaWork
{
	def apply[J, JF, D, R, RF](work: FuncWork[_, D, _], updater: ResultUpdater[J, JF, R, RF], creator: JobCreator[J, D]) = {
		new NinjaWork(work.priority, work.data, updater, creator)
	}

	def apply[J, D, R](priority: Int, data: D, creator: JobCreator[J, D], combine: (R, J) => R, initialResult: R) = {
		new NinjaWork(priority, data, ResultUpdater(combine, initialResult), creator)
	}
}

class NinjaWork[J, JF, D, R, RF](override val priority: Int,
																 override val data: D,
																 override val updater: ResultUpdater[J, JF, R, RF],
																 override val creator: JobCreator[J, D])
																extends FuncWork[JF, D, RF]
{
	private[ninjatasks] type baseJobType = J
	private[ninjatasks] type baseResType = R


}
