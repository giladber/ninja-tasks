package org.ninjatasks.test

import akka.actor.{ActorLogging, Actor, Props}
import org.ninjatasks.examples.SleepWork
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.language.postfixOps
import org.ninjatasks.api.JobManagementSubsystem
import JobManagementSubsystem.WorkResultFuture
import org.ninjatasks.spi.FuncWork
import org.ninjatasks.api.JobManagementSubsystem

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
		val c: (Int, Int) => Int = (x, y) => (1 + x) *(1 + y)
		val s: (Int, String) => Int = (i, s) => i + s.length
		val work = new SleepWork(3, 2).make().
			filter(x => x > 0).
			mapJobs(x => 5 * x, c).
			mapJobs(_.toString, s).
			map(x => "Length of all results combined is " + x).
			foreach(x => println(s"received $x"))
		val reporter = system.actorOf(Props(classOf[WorkReportingActor[Int, Unit, Int]], work), "reporter")
		reporter ! "send"
	}
}

class WorkReportingActor[T, D, R](work: FuncWork[T, D, R]) extends Actor with ActorLogging
{

	def send(): Unit = JobManagementSubsystem.executor !(work, 20.seconds)

	import scala.concurrent.ExecutionContext.Implicits.global

	override def receive: Receive =
	{
		case f: WorkResultFuture[_] => f.andThen
		{
			case Success(either) =>
				either match
				{
					case Right(result) =>
						log.info("received result of work {}: = {}", work, result)
					case Left(msg) =>
						log.info("Execution of work {} did not finish because of: {}", work, msg)
				}

			case Failure(ex) => log.error(ex, "error during work execution")
		}

		case "send" => send()
	}
}
