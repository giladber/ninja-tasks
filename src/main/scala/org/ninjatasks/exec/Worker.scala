package org.ninjatasks.exec

import akka.actor.{Actor, ActorLogging}
import org.ninjatasks.mgmt.{JobRequest, JobExecution, JobFailure, JobSuccess}

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
class Worker extends Actor with ActorLogging
{
	private[this] var stop = false

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
			stop = false
			log.info("{} is beginning execution of job id {}", self, job.id)
			try
			{
				sender ! JobSuccess(job.withFuture(future).execute(), job.id)
			}
			catch
				{
					case e: Exception => sender ! JobFailure(e, job.id)
				}
		case msg =>
			throw new IllegalArgumentException("Invalid input for worker: " + msg + "from sender " + sender)
	}
}
