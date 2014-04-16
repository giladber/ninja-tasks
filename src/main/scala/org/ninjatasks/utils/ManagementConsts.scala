package org.ninjatasks.utils

import akka.actor.ActorSystem

/**
 *
 * Created by Gilad Ber on 4/16/14.
 */
object ManagementConsts
{
	val systemName = "ninja"
	val system = ActorSystem(systemName)

	val WORKER_MGR_ROLE = "WorkerManager"
	val JOB_DELEGATOR_ROLE = "JobDelegator"

	val WORKER_MGR_ACTOR_NAME = "worker_manager"
	val JOB_DELEGATOR_ACTOR_NAME = "job_delegator"
}
