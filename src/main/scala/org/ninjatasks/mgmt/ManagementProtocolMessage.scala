package org.ninjatasks.mgmt

import org.ninjatasks.work.{ExecutableJob, ManagedJob}
import akka.actor.ActorSelection
import scala.concurrent.Future

/**
 * This class contains all possible messages which are used for the task management protocols.
 * Created by Gilad Ber on 4/15/14.
 */

sealed trait ManagementProtocolMessage extends Serializable

sealed trait JobResultMessage extends ManagementProtocolMessage

private[ninjatasks] case class AggregateJobMessage(jobs: Set[ExecutableJob[_, _]]) extends ManagementProtocolMessage

private[ninjatasks] case class JobSetRequest(max: Long) extends ManagementProtocolMessage

private[ninjatasks] case object JobCapacityRequest extends ManagementProtocolMessage

private[ninjatasks] case class JobCapacity(num: Long) extends ManagementProtocolMessage

private[ninjatasks] case class JobMessage(job: ManagedJob[_, _]) extends ManagementProtocolMessage

private[ninjatasks] case class JobExecution(job: ManagedJob[_, _], future: Future[_]) extends ManagementProtocolMessage

private[ninjatasks] case object JobRequest extends ManagementProtocolMessage

private[ninjatasks] case class JobSuccess[T](res: T, jobId: Long) extends JobResultMessage

private[ninjatasks] case class JobFailure(reason: Exception, jobId: Long) extends JobResultMessage

private[ninjatasks] case class WorkDelegationMessage(jobDelegator: ActorSelection) extends ManagementProtocolMessage

private[ninjatasks] case class WorkDataMessage[T](workId: Long, data: T) extends ManagementProtocolMessage

private[ninjatasks] case class WorkCancelMessage(workId: Long) extends ManagementProtocolMessage

private[ninjatasks] case object ComponentStarted extends ManagementProtocolMessage

private[ninjatasks] case object ComponentStartedAck extends ManagementProtocolMessage