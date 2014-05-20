package org.ninjatasks.main

import org.ninjatasks.WorkExecutionSubsystem
/**
 * Main entry point for worker manager
 * Created by Gilad Ber on 4/15/14.
 */
object NinjaAppWorker
{

	def main(args: Array[String])
	{
		WorkExecutionSubsystem.start()
	}
}
