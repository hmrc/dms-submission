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
import models.submission.Attachment
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.objectstore.client.Path

import java.security.MessageDigest
import java.util.Base64

class SubmissionMarkServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with IntegrationPatience {

  private val app: Application = GuiceApplicationBuilder()
    .build()

  private val service: SubmissionMarkService = app.injector.instanceOf[SubmissionMarkService]

  "generateSubmissionMark" - {

    val workDir = File.newTemporaryDirectory()
      .deleteOnExit()

    val pdf = workDir / "file.pdf"
    pdf.write("PDF")

    val file1 = workDir / "b.pdf"
    file1.write("FILE1")

    val file2 = workDir / "a.pdf"
    file2.write("FILE2")

    val attachments = Seq(
      Attachment(location = Path.File("dir/b.pdf"), contentMd5 = "someMd5", owner = "owner"),
      Attachment(location = Path.File("dir/a.pdf"), contentMd5 = "someMd5", owner = "owner")
    )

    val digest = MessageDigest.getInstance("SHA1")
    val expectedHash = digest.digest("PDFFILE2FILE1".getBytes("UTF-8"))
    val expectedResult = Base64.getEncoder.encodeToString(expectedHash)

    "must generate a SHA1 hash of the PDF and its attachments" in {
      service.generateSubmissionMark(workDir, pdf, attachments).futureValue mustEqual expectedResult
    }

    "must generate the same hash regardless of the order of attachments" in {
      service.generateSubmissionMark(workDir, pdf, attachments.reverse).futureValue mustEqual expectedResult
    }
  }
}
