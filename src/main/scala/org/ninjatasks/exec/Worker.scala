package org.ninjatasks.exec

import org.ninjatasks.work.Job
import akka.actor.{Actor, ActorLogging}
import org.ninjatasks.mgmt.ResultMessage

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
class Worker extends Actor with ActorLogging
{

	override def preStart() =
	{
		println("Started worker: " + self)
	}

	override def receive =
	{
		case job: Job[_, _] =>
			log.info(self + "is beginning execution of job id " + job.id)
			sender ! ResultMessage(job.execute(), job.id)
		case msg =>
			throw new IllegalArgumentException("Invalid input for worker: " + msg + "from sender " + sender)
	}
}
