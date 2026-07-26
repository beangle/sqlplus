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

package org.beangle.sqlplus.transport

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

case class TransferFailure(item: String, message: String,
                           transferredRows: Option[Long] = None, expectedRows: Option[Long] = None)

/** Immutable result of one transport stage.
 *
 * `succeededItems` retains object-level detail instead of exposing only an
 * aggregate count.
 */
case class StageResult(stage: String, total: Int, succeededItems: Set[String], skipped: Int,
                       partials: Seq[TransferFailure], failures: Seq[TransferFailure]) {
  def succeeded: Int = succeededItems.size

  def isSuccess: Boolean = partials.isEmpty && failures.isEmpty
}

/** Thread-safe accumulator shared by the workers of one transport stage. */
class StageReport(val stage: String, val total: Int) {
  private val successSet = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  private val skippedCount = new AtomicInteger()
  private val partialQueue = new ConcurrentLinkedQueue[TransferFailure]()
  private val failureQueue = new ConcurrentLinkedQueue[TransferFailure]()

  def succeeded(item: String): Unit = successSet.add(item)

  def skipped(): Unit = skippedCount.incrementAndGet()

  private def errorMsg(e: Throwable): String = {
    Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
  }

  def failed(item: String, e: Throwable): Unit = {
    failureQueue.add(TransferFailure(item, errorMsg(e)))
  }

  def failed(item: String, e: Throwable, transferredRows: Long, expectedRows: Long): Unit = {
    failureQueue.add(TransferFailure(item, errorMsg(e), Some(transferredRows), Some(expectedRows)))
  }

  def partial(item: String, e: Throwable, transferredRows: Long, expectedRows: Long): Unit = {
    partialQueue.add(TransferFailure(item, errorMsg(e), Some(transferredRows), Some(expectedRows)))
  }

  def failed(item: String, message: String): Unit = failureQueue.add(TransferFailure(item, message))

  def result: StageResult =
    StageResult(stage, total, successSet.asScala.toSet, skippedCount.get(),
      partialQueue.iterator().asScala.toSeq, failureQueue.iterator().asScala.toSeq)
}

trait Converter {

  /**
   * 设置目标数据源
   */
  def target: TableStore

  /**
   * 重新开始
   */
  def reset(): Unit

  /**
   * 开始导入
   */
  def start(): StageResult

  def payloadCount: Int

}
