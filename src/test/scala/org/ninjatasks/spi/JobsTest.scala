package org.ninjatasks.spi

import org.ninjatasks.UnitSpec
import scala.language.postfixOps

/**
 * ScalaTest test class for Jobs class and relevant classes.
 * Created by Gilad Ber on 6/14/2014.
 */
class JobsTest extends UnitSpec
{
	"A jobs object" should "map identity successfully" in {
		val jobs = Jobs[Int]
		jobs map(x => x) transform 1 should be (1)
	}

	it should "map multiplication successfully" in {
		val jobs = Jobs[Int]
		jobs map(5 * _) transform 1 should be (5)
	}

	it should "map addition successfully" in {
		val jobs = Jobs[Int]
		jobs map(_ + 1) transform 1 should be (2)
	}

	it should "map to option successfully" in {
		val jobs = Jobs[Int]
		jobs map(Some(_)) transform 1 should be (Some(1))
	}

	it should "compose maps successfully" in {
		val jobs = Jobs[Int]
		jobs map (_ + 1) map (Some(_)) transform 5 should be (Some(6))
	}

	it should "compose maps to 2 different types successfully" in {
		val jobs = Jobs[Int]
		jobs map (_.toDouble) map(Some(_)) transform 5 should be (Some(5.toDouble))
	}

	it should "filter values successfully" in {
		val jobs = Jobs[Int] filter (_ > 2)
		jobs accept 1 should be (false)
		jobs accept 3 should be (true)
	}

	it should "filter other types of values successfully" in {
		val jobs = Jobs[Option[Int]] filter (_.get > 2)
		jobs accept Some(1) should be (false)
		jobs accept Some(3) should be (true)
	}

	it should "compose filter and map successfully (1 each)" in {
		val jobs = Jobs[Int] map(Some(_)) filter (_.get > 2)
		jobs accept 1 should be (false)
		jobs transform 1 should be(Some(1))
		jobs accept 3 should be (true)
		jobs transform 3 should be (Some(3))

		val reverseJobs = Jobs[Int] filter (_ > 2) map(Some(_))
		reverseJobs accept 1 should be (false)
		reverseJobs transform 1 should be (Some(1))
		reverseJobs accept 3 should be (true)
		reverseJobs transform 3 should be (Some(3))
	}

	it should "compose filter and map successfully (more than 1)" in {
		val jobs1 = Jobs[Int] filter (_ < 3) map (_.toDouble + 2.5d) filter (_ <= 3.5d)
		jobs1 accept 1 should be (true)
		jobs1 accept 2 should be (false)
		jobs1 transform 1 should be (3.5)
		jobs1 transform 5 should be (7.5)

		val jobs2 = jobs1 map (_.toInt % 2) filter (_ == 0)
		jobs2 accept 1 should be (false)
		jobs2 transform 1 should be (1)
		jobs2 accept 0 should be (true)
		jobs2 transform 0 should be (0)
	}

}
