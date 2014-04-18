package org.ninjatasks.main

import org.ninjatasks.mgmt.{JobMessage, JobDelegator}
import akka.actor.Props
import org.ninjatasks.work.SleepJob

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
		actor ! JobMessage(SleepJob(2000, 1, 5, 1010100))
	}
}
