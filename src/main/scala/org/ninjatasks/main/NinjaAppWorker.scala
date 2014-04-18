package org.ninjatasks.main

import akka.actor.Props
import org.ninjatasks.utils.ManagementConsts
import org.ninjatasks.exec.WorkerManager

/**
 * Main entry point for worker manager
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
