package org.ninjatasks.spi

import java.util.UUID

/**
 * A work object is a container for multiple job objects.
 * The logic and data included in this object are:
 * 1. A JobCreator object, which is responsible for the lazy initialization of
 * processable job objects.
 * 2. A combine function, which reduces the results of the underlying job objects
 * as they are processed, into a single result.
 * 3. The initial result of the computation. The final result is calculated as a reduction of the combine function
 * over the stream of incoming results, with the initial result field as its initial value.
 * 4. User-supplied priority.
 * 5. A data object, which is usually a relatively large object that is required
 * for the processing of the job objects. It is preferable that implementations of
 * this trait will include all non-job-specific data in the work object, since the
 * work object is sent to worker actors only once per *work*, while if you include
 * lots un-needed data in job objects, that data will occur a higher latency in
 * transferring the job objects themselves from the work manager to the workers,
 * and vice versa.
 *
 * The final result of this work is computed by applying the combine function
 * to the stream of incoming job results, with initialResult being the initial value
 * of the reduction
 *
 * Since the combine and creator functions will be called by actors inside the ninja-tasks framework,
 * they must absolutely be non-blocking, otherwise possibly resulting in severe scalability issues
 * for this system.
 * This requirement is especially critical for the combine function which may be called many times,
 * depending on the number of underlying job objects a work will have. If a blocking call (say network, DB-related)
 * must be made, then make sure to either make it before or after submitting the work for processing. The same applies
 * for the creator function, however that will only be called once per work submission, so it has less potential
 * to become a bottleneck.
 *
 * @tparam JobT The type of results produced by processing the underlying jobs.
 * @tparam DataT The type of work-related data which is supplied to the job objects.
 * @tparam ResT The type of the final result obtained by the computation of this work.
 */
trait Work[JobT, DataT, ResT] extends WorkOps[JobT, ResT]
{
	self =>

	private[ninjatasks] type baseJobType
	private[ninjatasks] type baseResType
	private[ninjatasks] val id: UUID = UUID.randomUUID()
	private[ninjatasks] val updater: ResultUpdater[baseJobType, JobT, baseResType, ResT]
	private[ninjatasks] def update(res: baseJobType) = updater.update(res)
	private[ninjatasks] def result: ResT = updater.result

	/**
	 * The priority assigned to this work.
	 * A higher value means higher priority -- that is, works with the higher priority will be processed prior to works
	 * with a lower priority, regardless of insertion time. Job objects of works with the same priority value will be sent
	 * to processing in order of insertion to the internal job queue.
	 */
	val priority: Int

	/**
	 * Underlying data which will be used by jobs for their computation.
	 * This object should mostly consist of data that is not related to a certain job's computation,
	 * since it is sent to all processing actors, regardless of which job they are executing.
	 * On the other hand, it any data that is relevant to all jobs should be contained in this object
	 * and not in other job objects, in order to minimize serialization costs, to not incur
	 * that cost for every job that is sent.
	 *
	 * It should be noted that this object will most likely be shared between multiple job objects,
	 * so it is essential that either it is thread-safe, or that all jobs access it in a synchronized fashion.
	 */
	val data: Option[DataT]

	/**
	 * Job objects factory, for lazy creation of jobs when required and when
	 * processing has become possible.
	 */
	val creator: JobCreator[baseJobType, DataT]

	/**
	 * Returns a new work object with the given combiner, which is invoked on the results of the
	 * work's job results after the result is mapped by f.
	 * @param f mapping function
	 * @param combiner new combiner
	 * @tparam U type of the new intermediate results
	 * @return A new work object which maps the results of job objects by the mapping function.
	 */
	def mapJobs[U](f: JobT => U)(combiner: (ResT, U) => ResT): Work[U, DataT, ResT] = {
		val mappedUpdater = updater.mapJobs(f)(combiner)
		NinjaWork(this, mappedUpdater, creator)
	}

	/**
	 * Map the result of this work object to another
	 * @param f mapping function
	 * @tparam U target result type
	 * @return A new work instance with its result mapped by the given function
	 */
	def map[U](f: ResT => U): Work[JobT, DataT, U] = {
		val mappedUpdater = updater.map(f)
		NinjaWork(this, mappedUpdater, creator)
	}

	/**
	 * Add a filter to the work object's job results, ignoring results that do not pass the filter.
	 * @param p predicate to filter by
	 * @return A new RichWork object which ignores job results according to the input filter.
	 */
	def filter(p: JobT => Boolean): Work[JobT, DataT, ResT] = {
		val filteredUpdater = updater.filter(p)
		NinjaWork(this, filteredUpdater, creator)
	}

	/**
	 * Applies a function to each incoming job result, ignoring the function's result
	 * (i.e. it is only needed for side effects).
	 * @param f function to be invoked on each job result
	 * @tparam U function result type (is ignored)
	 * @return a new work object which will apply the input function to each job result on reduction
	 */
	def foreach[U](f: JobT => U): Work[JobT, DataT, ResT] = {
		val foreachUpdater = updater.foreach(f)
		NinjaWork(this, foreachUpdater, creator)
	}

	def fold[U](f: (U, JobT) => U)(acc: U): Work[JobT, DataT, U] =
	{
		val foldUpdater = updater.fold(f)(acc)
		NinjaWork(this, foldUpdater, creator)
	}
}

private[ninjatasks] object NinjaWork
{
	def apply[J, JF, D, R, RF](work: Work[_, D, _], updater: ResultUpdater[J, JF, R, RF], creator: JobCreator[J, D]) = {
		new NinjaWork(work.priority, work.data, updater, creator)
	}

	def apply[J, D, R](priority: Int, data: Option[D], creator: JobCreator[J, D], combine: (R, J) => R, initialResult: R) = {
		new NinjaWork(priority, data, ResultUpdater(combine, initialResult), creator)
	}
}

private[ninjatasks] class NinjaWork[J, JF, D, R, RF](override val priority: Int,
																 override val data: Option[D],
																 override val updater: ResultUpdater[J, JF, R, RF],
																 override val creator: JobCreator[J, D])
																extends Work[JF, D, RF]
{
	private[ninjatasks] type baseJobType = J
	private[ninjatasks] type baseResType = R


}
