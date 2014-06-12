package org.ninjatasks.api

import org.ninjatasks.spi.{ExecutableJob, NinjaWork, Work, JobCreator}
import org.ninjatasks.utils.ManagementConsts

/**
 * API class to create work objects from spi class implementations provided by the user.
 * Created by Gilad Ber on 6/4/2014.
 */
case class WorkConfig[JobT, DataT, ResT](creator: JobCreator[JobT, DataT], data: DataT, combine: (ResT, JobT) => ResT,
																					initialResult: ResT)
{
	var priority: Int = WorkConfig.defaultPriority

	def withPriority(p: Int): WorkConfig[JobT, DataT, ResT] = {
		priority = p
		this
	}

	def build: Work[JobT, DataT, ResT] = NinjaWork(priority, data, creator, combine, initialResult)

}

object WorkConfig
{
	val defaultPriority = ManagementConsts.config.getInt("ninja.work.default-priority")

	def ofJob[T, D](job: ExecutableJob[T, D], initialResult: T, data: D) = {
		new WorkConfig[T, D, T](JobCreator(job), data, (base, jobRes) => jobRes, initialResult).
			build
	}
}