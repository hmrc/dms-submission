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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import better.files.File
import models.submission.Attachment

import java.security.MessageDigest
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionMarkService @Inject() ()(implicit mat: Materializer, ec: ExecutionContext) {

  private val base64Encoder = Base64.getEncoder

  def generateSubmissionMark(pdf: File, attachments: Seq[Attachment]): Future[String] = {
    val digest = MessageDigest.getInstance("SHA1")
    attachments.sortBy(_.name).foldLeft(FileIO.fromPath(pdf.path)) { (source, attachment) =>
      source ++ FileIO.fromPath(attachment.file.path)
    }.runForeach(bs => digest.update(bs.asByteBuffer)).map { _ =>
      base64Encoder.encodeToString(digest.digest())
    }
  }
}
