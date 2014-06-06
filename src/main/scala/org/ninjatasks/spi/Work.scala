package org.ninjatasks.spi

//import java.util.UUID

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
 * 5. The number of underlying jobs which require processing.
 * 6. A data object, which is usually a relatively large object that is required
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
 * @tparam T The type of results produced by processing the underlying jobs.
 * @tparam D The type of work-related data which is supplied to the job objects.
 * @tparam R The type of the final result obtained by the computation of this work.
 */
trait Work[T, D, R] extends Serializable
{
//	/**
//	 * Job objects factory, for lazy creation of jobs when required and when
//	 * processing has become possible.
//	 * @return A user-defined implementation of JobCreator for this work object.
//	 */
//	def creator: JobCreator[T, D]
//
//	/**
//	 * A reduce function to compute the final result of this computation.
//	 * This function will be given as input, for a finished job J with result X,
//	 * (currentResult, X), and this value will be the new currentResult of this work.
//	 *
//	 * For example, if our work emits String result and we wished to combine them all into a list,
//	 * we would supply the following combine function:
//	 * (xs: List[String], x: String) => xs + x
//	 *
//	 * In another example, if we wanted to take the sum of all our Long results,
//	 * we would supply the following combine function:
//	 * (sum: Long, x: Long) => sum + x
//	 *
//	 * If for example we would like to take an average of all our Long results, then the combine function
//	 * will need to have an inner counter of how many results it had processed so far. Assuming that counter is n,
//	 * our combine function would be:
//	 * (avg: Long, x: Long) => n = n + 1; (x + (n-1)*avg)/n
//	 */
//	val combine: (R, T) => R
//
//	/**
//	 * The initial result of the computation.
//	 */
//	val initialResult: R
//
//	/**
//	 * Underlying data which will be used by jobs for their computation.
//	 * This object should mostly consist of data that is not related to a certain job's computation,
//	 * since it is sent to all processing actors, regardless of which job they are executing.
//	 * On the other hand, it any data that is relevant to all jobs should be contained in this object
//	 * and not in other job objects, in order to minimize serialization costs, to not incur
//	 * that cost for every job that is sent.
//	 *
//	 * It should be noted that this object will most likely be shared between multiple job objects,
//	 * so it is essential that either it is thread-safe, or that all jobs access it in a synchronized fashion.
//	 */
//	val data: D
//
//	/**
//	 * The priority assigned to this work.
//	 * A higher value means higher priority -- that is, works with the higher priority will be processed prior to works
//	 * with a lower priority, regardless of insertion time. Job objects of works with the same priority value will be sent
//	 * to processing in order of insertion to the internal job queue.
//	 */
//	val priority: Int
}

/**
 * An extension to the basic work trait, with additional functional operations applicable to its underlying elements.
 * All methods in this class do not change any underlying state of the work object, instead creating a new one.
// * @tparam Base The type of results produced by processing the underlying jobs.
// * @tparam D The type of work-related data which is supplied to the job objects.
// * @tparam R The type of the final result obtained by the computation of this work.
 */
//class RichWork[Base, Cur, D, R](val work: Work[Base, D, R]) extends Work[Base, D, R]
//{
//
//	override val combine = work.combine
//	override def creator = work.creator
//	override val priority = work.priority
//	override val initialResult = work.initialResult
//	override val data = work.data
//	val id: UUID = UUID.randomUUID()
//
//	val jobs: Jobs[Base, Cur] = Jobs[Base, Cur]
//	/**
//	 * Returns a new work object with the given combiner, which is invoked on the results of the
//	 * work's job results after the result is mapped by f.
//	 * @param f mapping function
//	 * @param combiner new combiner
//	 * @tparam U type of the new intermediate results
//	 * @return A new work object which maps the results of job objects by the mapping function.
//	 */
//	def mapJobs[U](f: Base => U, combiner: (R, U) => R): RichWork[Base, U, D, R] = {
//
//	}
//
//
//	/**
//	 * Map the result of this work object to another
//	 * @param f mapping function
//	 * @tparam U target result type
//	 * @return A new work instance with its result mapped by the given function
//	 */
//	def map[U](f: R => U): RichWork[Base, Cur, D, U] = {
//		new MappedWork[Base, D, R, U](this, f)
//	}
//
//
//
////	/**
////	 * Merges this work object and another work object, such that the result of the new work object is the
////	 * pair consisting of both work objects' results, and such that its underlying jobs are this work's jobs
////	 * along with the input work's jobs.
////	 * @param other Work object to merge this work with.
////	 * @tparam Cur Type of other work's job results.
////	 * @tparam D2 Type of other work's data.
////	 * @tparam R2 Type of other work's result
////	 * @return A new work object which includes all jobs of the two work objects and which outputs a result of their
////	 *         two results combined.
////	 */
////	def zip[Cur, D2, R2](other: Work[Cur, D2, R2]): RichWork[Either[Base, Cur], (D, D2), (R, R2)] = {
////		null
////	}
//
//	/**
//	 * Add a filter to the work object's job results, ignoring results that do not pass the filter.
//	 * @param p predicate to filter by
//	 * @return A new RichWork object which ignores job results according to the input filter.
//	 */
//	def filter(p: Base => Boolean): RichWork[Base, Cur, D, R] = {
//
//	}
//
//}
//
//object RichWork {
//	def apply[T, T2, D, R](work: Work[T, D, R]): RichWork[T, T2, D, R] = new RichWork(work)
//}
//
//object ManagedWork {
//	def apply[T, D, R](work: Work[T, D, R]): ManagedWork[T, D, R] = new ManagedWork(work)
//}
//
///**
// * A wrapper class to the Work trait, adding the result field which is not required to be implemented by the user.
// * @tparam T The type of results produced by processing the underlying jobs.
// * @tparam D The type of work-related data which is supplied to the job objects.
// * @tparam R The type of the final result obtained by the computation of this work.
// */
//private[ninjatasks] class ManagedWork[T, T2, D, R](override val combine: (R, T) => R,
//																							 override val id: UUID,
//																							 override val priority: Int,
//																							 override val initialResult: R,
//																							 override val data: D,
//																							 jobCreator: => JobCreator[T, D])
//																									extends RichWork[T, T2, D, R]
//{
//
//	require(creator != null)
//	require(combine != null)
//	require(initialResult != null)
//	require(data != null)
//	require(jobNum > 0)
//
////	val jobs: DelegatingJobs[T, D, T] = DelegatingJobs(jobCreator)
//	override def creator = jobCreator
//	var result: R = initialResult
//
//	def this(work: Work[T, D, R]) = this(work.combine, work.id, work.priority, work.initialResult, work.data, work.creator)
//
//	/**
//	 * Updates the intermediate result of the work's computation and returns it
//	 * @param additionalResult new job value to compute result with
//	 * @return the intermediate result
//	 */
//	def update(additionalResult: T): R =
//	{
//		result = combine(result, additionalResult)
//		println(s"result from base is $result")
//		result
//	}
//}
//
//class MappedWork[T, D, R, U](work: Work[T, D, R], f: R => U) extends ManagedWork[T, D, U](id = work.id,
//																																							 combine = (u, t) => u,
//																																							 priority = work.priority,
//																																							 initialResult = f(work.initialResult),
//																																							 data = work.data,
//																																							 jobCreator = work.creator)
//{
//
//	val managed = work match {
//		case mw: ManagedWork[T, D, R] => mw
//		case other => ManagedWork(other)
//	}
//	var intermediateResult: R = managed.initialResult
//
//	override def update(additionalResult: T) =
//	{
//		intermediateResult = managed.update(additionalResult)
//		result = f(intermediateResult)
//		println(s"result from mapped is $result")
//		result
//	}
//}
