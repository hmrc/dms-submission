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
import akka.stream.scaladsl.Source
import akka.util.ByteString
import better.files.File
import models.submission.Attachment
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.http.Status.FORBIDDEN
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, Object, ObjectMetadata, Path}

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import scala.concurrent.Future
import scala.util.Random

class AttachmentsServiceSpec extends AnyFreeSpec with Matchers with OptionValues
  with EitherValues with ScalaFutures with IntegrationPatience with MockitoSugar
  with BeforeAndAfterEach {

  private val mockObjectStoreClient = mock[PlayObjectStoreClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockObjectStoreClient)
  }

  private lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient)
    )
    .build()

  private lazy val service: AttachmentsService = app.injector.instanceOf[AttachmentsService]

  private val lastModified: Instant = Instant.now()

  private val contentLength = 64

  private val bytes: Array[Byte] =
    Random.nextBytes(contentLength)

  private val contentMd5: String =
    Base64.getEncoder.encodeToString {
      MessageDigest.getInstance("MD5").digest(bytes)
    }

  private val owner: String = "some-service"

  private val attachment = Attachment(
    location = "some/file.pdf",
    contentMd5 = contentMd5,
    owner = owner
  )

  private val retrievedObject = Object(
    location = Path.File("some/file.pdf"),
    content = Source.single(ByteString(bytes)),
    metadata = ObjectMetadata(
      contentType = "application/pdf",
      contentLength = contentLength,
      contentMd5 = Md5Hash(contentMd5),
      lastModified = lastModified,
      userMetadata = Map.empty
    )
  )

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "downloadAttachments" - {

    "must download attachments from object store to the working directory" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(retrievedObject)))

      service.downloadAttachments(workDir, Seq(attachment)).futureValue.value

      (workDir / "file.pdf").exists() mustBe true
      (workDir / "file.pdf").byteArray mustEqual bytes

      verify(mockObjectStoreClient).getObject[Source[ByteString, NotUsed]](eqTo(Path.File(attachment.location)), eqTo(owner))(any(), any())
    }

    "must return an error if the file doesn't exist in object-store" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      val errors = service.downloadAttachments(workDir, Seq(attachment)).futureValue.left.value

      errors.toChain.toList must contain only "some/file.pdf: not found"
    }

    "must return an error if the file doesn't match the checksum" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      val attachmentWithBadMd5 = attachment.copy(contentMd5 = "asdfasdf")

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(retrievedObject)))

      val errors = service.downloadAttachments(workDir, Seq(attachmentWithBadMd5)).futureValue.left.value

      errors.toChain.toList must contain only s"some/file.pdf: digest does not match"
    }

    "must return an error if the reported content length of the file is greater than 5mb" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      val largeRetrievedObject = retrievedObject.copy(metadata = retrievedObject.metadata.copy(contentLength = 5000001L))

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(largeRetrievedObject)))

      val errors = service.downloadAttachments(workDir, Seq(attachment)).futureValue.left.value

      errors.toChain.toList must contain only s"some/file.pdf: must be less than 5mb, size from object-store: 5000001b"
    }

    "must return an error if the file is not a PDF or JPG" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      val nonPdfOrJpgFile = retrievedObject.copy(metadata = retrievedObject.metadata.copy(contentType = "text/plain"))

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(nonPdfOrJpgFile)))

      val errors = service.downloadAttachments(workDir, Seq(attachment)).futureValue.left.value

      errors.toChain.toList must contain only s"some/file.pdf: must be either a PDF or JPG, content type from object-store: text/plain"
    }

    "must return errors from all files" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      val nonPdfOrJpgAttachment = attachment.copy(location = "some/text.txt")
      val nonPdfOrJpgFile = retrievedObject.copy(location = Path.File("some/text.txt"), metadata = retrievedObject.metadata.copy(contentType = "text/plain"))

      val largeRetrievedObjectAttachment = attachment.copy()
      val largeRetrievedObject = retrievedObject.copy(metadata = retrievedObject.metadata.copy(contentLength = 5000001L))

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(Some(nonPdfOrJpgFile)),
          Future.successful(Some(largeRetrievedObject))
        )

      val errors = service.downloadAttachments(workDir, Seq(nonPdfOrJpgAttachment, largeRetrievedObjectAttachment)).futureValue.left.value

      errors.toChain.toList must contain allElementsOf Seq(
        "some/text.txt: must be either a PDF or JPG, content type from object-store: text/plain",
        "some/file.pdf: must be less than 5mb, size from object-store: 5000001b"
      )
    }

    "must return an error when any call to object-store is unauthorised" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("error", FORBIDDEN)))

      val errors = service.downloadAttachments(workDir, Seq(attachment)).futureValue.left.value

      errors.toChain.toList must contain only "some/file.pdf: unauthorised response from object-store"
    }

    "must fail when any calls to object-store fail" in {

      val workDir = File.newTemporaryDirectory()
        .deleteOnExit()

      when(mockObjectStoreClient.getObject[Source[ByteString, NotUsed]](any(), any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException()))

      service.downloadAttachments(workDir, Seq(attachment)).failed.futureValue
    }
  }
}
