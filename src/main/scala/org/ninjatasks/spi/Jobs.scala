package org.ninjatasks.spi

private[ninjatasks] trait Jobs[T, M] extends Serializable
{
	def map[U](f: M => U): Jobs[T, U]

	def transform(jobResult: T): M

	def accept(jobResult: T): Boolean

	def filter(p: M => Boolean): Jobs[T, M]
}

private[ninjatasks] object Jobs {
	def apply[T]: Jobs[T, T] = new UnitJobs[T, T]
}

private[ninjatasks] class UnitJobs[T, D] extends Jobs[T, T]
{
	override def accept(t: T): Boolean = true

	override def transform(jobResult: T): T = jobResult

	override def map[U](f: T => U): Jobs[T, U] = new JobsWithMap(this, f)

	override def filter(p: T => Boolean): Jobs[T, T] = new JobsWithFilter(this, p)
}



private[ninjatasks] class JobsWithMap[T, Old, Cur](val jobs: Jobs[T, Old], val f: Old => Cur) extends Jobs[T, Cur]
{
	override def map[Target](map: Cur => Target): Jobs[T, Target] = new JobsWithMap(this, map)

	override def transform(t: T): Cur = f(jobs.transform(t))

	override def accept(t: T): Boolean = jobs.accept(t)

	override def filter(cond: Cur => Boolean): Jobs[T, Cur] = new JobsWithFilter(this, cond)
}

private[ninjatasks] class JobsWithFilter[T, U](val jobs: Jobs[T, U], val p: U => Boolean) extends Jobs[T, U]
{

	override def map[Target](map: U => Target): Jobs[T, Target] = new JobsWithMap(this, map)

	override def transform(t: T): U = jobs.transform(t)

	override def accept(t: T): Boolean = p(transform(t)) && jobs.accept(t)

	override def filter(cond: U => Boolean): Jobs[T, U] = new JobsWithFilter(this, cond)

}
