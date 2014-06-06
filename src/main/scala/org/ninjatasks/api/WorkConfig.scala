package org.ninjatasks.api

import org.ninjatasks.spi.{NinjaWork, FuncWork, JobCreator}
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

	def build: FuncWork[JobT, DataT, ResT] = NinjaWork(priority, data, creator, combine, initialResult)
}

object WorkConfig
{
	val defaultPriority = ManagementConsts.config.getInt("ninja.work.default-priority")
}