package services

import better.files.File
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder

class FileServiceSpec extends AnyFreeSpec with Matchers {

  private val app = new GuiceApplicationBuilder()
    .build()

  private val service = app.injector.instanceOf[FileService]

  "workDir" - {
    "must create a directory within play's temporary directory" in {
      val workDir = service.workDir().deleteOnExit()
      workDir.path.toString must startWith (app.configuration.get[String]("play.temporaryFile.dir"))
    }
  }

  "createZip" - {

    "must create a zip with the right contents in the work dir" in {
      val workDir = File.newTemporaryDirectory().deleteOnExit()
      val pdf = File.newTemporaryFile().writeText("Hello, World!")

      val zip = service.createZip(workDir, pdf)

      val tmpDir = File.newTemporaryDirectory().deleteOnExit()
      zip.unzipTo(tmpDir)

      zip.parent mustEqual workDir
      val unzippedPdf = tmpDir / "iform.pdf"
      unzippedPdf.contentAsString mustEqual "Hello, World!"
    }
  }
}
