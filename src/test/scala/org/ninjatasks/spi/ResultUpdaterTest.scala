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
			val id = plus map (x => x)
			id update 1
			id.result should be(1)
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

	it should "map jobs successfully" in {
		new Updaters {
			val map : Int => Int = x => 5 * x
			val mj = plus.mapJobs(map)((x: Int, y: Int) => (x + 1) * y)
			mj update 1
			mj.result should be (5)
			mj update 2
			mj.result should be (60)
		}
	}

	it should "apply two job maps successfully" in {
		new Updaters {
			val map: Int => Double = x => x.toDouble + 1d
			val map2: Double => List[Double] = x => List(x)
			val mj = plus.mapJobs(map)((x: Int, y: Double) => (x + y).toInt).
				mapJobs(map2)((x: Int, list: List[Double]) => (x + 2 * list(0)).toInt)

			mj update 2
			mj.result should be (6)
			mj update 3
			mj.result should be (14)
		}
	}

	it should "apply foreach successfully" in {
		new Updaters {
			var sum: Int = 0
			val aggregator = plus foreach(x => sum = sum + x)
			aggregator update 1
			sum should be (1)
			aggregator update 2
			sum should be (3)
			aggregator update 3
			sum should be (6)
		}
	}

	it should "compose foreach after filter" in {
		new Updaters {
			var x: Int = 0
			val u = plus filter (_ > 0) foreach (y => x = x + y)
			u update -1
			x should be (0)
			u update 1
			x should be (1)
			u update 0
			x should be (1)
			u update -2
			x should be (1)
			u update 5
			x should be (6)
		}
	}

	it should "fold job results successfully" in {
		new Updaters {
			val map: (List[Int], Int) => List[Int] = (list, e) => list ::: (e :: Nil)
			val folded = plus.fold(map)(Nil)
			folded update 1
			folded.result should be (List(1))
			folded update 2
			folded.result should be (List(1, 2))
			for (i <- 1 to 5) folded update 1
			folded.result should be (List(1, 2, 1, 1, 1, 1, 1))
		}
	}

	it should "compose fold after filter successfully" in {
		new Updaters {
			val map: (List[Int], Int) => List[Int] = (list, e) => list ::: (e :: Nil)
			val foldThenFilter = plus.fold(map)(Nil) filter (_ > 0)
			val filterThenFold = plus.filter(_ > 0).fold(map)(Nil)

			filterThenFold update 1
			filterThenFold.result should be (List(1))
			filterThenFold update -1
			filterThenFold.result should be (List(1))

			foldThenFilter update 1
			foldThenFilter.result should be (List(1))
			foldThenFilter update -1
			foldThenFilter.result should be (List(1))
		}
	}

	it should "compose result map with job map" in {
		new Updaters {
			val combine: (List[Int], Int) => List[Int] = (list, x) => list ::: (x :: Nil)
			val rmap: Int => List[Int] = x => List(x)
			val u = plus.map(rmap).mapJobs(_ * 2)(combine)

			u.result should be (List(0))
			u update 1
			u.result should be (List(0, 2))
			u update 2
			u.result should be (List(0, 2, 4))
		}
	}

	it should "compose result map with job map with filter" in {
		val combine: (List[Int], Int) => List[Int] = (list, x) => list ::: (x :: Nil)
		val rmap: Int => List[Int] = x => List(x)
		new Updaters
		{
			val u = plus.filter(_ > 0).map(rmap).mapJobs(_ * 2)(combine)

			u.result should be(List(0))
			u update -1
			u.result should be(List(0))
			u update 1
			u.result should be(List(0, 2))
			u update -2
			u.result should be(List(0, 2))
			u update 1
			u.result should be(List(0, 2, 2))
		}

		new Updaters {
			val u2 = plus.map(rmap).mapJobs(_ * 2)(combine).filter(_ > 4)
			u2.result should be (List(0))
			u2 update 2
			u2.result should be (List(0))
			u2 update 3
			u2.result should be (List(0, 6))
			u2 update 1
			u2.result should be (List(0, 6))
		}
	}

	it should "compose filter with fold with map jobs" in {
		new Updaters {
			val map = (list: List[Int], list2: List[Int]) => list ::: (list2 map(_ * 2))
			val combine: (Int, List[Int]) => Int = (x, list) => x + list(0)
			val u = plus.filter(_ > 0).mapJobs(List(_))(combine).fold(map)(Nil)

			u.result should be (Nil)
			u update 1
			u.result should be (List(2))
			u update 0
			u.result should be (List(2))
			u update -1
			u.result should be (List(2))
			u update 5
			u.result should be (List(2, 10))
		}
	}

}
