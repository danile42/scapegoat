package com.sksamuel.scapegoat

import java.io.File

import com.sksamuel.scapegoat.io.IOUtils

import scala.tools.nsc._
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.transform.{Transform, TypingTransformers}

class ScapegoatPlugin(val global: Global) extends Plugin {

  override val name: String = "scapegoat"
  override val description: String = "scapegoat compiler plugin"
  val component = new ScapegoatComponent(global, ScapegoatConfig.inspections)
  override val components: List[PluginComponent] = List(component)

  override def init(options: List[String], error: String => Unit): Boolean = {
    options.find(_.startsWith("disabled:")) match {
      case Some(option) =>
        component.disabled = option.drop("disabled:".length).split(',')
      case _ =>
    }
    options.find(_.startsWith("dataDir:")) match {
      case Some(option) =>
        component.dataDir = new File(option.drop("dataDir:".length))
        true
      case None =>
        error("-P:scapegoat:dataDir not specified")
        false
    }
  }

  override val optionsHelp: Option[String] = Some(Seq(
    "-P:scapegoat:dataDir:<pathtodatadir>    where the report should be written\n" +
      "-P:scapegoat:disabled:<listofinspections>    comma separated list of disabled inspections\n"
  ).mkString("\n"))
}

class ScapegoatComponent(val global: Global, inspections: Seq[Inspection])
  extends PluginComponent with TypingTransformers with Transform {

  require(inspections != null)

  import global._

  val feedback = new Feedback()
  var dataDir: File = new File(".")
  var disabled: Seq[String] = Nil

  override val phaseName: String = "scapegoat"
  override val runsAfter: List[String] = List("typer")
  override val runsBefore = List[String]("patmat")

  override def newPhase(prev: scala.tools.nsc.Phase): Phase = new Phase(prev) {
    override def run(): Unit = {
      println("[info] [scapegoat]: Begin anaylsis...")
      super.run()
      val count = feedback.warnings.size
      println(s"[warn] [scapegoat]: Anaylsis complete - $count warnings found.")
      val html = IOUtils.writeHTMLReport(dataDir, feedback)
      println(s"[info] [scapegoat]: Written HTML report [$html]")
      val xml = IOUtils.writeXMLReport(dataDir, feedback)
      println(s"[info] [scapegoat]: Written XML report [$xml]")
    }
  }

  protected def newTransformer(unit: CompilationUnit): Transformer = new Transformer(unit)

  class Transformer(unit: global.CompilationUnit) extends TypingTransformer(unit) {
    override def transform(tree: Tree) = {
      require(inspections != null)
      inspections
        .filterNot(inspection => disabled.contains(inspection.getClass.getSimpleName))
        .map(new SuppressAwareInspection(_))
        .foreach(_.traverser(global, feedback).traverse(tree))
      tree
    }
  }
}