/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import xsbti.{AnalysisCallback,Logger}
import scala.tools.nsc.{Phase, SubComponent}

class CompilerInterface
{
	def run(args: Array[String], callback: AnalysisCallback, maximumErrors: Int, log: Logger)
	{
			def debug(msg: => String) = log.debug(Message(msg))
			import scala.tools.nsc.{CompilerCommand, FatalError, Global, Settings, reporters, util}
			import util.FakePos

		debug("Interfacing (CompilerInterface) with Scala compiler " + scala.tools.nsc.Properties.versionString)

		val reporter = new LoggerReporter(maximumErrors, log)
		val settings = new Settings(reporter.error)
		val command = new CompilerCommand(args.toList, settings, error, false)

		val phasesSet = new scala.collection.mutable.HashSet[Any] // 2.7 compatibility
		object compiler extends Global(command.settings, reporter)
		{
			object sbtAnalyzer extends
			{
				val global: compiler.type = compiler
				val phaseName = Analyzer.name
				val runsAfter = List("jvm")
				override val runsBefore = List("terminal")
				val runsRightAfter = None
			}
			with SubComponent with Compat27
			{
				val analyzer = new Analyzer(global, callback)
				def newPhase(prev: Phase) = analyzer.newPhase(prev)
				def name = phaseName
			}
			lazy val pdescriptors = // done this way for compatibility between 2.7 and 2.8
			{
				phasesSet += sbtAnalyzer
				val superd = super.phaseDescriptors
				if(superd.contains(sbtAnalyzer)) superd else ( super.phaseDescriptors ++ Seq(sbtAnalyzer) ).toList
			}
			override def phaseDescriptors = pdescriptors
			trait Compat27 { val runsBefore: List[String] = Nil }
		}
		if(!reporter.hasErrors)
		{
			val run = new compiler.Run
			debug(args.mkString("Calling compiler with arguments  (CompilerInterface):\n\t", "\n\t", ""))
			run compile command.files
		}
		reporter.printSummary()
		if(reporter.hasErrors)
		{
			debug("Compilation failed (CompilerInterface)")
			throw new xsbti.CompileFailed { val arguments = args; override def toString = "Analyzed compilation failed" }
		}
	}
}