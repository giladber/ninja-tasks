package org.ninjatasks.execution

import java.util.UUID

import akka.testkit.TestActorRef
import org.ninjatasks.WorkExecUnitSpec
import org.ninjatasks.examples.SleepJob
import org.ninjatasks.spi.ManagedJob
import org.ninjatasks.taskmanagement.{JobSuccess, JobMessage, WorkDataRemoval, WorkDataMessage}

/**
 * Unit tests for WorkerManager class.
 * Created by Gilad Ber on 6/28/2014.
 */
class WorkerManagerTest extends WorkExecUnitSpec
{
	trait WMTest {
		val actorRef = TestActorRef[WorkerManager]
		val actor = actorRef.underlyingActor
		val id = UUID.randomUUID()
		val jobMsg = JobMessage(ManagedJob(SleepJob(1, 2, 3), id))
	}

	"A worker manager" should "have the correct number of child actors" in {
		WorkerManager.WORKER_NUM should be (Runtime.getRuntime.availableProcessors())
	}

	it should "add work data after receiving a work data message" in {
		new WMTest {
			actor.receive(WorkDataMessage(id, ()))
			actor.workData(id) should be (())
		}
	}

	it should "remove work data with work removal message" in {
		new WMTest {
			actor.receive(WorkDataMessage(id, ()))
			actor.workData(id) should be (())
			actor.receive(WorkDataRemoval(id))
			actor.workData.get(id) should be (None)
		}
	}

	it should "start with a request queue of size = WORKER_NUM" in {
		new WMTest {
			actor.requestQueue.size should be (WorkerManager.WORKER_NUM)
		}
	}

	it should "put a job message in its job queue" in {
		new WMTest {
			actor.requestQueue.clear()
			actor.receive(jobMsg)
			actor.jobQueue.size should be (1)
		}
	}
}
