package org.ninjatasks.api

import akka.actor.Props
import org.ninjatasks.execution.WorkerManager
import org.ninjatasks.utils.ManagementConsts.{system, WORKER_MGR_ACTOR_NAME}


/**
 * This class initiates the ninja-tasks work execution sub-system.
 * Created by Gilad Ber on 5/18/2014.
 */
object WorkExecutionSubsystem
{
	def start(): Unit =
	{
		system.actorOf(Props[WorkerManager], WORKER_MGR_ACTOR_NAME)
	}
}
