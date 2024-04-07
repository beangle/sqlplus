/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.sqlplus.report

import freemarker.cache.{ClassTemplateLoader, FileTemplateLoader, MultiTemplateLoader}
import freemarker.template.Configuration
import net.sourceforge.plantuml.{OptionFlags, Run}
import org.beangle.commons.io.Files.{/, stringWriter}
import org.beangle.commons.lang.Strings.isEmpty
import org.beangle.commons.logging.Logging
import org.beangle.jdbc.meta.Table
import org.beangle.sqlplus.report.model.{Group, Module, Report}
import org.beangle.template.freemarker.BeangleObjectWrapper

import java.io.File
import java.util.Locale

object Reporter extends Logging {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      logger.info("Usage: Reporter /path/to/your/report.xml [target] -debug")
      return
    }

    val reportxml = new File(args(0)).getAbsoluteFile
    val target =
      if (args.length > 1 && args(1) != "-debug") args(1)
      else reportxml.getParent

    logger.info(s"All wiki and images will be generated in $target")
    val reporter = new Reporter(Report(reportxml), target)
    reporter.filterTables()

    val debug = args.contains("-debug")
    if (debug) {
      logger.info("Debug Mode:Type gen to generate report again,or q or exit to quit!")
      var command = "gen"
      while {
        if (command == "gen") gen(reporter)
        print("gen/exit:")
        command = Console.in.readLine()
        command != "exit" && command != "q"
      } do ()
    } else {
      gen(reporter)
    }
  }

  def gen(reporter: Reporter): Unit = {
    try {
      reporter.genWiki()
      reporter.genImages()
      logger.info("report generate complete.")
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
}

class Reporter(val report: Report, val dir: String) extends Logging {
  val cfg = new Configuration(Configuration.VERSION_2_3_24)
  cfg.setEncoding(Locale.getDefault, "UTF-8")
  val overrideDir = new File(dir + ".." + / + "template")
  if (overrideDir.exists) {
    logger.info(s"Load override template from ${overrideDir.getAbsolutePath}")
    cfg.setTemplateLoader(new MultiTemplateLoader(Array(new FileTemplateLoader(overrideDir), new ClassTemplateLoader(getClass, "/template"))))
  } else
    cfg.setTemplateLoader(new ClassTemplateLoader(getClass, "/template"))
  cfg.setObjectWrapper(new BeangleObjectWrapper)

  def filterTables(): Unit = {
    for (s <- report.schemas; m <- s.modules) {
      val schema = report.database.getSchema(s.name).get
      val schemaTables = new collection.mutable.HashSet[Table]
      schema.tables.values foreach { t =>
        m.name match {
          case None => schemaTables += t
          case Some(pkg) =>
            t.module foreach { mn =>
              if (mn.startsWith(pkg)) schemaTables += t
            }
        }
      }
      val allTables = schemaTables.toList
      for (group <- m.groups) group.filter(schemaTables)
      for (image <- m.images) image.select(report.database)
      m.tables = allTables
    }
    report.build()
  }

  def genWiki(): Unit = {
    if (report.template == "singlehtml") {
      val data = new collection.mutable.HashMap[String, Any]
      data += ("engine" -> report.database.engine)
      data += ("tablesMap" -> report.allTables)
      data += ("report" -> report)
      data += ("sequences" -> report.allSequences)
      render(data, "index", dir)
    } else {
      for (rs <- report.schemas; s <- rs.modules) {
        logger.info(s"rendering module ${s.id}")
        val schema = report.database.getSchema(rs.name).get
        val data = new collection.mutable.HashMap[String, Any]
        data += ("engine" -> report.database.engine)
        data += ("tablesMap" -> schema.tables)
        data += ("report" -> report)
        data += ("sequences" -> schema.sequences)
        data += ("module" -> s)

        for (page <- report.pages) {
          if (page.iterable) {
            s.groups foreach { group =>
              renderGroup(s, group, page.name, data)
            }
          } else {
            data.remove("group")
            render(data, page.name, moduleBaseDir(s))
          }
        }
      }
    }
  }

  def renderGroup(rs: Module, group: Group, template: String, data: collection.mutable.HashMap[String, Any]): Unit = {
    data.put("group", group)
    if (group.tables.nonEmpty) {
      logger.info(s"rendering $group")
      render(data, template, moduleBaseDir(rs), group.fullName)
    }
    for (g <- group.children) renderGroup(rs, g, template, data)
  }

  def genImages(): Unit = {
    for (rs <- report.schemas; s <- rs.modules) {
      if (s.images.nonEmpty) {
        val data = new collection.mutable.HashMap[String, Any]()
        data += ("module" -> s)
        data += ("report" -> report)

        val imageBase = (if (report.template == "singlehtml") dir else moduleBaseDir(s)) + / + "images"
        s.images foreach { image =>
          data.put("image", image)
          val javafile = new File(imageBase + / + image.name + ".java")
          javafile.getParentFile.mkdirs()
          val fw = stringWriter(javafile)
          val freemarkerTemplate = cfg.getTemplate("image.ftl")
          freemarkerTemplate.process(data, fw)
          fw.close()
        }
        OptionFlags.getInstance().setSystemExit(false)
        Run.main(Array(imageBase, "-charset", "UTF-8"))
        if (!report.reserveImageUmlScript) {
          s.images foreach { image =>
            new File(imageBase + / + image.name + ".java").delete()
          }
        }
      }
    }
  }

  private def moduleBaseDir(module: Module): String = {
    val packageName = module.name.map(/ + _).getOrElse("")
    dir + / + module.schema.name + packageName
  }

  private def render(data: collection.mutable.HashMap[String, Any], template: String, base: String, result: String = ""): Unit = {
    val wikiResult = if (isEmpty(result)) template else result
    val file = new File(base + / + wikiResult + report.extension)
    file.getParentFile.mkdirs()
    val fw = stringWriter(file)
    val freemarkerTemplate = cfg.getTemplate(report.template + "/" + template + ".ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
  }

}
