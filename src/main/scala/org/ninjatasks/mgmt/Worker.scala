package org.ninjatasks.mgmt

import org.ninjatasks.work.Job
import akka.actor.{Actor, ActorLogging}

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
class Worker extends Actor with ActorLogging
{

	override def preStart() =
	{
		println("Started worker: " + self)
		context.parent ! JobRequestMessage(0)
	}

	override def receive =
	{
		case job: Job[_] =>
			log.info(self + "is beginning execution of job id " + job.id)
			sender ! ResultMessage(job.execute)
		case _ => throw new IllegalArgumentException("Invalid input for worker")
	}
}
