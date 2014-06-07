package org.ninjatasks.taskmanagement

import scala.concurrent.Future
import org.ninjatasks.spi.{FuncWork, ManagedJob}
import java.util.UUID

/**
 * General interface for all ninjatasks job execution protocol messages.
 * Created by Gilad Ber on 4/15/14.
 */
sealed trait ManagementProtocolMessage extends Serializable

/**
 * Father class of all job result type classes.
 */
sealed abstract class JobResult(val workId: UUID, val jobId: Long) extends ManagementProtocolMessage

/**
 * Father class of all work result type classes.
 */
sealed abstract class WorkResult(val workId: UUID) extends ManagementProtocolMessage


import scala.collection.immutable

/**
 * A message containing multiple job objects to be processed.
 * The type parameters for the ManagedJob in the set are both wildcards since the set
 * may contain jobs of different types (i.e. both a ManagedJob[X, Y] and ManagedJob[A, B]).
 * @param jobs set of job objects to be processed
 */
private[ninjatasks] case class AggregateJobMessage(jobs: immutable.Seq[ManagedJob[_, _]]) extends ManagementProtocolMessage

/**
 * A message containing a single job to be processed. This message is sent to the job delegator and
 * from there forwarded to the worker managers.
 * @param job Job which requires processing.
 * @tparam T Return type parameter of the underlying job object.
 * @tparam D Work data type parameter of the underlying job object.
 */
private[ninjatasks] case class JobMessage[T, D](job: ManagedJob[T, D]) extends ManagementProtocolMessage

/**
 * A request for at most max jobs, sent from the job extractor to the work manager.
 * @param max Maximum amount of jobs to be returned as an answer to this request.
 */
private[ninjatasks] case class JobSetRequest(max: Long) extends ManagementProtocolMessage

/**
 * Request for information on the number of available job space, that is, how many more job
 * objects can currently be sent for processing.
 */
private[ninjatasks] case object JobCapacityRequest extends ManagementProtocolMessage

/**
 * Answer to the JobCapacityRequest, detailing the current capacity for incoming job requests.
 * @param num The number of available slots for job requests in the job delegator.
 */
private[ninjatasks] case class JobCapacity(num: Long) extends ManagementProtocolMessage

/**
 * Job execution order sent from the worker manager to its underlying workers, indicating that the input job
 * is to be processed by the target actor worker.
 * The future is used internally to cancel the job's execution prematurely, if required.
 * @param job Job to be processed.
 * @param future Future used to cancel job execution in case of need.
 * @tparam T Underlying return type of the job.
 * @tparam D Underlying work data type of the job.
 * @tparam R Type of the future object.
 */
private[ninjatasks] case class JobExecution[T, D, R](job: ManagedJob[T, D],
																										 future: Future[R]) extends ManagementProtocolMessage

/**
 * A request sent from the worker manager to the job delegator, indicating the worker manager's underlying
 * workers are free to process more jobs.
 */
private[ninjatasks] case object JobRequest extends ManagementProtocolMessage

/**
 * Indicates the success of an executed job object, along with its result.
 * @param res Result of the job's execution.
 * @param jobId ID of the executed job.
 * @param workId ID of the job's owning work object.
 * @tparam T Type of the result.
 */
private[ninjatasks] case class JobSuccess[T](res: T, override val jobId: Long,
																						 override val workId: UUID) extends JobResult(workId, jobId)

/**
 * Indicates the failure of an executed job object, along with its reason.
 * @param reason Reason (exception) for failure.
 * @param jobId ID of the executed job.
 * @param workId ID of the job's owning work object.
 */
private[ninjatasks] case class JobFailure(reason: Throwable, override val jobId: Long,
																					override val workId: UUID) extends JobResult(workId, jobId)

/**
 * A message consisting of the work object's data payload which is required for the processing
 * of its underlying jobs. The data object will have data that is not relevant to a specific job,
 * and will be relatively heavy, which is why it is sent only once per work execution, instead
 * of being sent along with every job object.
 * This message is sent from the work manager to the worker managers.
 * @param workId ID of the work.
 * @param data Data payload.
 * @tparam T Type of the data payload.
 */
private[ninjatasks] case class WorkDataMessage[T](workId: UUID, data: T) extends ManagementProtocolMessage

/**
 * A message sent to the worker managers signaling that some work object is no longer
 * being processed, and thus that its data payload is to be removed.
 * This notification is published from the work manager.
 * @param workId ID of the cancelled work object.
 */
private[ninjatasks] case class WorkDataRemoval(workId: UUID) extends ManagementProtocolMessage

/**
 * A request to cancel the processing a work object that is possibly currently being executed.
 * This request is sent from a client to the work manager, from there delegated to the job delegator
 * and to the worker managers, which then in turn cancel the on-going related job executions.
 * @param workId ID of the work that is to be cancelled.
 */
private[ninjatasks] case class WorkCancelRequest(workId: UUID) extends ManagementProtocolMessage

/**
 * A notification that some component has started, and is requesting acknowledgement from
 * any other component in the ninja-tasks system in order to assume registration.
 */
private[ninjatasks] case object ComponentStarted extends ManagementProtocolMessage

/**
 * An acknowledgement sent upon receiving the aforementioned ComponentStarted, from any
 * source other than self.
 */
private[ninjatasks] case object ComponentStartedAck extends ManagementProtocolMessage

/**
 * An acknowledgement message sent to clients after having started the processing of
 * some work request received from the client.
 * @param workId Id of the work on which processing has begun.
 */
private[ninjatasks] case class WorkStarted(workId: UUID) extends ManagementProtocolMessage

/**
 * A notification that a submitted work object could not be executed due to capacity constraints,
 * and should be resent for processing later on.
 */
case class WorkRejected(override val workId: UUID) extends WorkResult(workId)

/**
 * A notification that the execution of a work request has finished, along with its result.
 * @param result Result of the execution.
 * @tparam R Type of the result object.
 */
case class WorkFinished[R](override val workId: UUID, result: R) extends WorkResult(workId)

/**
 * A notification that the execution of a work request has failed, along with its reason.
 * @param reason Reason for failure.
 */
case class WorkFailed(override val workId: UUID, reason: Throwable) extends WorkResult(workId)

/**
 * A notification that the execution of a work request has been cancelled, as per request by a client.
 */
case class WorkCancelled(override val workId: UUID) extends WorkResult(workId)

private[ninjatasks] case class CombineRequest[A, B, C, D](work: FuncWork[A, B, C], result: JobSuccess[D])
	extends ManagementProtocolMessage

private[ninjatasks] case class CombineAck(workId: UUID) extends ManagementProtocolMessage