package org.ninjatasks.cluster

import org.ninjatasks.mgmt.{ComponentStartedAck, ComponentStarted}
import org.ninjatasks.utils.ManagementConsts.config
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import scala.concurrent.duration._

/**
 *
 *
 * Created by Gilad Ber on 4/18/14.
 */
abstract class TopicAwareActor(receiveTopic: String, targetTopic: String) extends TopicReceivingActor(receiveTopic)
{
	//TODO should lifecycle methods be called async? if so, change docs to reflect that

	private[this] var replyReceived = false
	val subscribeInitialDelay = config.getLong("ninja.registration.subscription-req-initial-delay")
	val selfRequestDelayDelta = config.getLong("ninja.registration.subscription-self-req-delta")
	val subscribeRetryDelay = config.getLong("ninja.registration.subscription-retry-delay")

	val initialSelfRequestDelay = subscribeInitialDelay + selfRequestDelayDelta
	val selfRequestRetryDelay = subscribeRetryDelay + selfRequestDelayDelta

	import context.dispatcher

	private[this] final def scheduler = context.system.scheduler

	protected final def publish(message: Any) = mediator ! Publish(targetTopic, message)

	override def postSubscribe(): Unit =
	{
		scheduler.scheduleOnce(subscribeInitialDelay millis)
		{
			publish(ComponentStarted)
		}
		scheduler.scheduleOnce(initialSelfRequestDelay millis)
		{
			self ! ComponentStarted
		}
	}

	/**
	 * Method invoked after registration is successful to target topic.
	 * Empty default implementation.
	 */
	def postRegister(): Unit =
	{}

	override def receive =
	{
		super.receive orElse myReceive
	}

	private[this] def myReceive: Receive =
	{
		case ComponentStarted =>
			val s = sender()
			if (s != self)
			{
				s ! ComponentStartedAck
			}
			else if (!replyReceived)
			{
				scheduler.scheduleOnce(subscribeRetryDelay millis)
				{
					log.debug("Published ack request from {}", self)
					publish(ComponentStarted)
				}
				scheduler.scheduleOnce(selfRequestRetryDelay millis)
				{
					self ! ComponentStarted
				}
			}

		case ComponentStartedAck =>
			log.info("Received ack reply from {}", sender())
			replyReceived = true
			postRegister()
	}

}
