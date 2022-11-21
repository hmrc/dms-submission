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
import config.FileSystemExecutionContext
import models.Pdf
import org.apache.pdfbox.pdmodel.PDDocument

import javax.inject.{Singleton, Inject}
import scala.concurrent.Future

@Singleton
class PdfService @Inject() ()(implicit ec: FileSystemExecutionContext) {

  def getPdf(file: File): Future[Pdf] = Future {
    val document = PDDocument.load(file.path.toFile)
    try {
      Pdf(file, document.getNumberOfPages)
    } finally {
      document.close()
    }
  }
}
