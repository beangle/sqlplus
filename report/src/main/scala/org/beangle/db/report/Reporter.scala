/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.db.report

import java.io.{ File, FileInputStream }
import java.util.Locale
import org.beangle.commons.io.Files.{ / => /, forName, stringWriter }
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.Strings.{ isEmpty, substringAfterLast, substringBefore, substringBeforeLast }
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.{ Schema, Table }
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.db.report.model.{ Module, Report }
import org.beangle.template.freemarker.BeangleObjectWrapper
import org.umlgraph.doclet.UmlGraph
import freemarker.cache.{ ClassTemplateLoader, FileTemplateLoader, MultiTemplateLoader }
import freemarker.template.Configuration
import javax.sql.DataSource
import java.io.BufferedReader
import java.io.InputStreamReader
import org.beangle.commons.collection.Collections

object MultiReport extends Logging {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      logger.info("Usage: Reporter /path/to/your/report/dir")
      return
    }
    findReportXML(new File(args(0))) foreach (xml => Reporter.main(Array(xml)))
  }

  private def findReportXML(dir: File): List[String] = {
    val xmls = Collections.newBuffer[String]
    dir.listFiles() foreach { f =>
      if (f.isDirectory()) xmls ++= findReportXML(f)
      else {
        if (f.getName.endsWith(".xml")) xmls += f.getAbsolutePath
      }
    }
    xmls.toList
  }
}

object Reporter extends Logging {

  val DotReady = checkDot()

  def main(args: Array[String]): Unit = {
    if (!checkJdkTools()) {
      logger.info("Report need tools.jar which contains com.sun.tools.javadoc utility.")
      return ;
    }
    if (args.length < 1) {
      logger.info("Usage: Reporter /path/to/your/report.xml -debug")
      return
    }

    val reportxml = new File(args(0))
    var dir = reportxml.getAbsolutePath()
    dir = substringBeforeLast(dir, /) + / + substringBefore(substringAfterLast(dir, /), ".xml") + /
    logger.info(s"All wiki and images will be generated in $dir")
    val xml = scala.xml.XML.load(new FileInputStream(reportxml))
    val reporter = new Reporter(Report(xml), dir)
    reporter.filterTables()

    val debug = if (args.length > 1) args(1) == "-debug" else false
    if (debug) {
      logger.info("Debug Mode:Type gen to generate report again,or q or exit to quit!")
      var command = "gen"
      do {
        if (command == "gen") gen(reporter)
        print("gen/exit:")
        command = Console.in.readLine();
      } while (command != "exit" && command != "q")
    } else {
      gen(reporter)
    }
  }

  def gen(reporter: Reporter) {
    try {
      reporter.genWiki()
      reporter.genImages()
      reporter.close()
      logger.info("report generate complete.")
    } catch {
      case e: Exception => e.printStackTrace
    }
  }

  private def checkJdkTools(): Boolean = {
    try {
      ClassLoaders.load("com.sun.tools.javadoc.Main")
    } catch {
      case e: Exception => false
    }
    true
  }

  private def checkDot(): Boolean = {
    val pb = new ProcessBuilder("which", "dot")
    pb.redirectErrorStream(true)
    val pro = pb.start()
    pro.waitFor()
    val reader = new BufferedReader(new InputStreamReader(pro.getInputStream()))
    val line = reader.readLine()
    reader.close()
    !line.contains("which: no")
  }

}

class Reporter(val report: Report, val dir: String) extends Logging {
  val dbconf = report.dbconf
  val ds = DataSourceUtils.build(dbconf.driver, dbconf.user, dbconf.password, dbconf.props)
  val database = new Schema(report.dbconf.dialect, null, dbconf.schema)

  val meta = ds.getConnection().getMetaData()
  database.loadTables(meta, true)
  database.loadSequences(meta)

  val cfg = new Configuration(Configuration.VERSION_2_3_24)
  cfg.setEncoding(Locale.getDefault, "UTF-8")
  val overrideDir = new File(dir + ".." + / + "template")
  if (overrideDir.exists) {
    logger.info(s"Load override template from ${overrideDir.getAbsolutePath()}")
    cfg.setTemplateLoader(new MultiTemplateLoader(Array(new FileTemplateLoader(overrideDir), new ClassTemplateLoader(getClass, "/template"))))
  } else
    cfg.setTemplateLoader(new ClassTemplateLoader(getClass, "/template"))
  cfg.setObjectWrapper(new BeangleObjectWrapper)

  def filterTables() {
    val lastTables = new collection.mutable.HashSet[Table]
    lastTables ++= database.tables.values
    for (module <- report.modules) module.filter(lastTables)
    for (image <- report.images) image.select(database.tables.values)
    report.tables = database.tables.values.filterNot(lastTables.contains(_))
  }

  def genWiki() {
    val data = new collection.mutable.HashMap[String, Any]
    data += ("dialect" -> report.dbconf.dialect)
    data += ("tablesMap" -> database.tables)
    data += ("report" -> report)
    data += ("sequences" -> database.sequences)
    data += ("database" -> database)

    for (page <- report.pages) {
      if (page.iterable) {
        for (module <- report.modules)
          renderModule(module, page.name, data)
      } else {
        data.remove("module")
        render(data, page.name)
      }
    }
  }

  def renderModule(module: Module, template: String, data: collection.mutable.HashMap[String, Any]) {
    data.put("module", module)
    if (!module.tables.isEmpty) {
      logger.info(s"rendering module $module...")
      render(data, template, module.path)
    }

    for (module <- module.children) renderModule(module, template, data)
  }

  def genImages(): Unit = {
    if (report.images.isEmpty) return

    if (!Reporter.DotReady) {
      logger.warn("""
Cannot find dot command. images generation skipped.
dot is a tool to generate nice-looking diagrams with a minimum of effort. It's part of GraphViz.
see http://www.graphviz.org/doc/info/lang.html and http://www.linuxdevcenter.com/pub/a/linux/2004/05/06/graphviz_dot.html""")
      return
    }

    val data = new collection.mutable.HashMap[String, Any]()
    data += ("database" -> database)
    data += ("report" -> report)

    for (image <- report.images) {
      data.put("image", image)
      genImage(data, image.name)
    }
  }

  private def render(data: collection.mutable.HashMap[String, Any], template: String, result: String = "") {
    val wikiResult = if (isEmpty(result)) template else result;
    val file = new File(dir + wikiResult + report.extension)
    file.getParentFile().mkdirs()
    val fw = stringWriter(file)
    val freemarkerTemplate = cfg.getTemplate(report.template + "/" + template + ".ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
  }

  private def genImage(data: Any, result: String) {
    val javafile = new File(dir + "images" + / + result + ".java")
    javafile.getParentFile().mkdirs()
    val fw = stringWriter(javafile)
    val freemarkerTemplate = cfg.getTemplate("class.ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
    java2png(javafile)
    javafile.deleteOnExit()
  }

  private def java2png(javafile: File) {
    val javaPath = javafile.getAbsolutePath()
    val filename = substringBefore(substringAfterLast(javaPath, /), ".java")
    val dotPath = substringBeforeLast(javaPath, /) + / + filename + ".dot"
    val pngPath = substringBeforeLast(javaPath, /) + / + filename + ".png"
    val dotfile = forName(dotPath)
    UmlGraph.main(Array("-package", "-outputencoding", "utf-8", "-output", dotPath, javaPath));
    if (dotfile.exists) {
      Runtime.getRuntime().exec("dot -Tpng -o" + pngPath + " " + dotPath);
      dotfile.deleteOnExit()
    }
  }

  def close(): Unit = {
    DataSourceUtils.close(ds)
  }
}
