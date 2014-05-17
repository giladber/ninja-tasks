package org.ninjatasks.work

/**
 * A work object is a container for multiple job objects.
 * The logic and data included in this object are:
 * 1. A JobCreator object, which is responsible for the lazy initialization of
 * processable job objects.
 * 2. A combine function, which reduces the results of the underlying job objects
 * as they are processed, into a single result.
 * 3. The result, which is automatically calculated by the processing framework,
 * other than the initial value which must be supplied at initialization time,
 * by implementations of this trait.
 * 4. A unique ID.
 * 5. User-supplied priority.
 * 6. The number of underlying jobs which require processing.
 * 7. A data object, which is usually a relatively large object that is required
 * for the processing of the job objects. It is preferable that implementations of
 * this trait will include all non-job-specific data in the work object, since the
 * work object is sent to worker actors only once per *work*, while if you include
 * lots un-needed data in job objects, that data will occur a higher latency in
 * transferring the job objects themselves from the work manager to the workers,
 * and vice versa.
 *
 * @tparam T The type of results produced by processing the underlying jobs.
 * @tparam D The type of work-related data which is supplied to the job objects.
 */
trait Work[T, D]
{
	def creator: JobCreator[T, D]

	val combine: (T, T) => T

	var result: T

	val id: Long

	val data: D

	val priority: Int

	val jobNum: Long

	require(result != null)
	require(data != null)
	require(combine != null)
}