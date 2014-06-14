package org.ninjatasks.spi

import org.ninjatasks.UnitSpec

/**
 * Tests for the ResultUpdater class.
 * Created by Gilad Ber on 6/14/2014.
 */
class ResultUpdaterTest extends UnitSpec
{

	trait Updaters {
		val plus = ResultUpdater((a: Int, b: Int) => a + b, 0)
		val mult = ResultUpdater((a: Int, b: Int) => a * b, 1)
	}

	"A result updater" should "combine addition successfully" in {
		new Updaters {
			plus map (x => x) update 1
			plus.result should be(1)
		}
	}

	it should "combine consecutive values successfully" in {
		new Updaters {
			plus update 1
			plus update 2
			plus.result should be (3)

			mult update 2
			mult update 3
			mult.result should be (6)
		}
	}

	it should "map identity successfully" in {
		new Updaters {
			plus map (x => x) update 1
			plus.result should be(1)
		}
	}

	it should "map constant addition successfully" in {
		new Updaters {
			val pplus = plus map(_ + 5)
			pplus update 3
			pplus.result should be (8)

			val pmult = mult map (_ + 1)
			pmult update 2
			pmult.result should be (3)
			pmult update 3
			pmult.result should be (7)
		}
	}

	it should "compose 2 maps successfully" in {
		new Updaters {
			val u = mult map(_ * 2) map (_ + 3)
			u update 2
			u.result should be (7)
			u update 3
			u.result should be (15)
		}
	}

	it should "compose 3 maps successfully" in {
		new Updaters {
			val u = mult map(_ * 2) map (_ + 3) map (_ % 17)
			u update 2
			u.result should be (7)
			u update 3
			u.result should be (15)
			u update 2
			u.result should be (10)
		}
	}

	it should "filter jobs successfully" in {
		new Updaters {
			val f = plus filter(_ > 5)
			f update 1
			f.result should be (0)
			f update 5
			f.result should be (0)
			f update 6
			f.result should be (6)
			f update 1
			f.result should be (6)
			f update 7
			f.result should be (13)
		}
	}

	it should "apply 2 filters successfully" in {
		new Updaters {
			val f = mult filter (_ > 3) filter (_ % 7 != 0)
			f update 2
			f.result should be (1)
			f update 4
			f.result should be (4)
			f update 7
			f.result should be (4)
			f update 8
			f.result should be (32)
			f update 14
			f.result should be (32)
		}
	}
}
