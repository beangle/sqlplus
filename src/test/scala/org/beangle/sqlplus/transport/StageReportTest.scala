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

import org.beangle.commons.concurrent.Workers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class StageReportTest extends AnyFunSpec with Matchers {

  describe("StageReport") {
    it("collects concurrent best-effort results") {
      val report = new StageReport("tables", 100)
      Workers.workOn(1 to 100, 4) { i =>
        if i % 10 == 0 then report.failed(s"table_$i", "test failure")
        else report.succeeded(s"table_$i")
      }

      val result = report.result
      result.succeeded shouldBe 90
      result.skipped shouldBe 0
      result.failures should have size 10
      result.isSuccess shouldBe false
    }

    it("distinguishes partial transfers from failures") {
      val report = new StageReport("tables", 1)
      report.partial("PUBLIC.ORDERS", new RuntimeException("connection lost"), 50000, 120000)

      val result = report.result
      result.partials should have size 1
      result.partials.head.transferredRows shouldBe Some(50000)
      result.partials.head.expectedRows shouldBe Some(120000)
      result.failures shouldBe empty
      result.isSuccess shouldBe false
    }

    it("prevents after actions when a required table stage is partial or failed") {
      val success = StageResult("tables", 1, Set("table_a"), 0, Seq.empty, Seq.empty)
      val partial = StageResult(
        "tables", 1, Set.empty, 0, Seq(TransferFailure("table_a", "partial")), Seq.empty)
      val scanFailure = StageResult(
        "scan source", 1, Set.empty, 0, Seq.empty, Seq(TransferFailure("table_a", "failed")))

      Reactor.canExecuteAfterActions(Seq(success)) shouldBe true
      Reactor.canExecuteAfterActions(Seq(success, partial)) shouldBe false
      Reactor.canExecuteAfterActions(Seq(success, scanFailure)) shouldBe false
    }

    it("allows after actions when only a non-table stage failed") {
      val tables = StageResult("tables", 1, Set("table_a"), 0, Seq.empty, Seq.empty)
      val indexFailure = StageResult(
        "indexes", 1, Set.empty, 0, Seq.empty, Seq(TransferFailure("index_a", "failed")))

      // Reactor passes only scan and table results as action prerequisites.
      Reactor.canExecuteAfterActions(Seq(tables)) shouldBe true
      indexFailure.isSuccess shouldBe false
    }

    it("splits a shared table result into task summaries") {
      val shared = StageResult(
        "tables",
        3,
        Set("target.a", "target.b"),
        0,
        Seq(TransferFailure("other.c", "partial")),
        Seq(TransferFailure("target.c", "failed")))

      val task = Reactor.selectResult(
        "copy source -> target", Set("target.a", "target.c"), shared)
      task.total shouldBe 2
      task.succeededItems shouldBe Set("target.a")
      task.partials shouldBe empty
      task.failures.map(_.item) shouldBe Seq("target.c")
    }

    it("hides internal scans from the final summary") {
      val scan = StageResult("scan source -> target", 1, Set("source.a"), 0, Seq.empty, Seq.empty)
      val copy = StageResult("copy source -> target", 1, Set("target.a"), 0, Seq.empty, Seq.empty)

      Reactor.showInSummary(scan) shouldBe false
      Reactor.showInSummary(copy) shouldBe true
    }
  }
}
