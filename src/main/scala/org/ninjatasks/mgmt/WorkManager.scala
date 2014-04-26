package org.ninjatasks.mgmt

import akka.actor.{Props, ActorLogging, Actor}
import org.ninjatasks.work.{JobSetIterator, Work}
import scala.collection.mutable
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish

/**
 *
 * Created by Gilad Ber on 4/17/14.
 */
class WorkManager extends Actor with ActorLogging
{

	import org.ninjatasks.utils.ManagementConsts.WORK_TOPIC_NAME

	context.actorOf(Props(classOf[JobExtractor], self, delegator))

	private[this] val mediator = DistributedPubSubExtension(context.system).mediator
	private[this] val delegator = context.actorOf(Props[JobDelegator])

	/**
	 * TODO pending work should be saved in a queue, to preserve insertion order for fairness.
	 * In order for that to happen, the job set iterator neeeds to be wrapped by some trait
	 * which has priority and insertion order semantics.
	 */
	private[this] val pendingWork = new mutable.HashMap[Long, JobSetIterator[_, _]]


	override def receive =
	{
		case work: Work[_, _] =>
			pendingWork put(work.id, work.iterator)
			mediator ! Publish(WORK_TOPIC_NAME, WorkDataMessage(work.id, work.data))


		case JobSetRequest(amount) =>
	}
}

class OrderedPrioritized[T](val data: T, val priority: Int, val serial: Long) extends Ordered[OrderedPrioritized[_]]
{
	override def compare(that: OrderedPrioritized[_])
	{
		this.priority - that.priority match
		{
			case x if x == 0 => (that.serial - this.serial).toInt
			case x if x != 0 => x.toInt
		}
	}
}