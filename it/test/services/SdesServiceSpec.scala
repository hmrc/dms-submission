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

import connectors.SdesConnector
import models.Done
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest}
import models.submission.{ObjectSummary, QueryResult, SubmissionItem, SubmissionItemStatus}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.SubmissionItemRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.objectstore.client.Path
import util.MutableClock

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import java.util.UUID
import scala.concurrent.Future

class SdesServiceSpec extends AnyFreeSpec with Matchers
  with DefaultPlayMongoRepositorySupport[SubmissionItem]
  with ScalaFutures with IntegrationPatience
  with MockitoSugar with OptionValues with BeforeAndAfterEach {

  private val clock: Clock = MutableClock(Instant.now())
  private val mockSdesConnector: SdesConnector = mock[SdesConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSdesConnector)
  }

  private lazy val app = GuiceApplicationBuilder()
    .configure(
      "service.sdes.information-type" -> "information-type",
      "service.sdes.recipient-or-sender" -> "recipient-or-sender",
      "services.sdes.object-store-location-prefix" -> "http://prefix/",
      "lock-ttl" -> 30
    )
    .overrides(
      bind[SdesConnector].toInstance(mockSdesConnector),
      bind[MongoComponent].toInstance(mongoComponent),
      bind[Clock].toInstance(clock)
    )
    .build()

  override protected lazy val repository: SubmissionItemRepository =
    app.injector.instanceOf[SubmissionItemRepository]

  private val service = app.injector.instanceOf[SdesService]

  private def randomItem = SubmissionItem(
    id = UUID.randomUUID().toString,
    owner = UUID.randomUUID().toString,
    callbackUrl = "callbackUrl",
    status = SubmissionItemStatus.Submitted,
    objectSummary = ObjectSummary(
      location = "location",
      contentLength = 1337,
      contentMd5 = "hash",
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    ),
    failureReason = None,
    created = clock.instant().minus(1, ChronoUnit.DAYS),
    lastUpdated = clock.instant().minus(1, ChronoUnit.DAYS),
    sdesCorrelationId = UUID.randomUUID().toString,
  )

  "notifyOldestSubmittedItem" - {

    "must notify SDES for the latest submitted item, update the status to Forwarded, and return Found when there is an item to process" in {

      val item = randomItem

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      repository.insert(item).futureValue
      service.notifyOldestSubmittedItem().futureValue mustBe QueryResult.Found

      val expectedRequest = FileNotifyRequest(
        "information-type",
        FileMetadata(
          recipientOrSender = "dms-submission",
          name = s"${item.sdesCorrelationId}.zip",
          location = s"http://prefix/${Path.File("location").asUri}",
          checksum = FileChecksum("md5", value = "85ab21"),
          size = 1337,
          properties = List.empty
        ),
        FileAudit(item.sdesCorrelationId)
      )

      verify(mockSdesConnector, times(1)).notify(eqTo(expectedRequest))(any())

      val updatedItem = repository.get(item.sdesCorrelationId).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Forwarded
    }

    "must return NotFound when there is no item to process" in {
      service.notifyOldestSubmittedItem().futureValue mustBe QueryResult.NotFound
      verify(mockSdesConnector, never()).notify(any())(any())
    }

    "must return Found when the call to SDES fails" in {

      val item = randomItem

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.failed(new RuntimeException()))

      repository.insert(item).futureValue
      service.notifyOldestSubmittedItem().futureValue mustBe QueryResult.Found

      val updatedItem = repository.get(item.sdesCorrelationId).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Submitted
    }
  }

  "notifySubmittedItems" - {

    "must attempt to notify items until there are no more waiting" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem

      when(mockSdesConnector.notify(any())(any())).thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifySubmittedItems().futureValue

      verify(mockSdesConnector, times(3)).notify(any())(any())

      repository.get(item1.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
      repository.get(item2.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
      repository.get(item3.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
    }

    "must continue to notify other items when there is a failure" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem

      when(mockSdesConnector.notify(any())(any()))
        .thenReturn(Future.successful(Done))
        .thenReturn(Future.failed(new RuntimeException()))
        .thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifySubmittedItems().futureValue

      verify(mockSdesConnector, times(3)).notify(any())(any())

      repository.get(item1.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
      repository.get(item2.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Submitted
      repository.get(item3.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Forwarded
    }
  }
}
