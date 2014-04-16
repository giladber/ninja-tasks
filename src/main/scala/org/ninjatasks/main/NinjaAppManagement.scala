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
		system.actorOf(Props[JobDelegator], JOB_DELEGATOR_ACTOR_NAME)
	}
}
