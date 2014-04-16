package org.ninjatasks.mgmt

import org.ninjatasks.utils.ManagementConsts.WORKER_MGR_ROLE
import akka.actor._
import scala.collection.mutable
import akka.cluster.{MemberStatus, Member, ClusterEvent, Cluster}
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.MemberExited

/**
 * Delegates work to remote worker managers
 * Created by Gilad Ber on 4/16/14.
 */
class JobDelegator extends Actor with ActorLogging
{
	val targetManagers = new mutable.HashSet[ActorSelection]
	val cluster = Cluster(context.system)

	override def preStart() = cluster.subscribe(self, ClusterEvent.InitialStateAsEvents,
																							classOf[MemberUp], classOf[MemberExited])

	override def postStop() = cluster.unsubscribe(self)

	override def receive =
	{
		case JobMessage(job) =>
			targetManagers foreach (_ ! job)

		case MemberUp(m) => targetManagers add member2ActorSelection(m)

		case MemberExited(m) => targetManagers remove member2ActorSelection(m)

		case state: CurrentClusterState =>
			state.members filter isWorkerManager map member2ActorSelection foreach targetManagers.add

		case _ =>
			throw new IllegalArgumentException("Unknown message type receieved!")
	}


	def isWorkerManager: (Member) => Boolean =
	{
		m => m.status == MemberStatus.Up && m.hasRole(WORKER_MGR_ROLE)
	}

	final def member2ActorSelection(member: Member) =
	{
		val result = context.actorSelection(RootActorPath(member.address) / "user" / "ninja")
		println("result of conversion is: " + result)
		result
	}
}
