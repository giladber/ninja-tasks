package org.ninjatasks.execution

import scala.concurrent.Promise
import akka.actor.ActorRef
import scala.concurrent.promise
import java.util.UUID

private[ninjatasks] object WorkerContext
{
	def apply(worker: ActorRef, workId: UUID) =
		new WorkerContext(promise[Int](), workId, worker)
}

/**
 * Promise used to signal cancellation of work being processed by workers.
 * Created by Gilad Ber on 4/26/2014.
 */
private[ninjatasks] class WorkerContext(val promise: Promise[Int], val workId: UUID, val worker: ActorRef)
{

	/**
	 * Finishes execution of the promise object, which will cause its wrapping future
	 * to also stop, hopefully causing the executing job to stop in the near future (depends
	 * on client code).
	 */
	def signalStop(): Unit =
	{
		println("Received signal to stop work " + workId)
		promise.success(1)
	}

}
