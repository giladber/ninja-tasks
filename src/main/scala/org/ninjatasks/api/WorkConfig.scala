package org.ninjatasks.api

import org.ninjatasks.spi.{ExecutableJob, NinjaWork, Work, JobCreator}
import org.ninjatasks.utils.ManagementConsts

/**
 * API class to create work objects from spi class implementations provided by the user.
 * Created by Gilad Ber on 6/4/2014.
 */
case class WorkConfig[JobT, DataT, ResT](creator: JobCreator[JobT, DataT], combine: (ResT, JobT) => ResT,
																					initialResult: ResT)
{

	require(creator != null, "Job creator must not be null")
	require(combine != null, "Combine function must not be null")

	var priority: Int = WorkConfig.defaultPriority
 	var data: Option[DataT] = None

	def withPriority(p: Int): this.type = {
		priority = p
		this
	}

	def withData(userData: DataT): this.type = {
		data = Option(userData)
		this
	}

	def build: Work[JobT, DataT, ResT] = NinjaWork(priority, data, creator, combine, initialResult)

}

object WorkConfig
{
	val defaultPriority = ManagementConsts.config.getInt("ninja.work.default-priority")

	def ofJob[T, D](job: ExecutableJob[T, D], initialResult: T, data: D) = {
		new WorkConfig[T, D, T](JobCreator(job), (base, jobRes) => jobRes, initialResult).
			withData(data).build
	}

	def ofJob[T, D](job: ExecutableJob[T, D], initialResult: T) = {
		new WorkConfig[T, D, T](JobCreator(job), (base, jobRes) => jobRes, initialResult).
			build
	}
}