package org.ninjatasks.main

import akka.actor.{ActorRef, ActorLogging, Actor, Props}
import akka.contrib.pattern.DistributedPubSubExtension
import org.ninjatasks.JobManagementSubsystem
import org.ninjatasks.utils.ManagementConsts
import org.ninjatasks.examples.SleepWork
import org.ninjatasks.utils.ManagementConsts._
import org.ninjatasks.mgmt.WorkCancelled
import org.ninjatasks.mgmt.WorkStarted
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import org.ninjatasks.mgmt.WorkFinished
import org.ninjatasks.mgmt.WorkFailed
import org.ninjatasks.work.Work

/**
 *
 * Created by Gilad Ber on 4/16/14.
 */
object NinjaAppManagement
{
	import org.ninjatasks.utils.ManagementConsts.system
	def main(args: Array[String])
	{
		val mgr = JobManagementSubsystem.start()
		Thread.sleep(10000)
		val work = new SleepWork(555, 4, 3)
		system.actorOf(Props(classOf[WorkReportingActor], mgr, work), "reporter")
	}
}

class WorkReportingActor(mgr: ActorRef, work: Work[_, _, _]) extends Actor with ActorLogging
{
	val mediator = DistributedPubSubExtension(system).mediator
	mediator ! Subscribe(ManagementConsts.WORK_TOPIC_PREFIX + work.id, self)
	mgr ! work

	override def receive: Receive = {
		case WorkStarted(id) => log.info("Work {} has started executing!", id)
		case WorkFinished(id, res) => log.info("Work {} finished with result {}", id, res)
		case WorkCancelled(id) => log.info("Work {} has been successfully cancelled", id)
		case WorkFailed(id, reason) => log.info("Work {} has failed due to exception: {}", id, reason.getMessage)
	}
}
