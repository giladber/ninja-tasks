package org.ninjatasks.taskmanagement

import akka.actor._
import scala.language.postfixOps

/**
 * An actor dedicated to performing combine operations
 * Created by Gilad Ber on 6/6/2014.
 */
class CombinePerformer extends Actor with ActorLogging
{

	override def receive: Receive =
	{
		case CombineRequest(work, s) =>
			work.update(s.res.asInstanceOf[work.baseJobType])
			sender() ! CombineAck(work.id)
	}

}