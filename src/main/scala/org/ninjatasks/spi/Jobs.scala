package org.ninjatasks.spi

trait Jobs[T, D] extends Serializable
{
	def creator: JobCreator[T, D]
}

class RichJobs[T, D, M](val base: RichJobs[T, D, M]) extends Jobs[T, D]
{
	def map[U](f: T => U): RichJobs[T, D, U] = base.map(f)

	def accept(jobResult: T): Boolean = base.accept(jobResult)

	def transform(jobResult: T): M = base.transform(jobResult)

	def filter(p: M => Boolean): RichJobs[T, D, M] = base.filter(p)
}

object RichJobs {
	def apply[T, D](jobs: Jobs[T, D]): RichJobs[T, D, T] = new RichJobs(new BaseRichJobs(jobs))
	def apply[T, D](jc: JobCreator[T, D]): RichJobs[T, D, T] = apply(new Jobs[T, D] {override def creator = jc})
}

private[ninjatasks] class BaseRichJobs[T, D](val jobs: Jobs[T, D]) extends RichJobs[T, D, T](null)
{
	override def accept(t: T): Boolean = true

	override def transform(jobResult: T): T = jobResult

	override def map[U](f: T => U): RichJobs[T, D, U] = new JobsWithMap(this, f)

	override def filter(p: T => Boolean): RichJobs[T, D, T] = new JobsWithFilter(this, p)
}



class JobsWithMap[T, D, Old, Cur](val jobs: RichJobs[T, D, Old], val f: T => Cur) extends RichJobs[T, D, Cur](null)
{
	override def map[Target](map: Cur => Target): JobsWithMap[T, D, Cur, Target] = new JobsWithMap(this, t => map(f(t)))

	override def transform(t: T): Cur = f(t)

	override def accept(t: T): Boolean = jobs.accept(t)

	override def filter(cond: Cur => Boolean): RichJobs[T, D, Cur] = new JobsWithFilter(this, x => true)
}

class JobsWithFilter[T, D, U](val jobs: RichJobs[T, D, U], val p: T => Boolean) extends RichJobs[T, D, U](jobs)
{
	override def accept(t: T): Boolean = {
		jobs.accept(t) && p(t)
	}

	override def filter(cond: T => Boolean): RichJobs[T, D, U] = {
		new JobsWithFilter(this, t => p(t) && cond(t))
	}
}
