package org.ninjatasks.mgmt

import akka.actor.{ActorLogging, Actor}
import org.ninjatasks.work.Work
import scala.collection.mutable
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import org.ninjatasks.utils.ManagementConsts.WORK_TOPIC_NAME

/**
 *
 * Created by Gilad Ber on 4/17/14.
 */
class WorkManager extends Actor with ActorLogging
{
	val mediator = DistributedPubSubExtension(context.system).mediator
	private val pendingWork = new mutable.HashSet[Work[_, _]]()

	override def receive =
	{
		case work: Work[_, _] =>
		{
			pendingWork add work
			mediator ! Publish(WORK_TOPIC_NAME, WorkDataMessage(work.id, work.data))
		}

		case WorkDelegationMessage(to) =>
	}
}
