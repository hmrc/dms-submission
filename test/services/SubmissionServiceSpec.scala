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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import better.files.File
import models.Done
import models.submission._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.Future

class SubmissionServiceSpec extends AnyFreeSpec with Matchers
  with ScalaFutures with MockitoSugar with OptionValues with BeforeAndAfterEach
  with IntegrationPatience {

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private val mockObjectStoreClient = mock[PlayObjectStoreClient]
  private val mockZipService = mock[ZipService]
  private val mockSubmissionItemRepository = mock[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset[Any](
      mockObjectStoreClient,
      mockZipService,
      mockSubmissionItemRepository
    )
  }

  "submit" - {

    val app = new GuiceApplicationBuilder()
      .overrides(
        bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient),
        bind[ZipService].toInstance(mockZipService),
        bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository),
        bind[Clock].toInstance(clock)
      )
      .build()

    val service = app.injector.instanceOf[SubmissionService]

    val hc: HeaderCarrier = HeaderCarrier()
    val metadata = SubmissionMetadata(
      store = false,
      source = "source",
      timeOfReceipt = LocalDateTime.of(2022, 2, 2, 0, 0, 0).toInstant(ZoneOffset.UTC),
      formId = "formId",
      numberOfPages = 1,
      customerId = "customerId",
      submissionMark = "submissionMark",
      casKey = "casKey",
      classificationType = "classificationType",
      businessArea = "businessArea"
    )
    val request = SubmissionRequest(None, "callbackUrl", metadata)

    val pdf = File.newTemporaryFile()
      .deleteOnExit()
      .write("Hello, World!")
    val zip = File.newTemporaryFile()
      .deleteOnExit()
      .write("Some bytes")

    val objectSummaryWithMd5 = ObjectSummaryWithMd5(
      location = Path.File("file"),
      contentLength = 1337L,
      contentMd5 = Md5Hash("hash"),
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    )
    val item = SubmissionItem(
      id = "id",
      owner = "test-service",
      callbackUrl = "callbackUrl",
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = "file",
        contentLength = 1337L,
        contentMd5 = "hash",
        lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
      ),
      failureReason = None,
      created = clock.instant(),
      lastUpdated = clock.instant(),
      sdesCorrelationId = "sdesCorrelationId"
    )

    "must create a zip file of the contents of the request along with a metadata xml for routing, upload to object-store, store in mongo" in {
      val itemCaptor: ArgumentCaptor[SubmissionItem] = ArgumentCaptor.forClass(classOf[SubmissionItem])
      val fileCaptor: ArgumentCaptor[Path.File] = ArgumentCaptor.forClass(classOf[Path.File])

      when(mockZipService.createZip(any(), eqTo(pdf), eqTo(request.metadata), any())).thenReturn(Future.successful(zip))
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.successful(Done))

      val result = service.submit(request, pdf, "test-service")(hc).futureValue

      verify(mockObjectStoreClient).putObject(fileCaptor.capture(), eqTo(zip.path.toFile), any(), any(), any(), any())(any(), any())
      verify(mockSubmissionItemRepository).insert(itemCaptor.capture())

      val capturedItem = itemCaptor.getValue
      capturedItem mustEqual item.copy(id = capturedItem.id, sdesCorrelationId = capturedItem.sdesCorrelationId)
      result mustEqual capturedItem.id

      val capturedFile = fileCaptor.getValue
      capturedFile mustEqual Path.Directory("sdes/test-service").file(capturedItem.id)
    }

    "must use the id in the request if it exists" in {
      val itemCaptor: ArgumentCaptor[SubmissionItem] = ArgumentCaptor.forClass(classOf[SubmissionItem])
      val id = "id"
      val requestWithCorrelationId = request.copy(id = Some(id))

      when(mockZipService.createZip(any(), eqTo(pdf), eqTo(request.metadata), any())).thenReturn(Future.successful(zip))
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.successful(Done))

      val result = service.submit(requestWithCorrelationId, pdf, "test-service")(hc).futureValue

      verify(mockObjectStoreClient).putObject(eqTo(Path.Directory("sdes/test-service").file(id)), eqTo(zip.path.toFile), any(), any(), any(), any())(any(), any())
      verify(mockSubmissionItemRepository).insert(itemCaptor.capture())

      val capturedItem = itemCaptor.getValue
      capturedItem mustEqual item.copy(id = capturedItem.id, sdesCorrelationId = capturedItem.sdesCorrelationId)
      capturedItem.id mustEqual id
      result mustEqual id
    }

    "must fail when the fail service fails to create a zip file" in {
      when(mockZipService.createZip(any(), eqTo(pdf), eqTo(request.metadata), any())).thenThrow(new RuntimeException())
      service.submit(request, pdf, "test-service")(hc).failed.futureValue
    }

    "must fail when object store fails" in {
      when(mockZipService.createZip(any(), eqTo(pdf), eqTo(request.metadata), any())).thenReturn(Future.successful(zip))
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenThrow(new RuntimeException())
      service.submit(request, pdf, "test-service")(hc).failed.futureValue
    }

    "must fail when the call to mongo fails" in {
      when(mockZipService.createZip(any(), eqTo(pdf), eqTo(request.metadata), any())).thenReturn(Future.successful(zip))
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.failed(new RuntimeException()))
      service.submit(request, pdf, "test-service")(hc).failed.futureValue
    }
  }
}