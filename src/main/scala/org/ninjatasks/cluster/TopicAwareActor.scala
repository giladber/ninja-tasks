package org.ninjatasks.cluster

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator._
import org.ninjatasks.mgmt.{ComponentStartedAck, ComponentStarted}
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.contrib.pattern.DistributedPubSubMediator.Unsubscribe
import akka.contrib.pattern.DistributedPubSubMediator.UnsubscribeAck
import akka.contrib.pattern.DistributedPubSubMediator.SubscribeAck
import scala.concurrent.duration._

/**
 * Manages relative nodes in the cluster
 * Created by Gilad Ber on 4/18/14.
 */
abstract class TopicAwareActor(subscriptionTopic: String, registrationTopic: String) extends Actor with ActorLogging
{
	protected val mediator: ActorRef = DistributedPubSubExtension(context.system).mediator
	protected var replyReceived = false

	import context.dispatcher

	private[this] final def scheduler = context.system.scheduler

	final def publish(message: Any) = mediator ! Publish(registrationTopic, message)

	override def preStart() = mediator ! Subscribe(subscriptionTopic, self)

	override def postStop() = mediator ! Unsubscribe(subscriptionTopic, self)

	def postSubscribe(): Unit =
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

	def postRegister(): Unit =
	{}

	override def receive =
	{
		case SubscribeAck(s) =>
		{
			log.info("Actor {} subscribed to topic {}", s.ref, s.topic)
			postSubscribe()
		}

		case UnsubscribeAck(s) => log.info("Actor {} unsubscribed from topic {}", s.ref, s.topic)

		case ComponentStarted =>
			if (sender() != self)
			{
				sender() ! ComponentStartedAck
			}
			else if (!replyReceived)
			{
				scheduler.scheduleOnce(2 seconds)
				{
					println("Published ack request from " + self)
					publish(ComponentStarted)
				}
				scheduler.scheduleOnce(3 seconds)
				{
					self ! ComponentStarted
				}
			}

		case ComponentStartedAck =>
			println("Received ack reply from " + sender())
			replyReceived = true
			postRegister()
	}

}
