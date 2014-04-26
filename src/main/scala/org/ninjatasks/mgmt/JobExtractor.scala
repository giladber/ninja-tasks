package org.ninjatasks.mgmt

import akka.actor.{ActorRef, Cancellable, Actor, ActorLogging}
import scala.concurrent.duration._
import akka.pattern.{ask, pipe, AskTimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 *
 * Created by Gilad Ber on 4/26/2014.
 */
class JobExtractor(val workSource: ActorRef, val delegator: ActorRef) extends Actor with ActorLogging
{
	var cancelOption: Option[Cancellable] = None

	def scheduler = context.system.scheduler

	override def preStart() =
		cancelOption foreach (c => c.cancel())

	val cancellable = scheduler.schedule(initialDelay = 0 seconds, interval = 1 second)
	{
		val f = ask(delegator, JobCapacityRequest)(timeout = 500 millis)
		f map
			{
				case JobCapacity(amount) => JobSetRequest(amount)
				case e: AskTimeoutException => throw e
				case _ => throw new IllegalArgumentException("Invalid response received to job capacity request")
			} pipeTo workSource
	}

	override def receive =
	{
		case ajm: AggregateJobMessage => delegator ! ajm
	}
}
