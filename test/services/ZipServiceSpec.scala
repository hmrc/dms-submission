/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import better.files.File
import models.Pdf
import models.submission.SubmissionMetadata
import org.scalatest.EitherValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

import java.time.{Clock, LocalDateTime, ZoneOffset}
import scala.io.Source
import scala.xml.{Utility, XML}

class ZipServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with EitherValues {

  private val clock = Clock.fixed(LocalDateTime.of(2022, 3, 2, 12, 30, 45, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[Clock].toInstance(clock)
    )
    .configure(
      "metadata.format" -> "format",
      "metadata.mimeType" -> "mimeType",
      "metadata.target" -> "target"
    )
    .build()

  private val service = app.injector.instanceOf[ZipService]

  private val pdfBytes: Array[Byte] = {
    val stream = getClass.getResourceAsStream("/test.pdf")
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }

  "createZip" - {

    "must create a zip with the right contents in the work dir" in {
      val correlationId = "correlationId"
      val metadata = SubmissionMetadata(
        store = true,
        source = "source",
        timeOfReceipt = LocalDateTime.of(2022, 2, 1, 12, 30, 45, 0).toInstant(ZoneOffset.UTC),
        formId = "formId",
        customerId = "customerId",
        submissionMark = "submissionMark",
        casKey = "casKey",
        classificationType = "classificationType",
        businessArea = "businessArea"
      )
      val workDir = File.newTemporaryDirectory().deleteOnExit()
      val pdfFile = File.newTemporaryFile().writeByteArray(pdfBytes)
      val pdf = Pdf(pdfFile, 4)

      val zip = service.createZip(workDir, pdf, metadata, correlationId).futureValue.value

      val tmpDir = File.newTemporaryDirectory().deleteOnExit()
      zip.unzipTo(tmpDir)

      zip.parent mustEqual workDir
      val unzippedPdf = tmpDir / "correlationId-20220201-iform.pdf"
      unzippedPdf.isSameContentAs(pdfFile) mustBe true

      val unzippedMetadata = tmpDir / "correlationId-20220201-metadata.xml"
      val expectedMetadata = Utility.trim(XML.load(Source.fromResource("metadata.xml").bufferedReader()))
      XML.loadString(unzippedMetadata.contentAsString) mustEqual expectedMetadata
    }
  }
}
