/*
 * Copyright 2022 HM Revenue & Customs
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
