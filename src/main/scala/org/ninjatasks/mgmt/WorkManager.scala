package org.ninjatasks.mgmt

import akka.actor.{Props, ActorLogging, Actor}
import org.ninjatasks.work.Work
import scala.collection.mutable

/**
 *
 * Created by Gilad Ber on 4/17/14.
 */
class WorkManager extends Actor with ActorLogging
{
	val delegator = context.actorOf(Props[JobDelegator])
	val jobExtractor = context.actorOf(Props(classOf[JobExtractor], self, delegator))
	private[this] val pendingWork = new mutable.HashMap[Long, Work[_, _]]

	override def receive =
	{
		case work: Work[_, _] =>
		{
			pendingWork put(work.id, work)
		}


		//TODO add usage of job creators

		case WorkDelegationMessage(to) =>
	}
}
