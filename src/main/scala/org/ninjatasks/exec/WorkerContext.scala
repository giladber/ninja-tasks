package org.ninjatasks.exec

import scala.concurrent.Promise
import akka.actor.ActorRef
import scala.concurrent.promise

object WorkerContext
{
	def apply(worker: ActorRef, workId: Long) =
		new WorkerContext(promise[Int](), workId, worker)
}

/**
 * Promise used to signal cancellation of work being processed by workers.
 * Created by Gilad Ber on 4/26/2014.
 */
class WorkerContext(val promise: Promise[Int], val workId: Long, val worker: ActorRef)
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
