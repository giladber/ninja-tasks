package org.ninjatasks.main

import org.ninjatasks.mgmt.{WorkManager, JobDelegator}
import akka.actor.Props

/**
 *
 * Created by Gilad Ber on 4/16/14.
 */
object NinjaAppManagement
{
	def main(args: Array[String])
	{
		import org.ninjatasks.utils.ManagementConsts.{system, JOB_DELEGATOR_ACTOR_NAME}
		system.actorOf(Props[WorkManager])
//		Thread.sleep(5000)
		//		actor ! JobMessage(SleepJob(5000, 1, 5, 1010100))
		//		println("Sent sleepjob msg")
	}
}
