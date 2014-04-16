package org.ninjatasks.mgmt

import org.ninjatasks.work.Job

/**
 *
 * Created by Gilad Ber on 4/15/14.
 */
case class JobMessage[T](job: Job[T])

case class ResultMessage[T](res: T)

case class JobRequestMessage(dummy: Byte)
