package org.ninjatasks.main

import akka.actor.Props
import org.ninjatasks.mgmt.WorkerManager
import org.ninjatasks.utils.ManagementConsts

/**
 * Main entry point
 * Created by Gilad Ber on 4/15/14.
 */
object NinjaAppWorker
{

	def main(args: Array[String])
	{
		import org.ninjatasks.utils.ManagementConsts.system
		system.actorOf(Props[WorkerManager], ManagementConsts.WORKER_MGR_ACTOR_NAME)
	}
}
