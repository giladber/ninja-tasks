package org.ninjatasks.main

import org.ninjatasks.mgmt.JobDelegator
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
		val actor = system.actorOf(Props[JobDelegator], JOB_DELEGATOR_ACTOR_NAME)
		Thread.sleep(5000)
		//		actor ! JobMessage(SleepJob(5000, 1, 5, 1010100))
		//		println("Sent sleepjob msg")
	}
}
