package org.ninjatasks.mgmt

import akka.actor.{ActorRef, Cancellable, Actor, ActorLogging}
import scala.concurrent.duration._
import akka.pattern.{ask, pipe, AskTimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global
import org.ninjatasks.utils.ManagementConsts

/**
 *
 * Created by Gilad Ber on 4/26/2014.
 */
class JobExtractor(val workSource: ActorRef, val delegator: ActorRef) extends Actor with ActorLogging
{
	var cancelOption: Option[Cancellable] = None
	val config = ManagementConsts.config
	val initialDelay = config.getLong("ninja.extractor.initial-delay")
	val periodicDelay = config.getLong("ninja.extractor.periodic-delay")
	val capacityRequestTimeout = config.getLong("ninja.extractor.request-timeout")

	def scheduler = context.system.scheduler

	override def preStart() =
	{
		cancelOption foreach (c => c.cancel())
		val cancellable = scheduler.schedule(initialDelay = initialDelay millis, interval = periodicDelay millis)
		{
			val f = ask(delegator, JobCapacityRequest)(timeout = capacityRequestTimeout millis)
			f map
				{
					case JobCapacity(amount) => JobSetRequest(amount)
					case e: AskTimeoutException => throw e
					case _ => throw new IllegalArgumentException("Invalid response received to job capacity request")
				} pipeTo workSource
		}

		cancelOption = Some(cancellable)
	}

	override def receive =
	{
		case ajm: AggregateJobMessage => delegator ! ajm
	}
}
