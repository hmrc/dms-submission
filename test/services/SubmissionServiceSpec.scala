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
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus, SubmissionRequest}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.{Clock, Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class SubmissionServiceSpec extends AnyFreeSpec with Matchers
  with ScalaFutures with MockitoSugar with OptionValues with BeforeAndAfterEach
  with IntegrationPatience {

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private val mockObjectStoreClient = mock[PlayObjectStoreClient]
  private val mockFileService = mock[FileService]
  private val mockSdesService = mock[SdesService]
  private val mockSubmissionItemRepository = mock[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockObjectStoreClient,
      mockFileService,
      mockSdesService,
      mockSubmissionItemRepository
    )
  }

  "submit" - {

    val app = new GuiceApplicationBuilder()
      .overrides(
        bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient),
        bind[FileService].toInstance(mockFileService),
        bind[SdesService].toInstance(mockSdesService),
        bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository),
        bind[Clock].toInstance(clock)
      )
      .build()

    val service = app.injector.instanceOf[SubmissionService]

    val hc: HeaderCarrier = HeaderCarrier()
    val request = SubmissionRequest("callbackUrl")

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
      correlationId = "correlationId",
      callbackUrl = "callbackUrl",
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = "file",
        contentLength = 1337L,
        contentMd5 = "hash",
        lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
      ),
      failureReason = None,
      lastUpdated = clock.instant()
    )

    "must create a zip file of the contents of the request along with a metadata xml for routing, upload to object-store, store in mongo and notify SDES" in {
      val workDir = File.newTemporaryDirectory()
      val itemCaptor: ArgumentCaptor[SubmissionItem] = ArgumentCaptor.forClass(classOf[SubmissionItem])

      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.successful(Done))
      when(mockSdesService.notify(any(), any())(any())).thenReturn(Future.successful(Done))

      service.submit(request, pdf)(hc).futureValue

      verify(mockObjectStoreClient).putObject(eqTo(Path.File("file")), eqTo(zip.path.toFile), any(), any(), any(), any())(any(), any())
      verify(mockSubmissionItemRepository).insert(itemCaptor.capture())
      verify(mockSdesService).notify(eqTo(objectSummaryWithMd5), any())(any())

      itemCaptor.getValue.copy(correlationId = "correlationId") mustEqual item

      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when the file service fails to create a work directory" in {
      when(mockFileService.workDir()).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
    }

    "must fail when the fail service fails to create a zip file" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when object store fails" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when the call to mongo fails" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.failed(new RuntimeException()))
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when sdes fails" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummaryWithMd5))
      when(mockSubmissionItemRepository.insert(any())).thenReturn(Future.successful(Done))
      when(mockSdesService.notify(any(), any())(any())).thenReturn(Future.failed(new RuntimeException()))
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }
  }
}
