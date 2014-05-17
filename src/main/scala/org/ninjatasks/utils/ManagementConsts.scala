package org.ninjatasks.utils

import akka.actor.ActorSystem
import akka.cluster.ClusterEvent.{ReachableMember, UnreachableMember, MemberUp, MemberExited}

/**
 *
 * Created by Gilad Ber on 4/16/14.
 */
object ManagementConsts
{
	/**
	 * Name of the actor system in which the ninja-tasks system runs.
	 */
	val systemName = "ninja"

	/**
	 * Actor system of ninja-tasks.
	 */
	val system = ActorSystem(systemName)

	/**
	 * String representing the worker manager role.
	 */
	val WORKER_MGR_ROLE = "WorkerManager"

	/**
	 * String representing the job manager role.
	 */
	val JOB_DELEGATOR_ROLE = "JobDelegator"

	/**
	 * Name of the worker manager actor.
	 */
	val WORKER_MGR_ACTOR_NAME = "worker_manager"

	/**
	 * Name of the job manager actor.
	 */
	val JOB_DELEGATOR_ACTOR_NAME = "job_delegator"

	/**
	 * Name of the topic through which messages are sent to job managers.
	 */
	val MGMT_TOPIC_NAME = "topic-mgmt"

	/**
	 * Name of the topic through which messages are sent to worker managers.
	 */
	val WORK_TOPIC_NAME = "topic-work"

	/**
	 * Prefix for topics to which messages are sent regarding jobs failure/success.
	 * The naming scheme is: Prefix+WORK_ID to register for a topic containing updates to jobs
	 * of the work object with id WORK_ID. For example, to register for job updates regarding work 101,
	 * we would send a Subscribe(JOBS_TOPIC_PREFIX+"101", actor) message.
	 */
	val JOBS_TOPIC_PREFIX = "JOBS$"

	/**
	 * Prefix for topic names to which messages are sent regarding work lifecycle management,
	 * for example messages regarding a work object's processing starting or ending, or being cancelled.
	 * The naming scheme for such topics is Prefix+WORK_ID representing a topic for work of id WORK_ID.
	 * So for example if we wanted to know when  work 500's execution ended, we would send a
	 * Subscribe(WORK_TOPIC_PREFIX + "500", actor) message.
	 */
	val WORK_TOPIC_PREFIX = "WORK$"
}
