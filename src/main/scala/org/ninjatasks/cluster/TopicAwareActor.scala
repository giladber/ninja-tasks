package org.ninjatasks.cluster

import org.ninjatasks.mgmt.{ComponentStartedAck, ComponentStarted}
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import scala.concurrent.duration._

/**
 * Manages relative nodes in the cluster
 * Created by Gilad Ber on 4/18/14.
 */
abstract class TopicAwareActor(receiveTopic: String, targetTopic: String) extends TopicReceivingActor(receiveTopic)
{
	//TODO should lifecycle methods be called async? if so, change docs to reflect that

	protected var replyReceived = false

	import context.dispatcher

	private[this] final def scheduler = context.system.scheduler

	protected final def publish(message: Any) = mediator ! Publish(targetTopic, message)

	override def postSubscribe(): Unit =
	{
		scheduler.scheduleOnce(5 seconds)
		{
			publish(ComponentStarted)
		}
		scheduler.scheduleOnce(6 seconds)
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
				scheduler.scheduleOnce(2 seconds)
				{
					log.debug("Published ack request from {}", self)
					publish(ComponentStarted)
				}
				scheduler.scheduleOnce(3 seconds)
				{
					self ! ComponentStarted
				}
			}

		case ComponentStartedAck =>
			log.debug("Received ack reply from {}", sender())
			replyReceived = true
			postRegister()
	}

}
