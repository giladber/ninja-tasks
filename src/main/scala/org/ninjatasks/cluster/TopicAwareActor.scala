package org.ninjatasks.cluster

import akka.actor.{ActorLogging, Actor}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{UnsubscribeAck, SubscribeAck, Unsubscribe, Subscribe}

/**
 * Manages relative nodes in the cluster
 * Created by Gilad Ber on 4/18/14.
 */
class TopicAwareActor(topic: String) extends Actor with ActorLogging
{
	protected val mediator = DistributedPubSubExtension(context.system).mediator

	override def preStart() = mediator ! Subscribe(topic, self)

	override def postStop() = mediator ! Unsubscribe(topic, self)

	override def receive =
	{
		case SubscribeAck(s) => log.info("Actor {} subscribed to topic {}", s.ref, s.topic)

		case UnsubscribeAck(s) => log.info("Actor {} unsubscribed from topic {}", s.ref, s.topic)
	}

}
