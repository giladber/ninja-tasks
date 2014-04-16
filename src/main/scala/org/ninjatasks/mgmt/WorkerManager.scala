package org.ninjatasks.mgmt

import akka.actor._
import akka.cluster.{MemberStatus, Member, ClusterEvent, Cluster}
import scala.collection.mutable
import org.ninjatasks.work.Job
import akka.cluster.ClusterEvent._
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp

object WorkerManager
{
	val WORKER_NUM = System.getProperty("workerNum", "-1") match
	{
		case "-1" => Runtime.getRuntime.availableProcessors()
		case _ => System.getProperty("workerNum").toInt
	}
	val managers = new mutable.HashSet[ActorSelection]()
}


/**
 * The WorkerManager manages job executing workers.
 * It delegates work to them, resets them when needed, etc.
 * There should only be one WorkerManager per machine!
 * Created by Gilad Ber on 4/15/14.
 */
class WorkerManager extends Actor with ActorLogging
{

	import org.ninjatasks.utils.ManagementConsts.JOB_DELEGATOR_ROLE
	import WorkerManager.{WORKER_NUM, managers}

	val cluster = Cluster(context.system)
	val requestQueue = new mutable.Queue[ActorRef]()
	val jobQueue = new mutable.PriorityQueue[Job[_]]()
	val childActors = new mutable.HashSet[ActorRef]()


	override def preStart() =
	{
		childActors ++= (1 to WORKER_NUM) map (s => context.actorOf(Props[Worker], "worker" + s))

		cluster.subscribe(self, ClusterEvent.InitialStateAsEvents,
											classOf[MemberUp], classOf[MemberExited])
	}

	override def postStop() = cluster.unsubscribe(self)


	override def receive =
	{
		case JobMessage(job) =>
			jobQueue += job
			if (!requestQueue.isEmpty)
			{
				requestQueue.dequeue ! job
			}

		case ResultMessage(result) =>
			requestQueue += sender
			if (!jobQueue.isEmpty)
			{
				requestQueue.dequeue ! jobQueue.dequeue
			}
			managers foreach (_ ! result)

		case JobRequestMessage(d) =>
			requestQueue += sender

		case MemberUp(m) =>
			managers add member2ActorSelection(m)

		case MemberExited(m) =>
			managers remove member2ActorSelection(m)

		case state: CurrentClusterState =>
			state.members filter isJobDelegator map member2ActorSelection foreach managers.add

	}

	def isJobDelegator: (Member) => Boolean =
		m => m.status == MemberStatus.Up && m.hasRole(JOB_DELEGATOR_ROLE)

	final def member2ActorSelection(member: Member) =
	{
		val result = context.actorSelection(RootActorPath(member.address) / "user" / "ninja")
		println("result of conversion of member " + member + " is: " + result)
		result
	}
}
