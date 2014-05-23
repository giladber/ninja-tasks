package org.ninjatasks.main

import akka.actor.{ActorLogging, Actor, Props}
import org.ninjatasks.JobManagementSubsystem
import org.ninjatasks.examples.SleepWork
import org.ninjatasks.work.Work
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 *
 * Created by Gilad Ber on 4/16/14.
 */
object NinjaAppManagement
{
	import org.ninjatasks.utils.ManagementConsts.system
	def main(args: Array[String])
	{
		JobManagementSubsystem.start()
		val c: (Int, Int) => Int = (x, y) => {
			println(s"combining: $x, $y")
			val res = x + y
			println(s"result is $res")
			res
		}
		val work = new SleepWork(555, 4, 3).mapJobResults(x => 2*x, c)
		val reporter = system.actorOf(Props(classOf[WorkReportingActor[Int, Unit, Int]], work), "reporter")
		Thread.sleep(10000)
		reporter ! "send"
	}
}

class WorkReportingActor[T, D, R](work: Work[T, D, R]) extends Actor with ActorLogging
{

	def send(): Unit = JobManagementSubsystem.executor ! (work, 20.seconds)

	import scala.concurrent.ExecutionContext.Implicits.global

	override def receive: Receive = {
		case f: Future[R] => f.andThen
		{
			case Success(res) => log.info("received result of work {}: = {}", work.id, res)
			case Failure(ex) => log.error(ex, "error during work execution")
		}

		case "send" => send()
	}
}
