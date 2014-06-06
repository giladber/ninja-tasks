package org.ninjatasks.spi

/**
 * Container trait for all functional work operations.
 * These operations are implemented by the Work trait, to allow
 * for a fluent DSL in using work objects.
 * @tparam JobT Type of job results
 * @tparam ResT Type of work result
 */
private[ninjatasks] trait WorkOps[JobT, ResT]
{

	def foreach[U](f: JobT => U): WorkOps[JobT, ResT]

	def fold[U](f: (U, JobT) => U)(acc: U): WorkOps[JobT, U]

	def filter(p: JobT => Boolean): WorkOps[JobT, ResT]

	def mapJobs[U](f: JobT => U)(combiner: (ResT, U) => ResT): WorkOps[U, ResT]

	def map[U](f: ResT => U): WorkOps[JobT, U]
}
