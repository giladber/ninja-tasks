package org.ninjatasks.cluster

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.contrib.pattern.DistributedPubSubMediator.{Unsubscribe, Subscribe, UnsubscribeAck, SubscribeAck}
import akka.contrib.pattern.DistributedPubSubExtension

/**
 *
 * Created by Gilad Ber on 4/26/2014.
 */
private[ninjatasks] class TopicReceivingActor(receiveTopic: String) extends Actor with ActorLogging
{

	protected val mediator: ActorRef = DistributedPubSubExtension(context.system).mediator

	override def preStart() = mediator ! Subscribe(receiveTopic, self)

	override def postStop() = mediator ! Unsubscribe(receiveTopic, self)

	/**
	 * This method is called once a SubscribeAck is received.
	 * Empty default implementation.
	 */
	def postSubscribe(): Unit =
	{}

	/**
	 * This method is called once an UnsubscribeAck is received.
	 * Empty default implementation.
	 */
	def postUnsubscribe(): Unit =
	{}

	override def receive =
	{
		case SubscribeAck(s) =>
			log.info("Actor {} subscribed to topic {}", s.ref, s.topic)
			postSubscribe()

		case UnsubscribeAck(s) =>
			log.info("Actor {} un-subscribed from topic {}", s.ref, s.topic)
			postUnsubscribe()
	}

}
