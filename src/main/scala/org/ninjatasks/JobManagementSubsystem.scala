package org.ninjatasks

import akka.actor.{Props, ActorRef}
import org.ninjatasks.utils.ManagementConsts.{system, WORK_MGR_ACTOR_NAME}
import org.ninjatasks.mgmt.WorkManager

/**
 * This class initiates the ninja tasks work\job management system.
 * Created by Gilad Ber on 5/18/2014.
 */
object JobManagementSubsystem
{
	def start(): ActorRef =
	{
		system.actorOf(Props[WorkManager], WORK_MGR_ACTOR_NAME)
	}
}
