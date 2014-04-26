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
	 * Signals the targeted actor to stop if it is executing jobs from the parameter work.
	 * @param cancelledWorkId ID of work which should be cancelled
	 */
	def signalStop(cancelledWorkId: Long): Unit =
		if (cancelledWorkId == workId)
		{
			promise.success(1)
		}

}
