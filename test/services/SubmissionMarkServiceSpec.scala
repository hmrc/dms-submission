package services

import better.files.File
import models.submission.Attachment
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

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
      Attachment(location = "dir/b.pdf", contentMd5 = "someMd5", owner = "owner"),
      Attachment(location = "dir/a.pdf", contentMd5 = "someMd5", owner = "owner")
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
