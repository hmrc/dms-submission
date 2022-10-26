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

import connectors.SdesConnector
import models.Done
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.Instant
import scala.concurrent.Future

class SdesServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures with OptionValues with MockitoSugar with BeforeAndAfterEach {

  private val mockSdesConnector = mock[SdesConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSdesConnector)
  }

  private val app = GuiceApplicationBuilder()
    .configure(
      "service.sdes.information-type" -> "information-type",
      "service.sdes.recipient-or-sender" -> "recipient-or-sender"
    )
    .overrides(
      bind[SdesConnector].toInstance(mockSdesConnector)
    )
    .build()

  private val hc: HeaderCarrier = HeaderCarrier()

  private val service = app.injector.instanceOf[SdesService]

  "notify" - {

    "must call the connector with the right request when given an object summary and a correlation id" in {

      val objectSummary = ObjectSummaryWithMd5(
        Path.File("file"),
        contentLength = 2000,
        contentMd5 = Md5Hash("hash"),
        lastModified = Instant.now
      )
      val correlationId = "uuid"

      val request = FileNotifyRequest(
        "information-type",
        FileMetadata(
          "recipient-or-sender",
          "file",
          Path.File("file").asUri,
          FileChecksum("md5", value = "hash"),
          2000,
          List()
        ),
        FileAudit("uuid")
      )

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      service.notify(objectSummary, correlationId)(hc).futureValue

      verify(mockSdesConnector, times(1)).notify(request)(hc)
    }

    "must fail when the connector call fails" in {

      val objectSummary = ObjectSummaryWithMd5(
        Path.File("file"),
        contentLength = 2000,
        contentMd5 = Md5Hash("hash"),
        lastModified = Instant.now
      )
      val correlationId = "uuid"

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.failed(new RuntimeException()))

      service.notify(objectSummary, correlationId)(hc).failed.futureValue
    }
  }
}
