package org.ninjatasks.mgmt

import org.ninjatasks.work.Job
import akka.actor.ActorSelection

/**
 * This class contains all possible messages which are used for the task management protocols.
 * Created by Gilad Ber on 4/15/14.
 */

sealed trait ManagementProtocolMessage extends Serializable

private[ninjatasks] case class AggregateJobMessage(jobs: Set[Job[_, _]]) extends ManagementProtocolMessage

private[ninjatasks] case class JobMessage(job: Job[_, _]) extends ManagementProtocolMessage

private[ninjatasks] case object JobRequest extends ManagementProtocolMessage

private[ninjatasks] case class JobSuccess[T](res: T, jobId: Long) extends ManagementProtocolMessage

private[ninjatasks] case class JobFailure(reason: Exception, jobId: Long) extends ManagementProtocolMessage

private[ninjatasks] case class WorkDelegationMessage(jobDelegator: ActorSelection) extends ManagementProtocolMessage

private[ninjatasks] case class WorkDataMessage[T](workId: Long, data: T) extends ManagementProtocolMessage

private[ninjatasks] case object ComponentStarted extends ManagementProtocolMessage

private[ninjatasks] case object ComponentStartedAck extends ManagementProtocolMessage