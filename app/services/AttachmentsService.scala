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

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Keep, Source}
import akka.util.ByteString
import better.files.File
import cats.data.{EitherNec, EitherT, NonEmptyChain}
import cats.implicits._
import models.Done
import models.submission.Attachment
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient

import java.security.MessageDigest
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AttachmentsService @Inject() (
                                     objectStoreClient: PlayObjectStoreClient
                                   )(implicit ec: ExecutionContext, mat: Materializer) {

  private val digestAlg: MessageDigest = MessageDigest.getInstance("MD5")
  private val encoder: Base64.Encoder = Base64.getEncoder

  private val sizeLimit: Long = 5000000L
  private val acceptedContentTypes: Set[String] = Set("application/pdf", "image/jpeg")

  def downloadAttachments(workDir: File, attachments: Seq[Attachment])(implicit hc: HeaderCarrier): Future[EitherNec[String, Done]] =
    attachments.parTraverse { attachment =>
      EitherT(downloadAttachment(workDir, attachment))
    }.as(Done).value

  private def downloadAttachment(workDir: File, attachment: Attachment)(implicit hc: HeaderCarrier): Future[EitherNec[String, Done]] = {
    for {
      o    <- getObject(attachment)
      _    <- checkSize(o)
      _    <- checkContentType(o)
      file <- downloadFile(workDir, o)
      _    <- checkDigest(attachment, file)
    } yield Done
  }.value

  private def getObject(attachment: Attachment)(implicit hc: HeaderCarrier): EitherT[Future, NonEmptyChain[String], OsObject] =
    EitherT {
      objectStoreClient.getObject[Source[ByteString, NotUsed]](Path.File(attachment.location), attachment.owner).map { a =>
        a.map(_.rightNec[String])
          .getOrElse(s"${attachment.location}: not found".leftNec[OsObject])
      }.recover {
        case e: UpstreamErrorResponse if e.statusCode == 403 =>
          s"${attachment.location}: unauthorised response from object-store".leftNec
      }
    }

  private def downloadFile(workDir: File, o: OsObject): EitherT[Future, NonEmptyChain[String], File] = {
    val file = workDir / o.location.fileName
    EitherT.liftF(o.content.toMat(FileIO.toPath(file.path))(Keep.right).run())
      .map(_ => file)
  }

  private def checkSize(o: OsObject): EitherT[Future, NonEmptyChain[String], Done] =
    if (o.metadata.contentLength > sizeLimit) {
      EitherT.leftT(NonEmptyChain.one(s"${o.location.asUri}: must be less than 5mb, size from object-store: ${o.metadata.contentLength}b"))
    } else EitherT.pure(Done)

  private def checkContentType(o: OsObject): EitherT[Future, NonEmptyChain[String], Done] =
    if (!acceptedContentTypes.contains(o.metadata.contentType)) {
      EitherT.leftT(NonEmptyChain.one(s"${o.location.asUri}: must be either a PDF or JPG, content type from object-store: ${o.metadata.contentType}"))
    } else EitherT.pure(Done)

  private def checkDigest(attachment: Attachment, file: File): EitherT[Future, NonEmptyChain[String], Done] =
    if (fileDigest(file) != attachment.contentMd5) {
      EitherT.leftT(NonEmptyChain.one(s"${attachment.location}: digest does not match"))
    } else EitherT.pure(Done)

  private def fileDigest(file: File): String =
    encoder.encodeToString(digestAlg.digest(file.byteArray))
}
