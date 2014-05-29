package org.ninjatasks.utils

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.ninjatasks.api.ManagementLookupBus

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

	val lookupBus = new ManagementLookupBus


	/**
	 * Actor system of ninja-tasks.
	 */
	val system = ActorSystem(systemName)

	/**
	 * Ninja-tasks lib config.
	 * Backed by the contained reference.conf file.
	 */
	val config = system.settings.config.withFallback(ConfigFactory.defaultReference())

	val WORKER_MGR_ROLE = "WorkerManager"

	val JOB_DELEGATOR_ROLE = "JobDelegator"

	val WORKER_MGR_ACTOR_NAME = "worker_manager"

	val JOB_DELEGATOR_ACTOR_NAME = "job_delegator"

	val WORK_MGR_ACTOR_NAME = "work_mgr"

	val JOB_EXTRACTOR_ACTOR_NAME = "job_extractor"

	val WORK_EXECUTOR_ACTOR_NAME = "work_executor"

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
