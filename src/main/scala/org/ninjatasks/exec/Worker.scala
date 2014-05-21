package org.ninjatasks.exec

import akka.actor.{Actor, ActorLogging}
import org.ninjatasks.mgmt.{JobRequest, JobExecution, JobFailure, JobSuccess}
import scala.util.{Failure, Success, Try}

/**
 * Actor responsible for performing the actual processing of job objects.
 * Created by Gilad Ber on 4/15/14.
 */
private[ninjatasks] class Worker extends Actor with ActorLogging
{
	override def preStart() =
	{
		println("Started worker: " + self)
	}

	override def postRestart(reason: Throwable) =
	{
		super.postRestart(reason)
		context.parent ! JobRequest
	}

	override def receive =
	{
		case JobExecution(job, future) =>
			log.info("beginning execution of job id {}", job.id)

			Try(job.withFuture(future).execute()) match {
				case Success(res) => sender() ! JobSuccess(res, job.id, job.workId)
				case Failure(ex) => sender() ! JobFailure(ex, job.id, job.workId)
			}

		case msg =>
			throw new IllegalArgumentException("Invalid input for worker: " + msg + "from sender " + sender)
	}
}
