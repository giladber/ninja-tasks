package org.ninjatasks

import akka.actor.ActorSystem
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}


/**
 * Unit spec for testing actors.
 * Created by Gilad Ber on 6/28/2014.
 */
class WorkExecUnitSpec extends TestKit(
	ActorSystem("ninja", ConfigFactory.parseResources("worker-application.conf")) )
	with DefaultTimeout with ImplicitSender
	with FlatSpecLike with Matchers with BeforeAndAfterAll
{

	override def afterAll()
	{
		shutdown()
	}

}
