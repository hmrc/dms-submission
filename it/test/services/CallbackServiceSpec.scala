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

import connectors.CallbackConnector
import models.Done
import models.submission.{ObjectSummary, QueryResult, SubmissionItem, SubmissionItemStatus}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
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
import util.MutableClock

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import java.util.UUID
import scala.concurrent.Future

class CallbackServiceSpec extends AnyFreeSpec with Matchers
  with DefaultPlayMongoRepositorySupport[SubmissionItem]
  with ScalaFutures with IntegrationPatience
  with MockitoSugar with OptionValues with BeforeAndAfterEach {

  private val clock: Clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS))
  private val mockCallbackConnector: CallbackConnector = mock[CallbackConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockCallbackConnector)
  }

  private lazy val app = GuiceApplicationBuilder()
    .configure(
      "lock-ttl" -> 30
    )
    .overrides(
      bind[CallbackConnector].toInstance(mockCallbackConnector),
      bind[MongoComponent].toInstance(mongoComponent),
      bind[Clock].toInstance(clock)
    )
    .build()

  override protected val repository: SubmissionItemRepository =
    app.injector.instanceOf[SubmissionItemRepository]

  private lazy val service =
    app.injector.instanceOf[CallbackService]

  private def randomItem = SubmissionItem(
    id = UUID.randomUUID().toString,
    owner = UUID.randomUUID().toString,
    callbackUrl = "callbackUrl",
    status = SubmissionItemStatus.Processed,
    objectSummary = ObjectSummary(
      location = "location",
      contentLength = 1337,
      contentMd5 = "hash",
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    ),
    failureType = None,
    failureReason = None,
    created = clock.instant().minus(1, ChronoUnit.DAYS),
    lastUpdated = clock.instant().minus(1, ChronoUnit.DAYS),
    sdesCorrelationId = UUID.randomUUID().toString,
  )

  "notifyOldestProcessedItem" - {

    "must notify the callback for the latest processed item, update the status to Completed, and return Found when there an item to process" in {

      val item = randomItem

      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      repository.insert(item).futureValue
      service.notifyOldestProcessedItem().futureValue mustBe QueryResult.Found

      val expectedItem = item.copy(lastUpdated = clock.instant())

      verify(mockCallbackConnector, times(1)).notify(eqTo(expectedItem))

      val updatedItem = repository.get(item.sdesCorrelationId).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Completed
    }

    "must return NotFound when there is no item to process" in {
      service.notifyOldestProcessedItem().futureValue mustBe QueryResult.NotFound
      verify(mockCallbackConnector, never()).notify(any())
    }

    "must return Found when the callback fails" in {

      val item = randomItem

      when(mockCallbackConnector.notify(any())).thenReturn(Future.failed(new RuntimeException()))

      repository.insert(item).futureValue
      service.notifyOldestProcessedItem().futureValue mustBe QueryResult.Found

      val updatedItem = repository.get(item.sdesCorrelationId).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Processed
    }
  }

  "notifyProcessedItems" - {

    "must attempt to notify items until there are no more waiting" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem

      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifyProcessedItems().futureValue

      verify(mockCallbackConnector, times(3)).notify(any())

      repository.get(item1.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
      repository.get(item2.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
      repository.get(item3.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
    }

    "must continue to notify other items when there is a failure" in {

      val item1 = randomItem
      val item2 = randomItem
      val item3 = randomItem

      when(mockCallbackConnector.notify(any()))
        .thenReturn(Future.successful(Done))
        .thenReturn(Future.failed(new RuntimeException()))
        .thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifyProcessedItems().futureValue

      verify(mockCallbackConnector, times(3)).notify(any())

      repository.get(item1.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
      repository.get(item2.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Processed
      repository.get(item3.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
    }
  }

  "notifyOldestFailedItem" - {

    "must notify the callback for the latest failed item, update the status to Completed, and return Found when there an item to process" in {

      val item = randomItem.copy(
        status = SubmissionItemStatus.Failed,
        failureReason = Some("reason"),
        failureType = Some(SubmissionItem.FailureType.Sdes)
      )

      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      repository.insert(item).futureValue
      service.notifyOldestFailedItem().futureValue mustBe QueryResult.Found

      val expectedItem = item.copy(lastUpdated = clock.instant())

      verify(mockCallbackConnector, times(1)).notify(eqTo(expectedItem))

      val updatedItem = repository.get(item.sdesCorrelationId).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Completed
      updatedItem.failureReason.value mustEqual "reason"
      updatedItem.failureType.value mustEqual SubmissionItem.FailureType.Sdes
    }

    "must return NotFound when there is no item to process" in {
      service.notifyOldestFailedItem().futureValue mustBe QueryResult.NotFound
      verify(mockCallbackConnector, never()).notify(any())
    }

    "must return Found when the callback fails" in {

      val item = randomItem.copy(status = SubmissionItemStatus.Failed)

      when(mockCallbackConnector.notify(any())).thenReturn(Future.failed(new RuntimeException()))

      repository.insert(item).futureValue
      service.notifyOldestFailedItem().futureValue mustBe QueryResult.Found

      val updatedItem = repository.get(item.sdesCorrelationId).futureValue.value
      updatedItem.status mustEqual SubmissionItemStatus.Failed
    }
  }

  "notifyFailedItems" - {

    "must attempt to notify items until there are no more waiting" in {

      val item1 = randomItem.copy(status = SubmissionItemStatus.Failed)
      val item2 = randomItem.copy(status = SubmissionItemStatus.Failed)
      val item3 = randomItem.copy(status = SubmissionItemStatus.Failed)

      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifyFailedItems().futureValue

      verify(mockCallbackConnector, times(3)).notify(any())

      repository.get(item1.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
      repository.get(item2.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
      repository.get(item3.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
    }

    "must continue to notify other items when there is a failure" in {

      val item1 = randomItem.copy(status = SubmissionItemStatus.Failed)
      val item2 = randomItem.copy(status = SubmissionItemStatus.Failed)
      val item3 = randomItem.copy(status = SubmissionItemStatus.Failed)

      when(mockCallbackConnector.notify(any()))
        .thenReturn(Future.successful(Done))
        .thenReturn(Future.failed(new RuntimeException()))
        .thenReturn(Future.successful(Done))

      repository.insert(item1).futureValue
      repository.insert(item2).futureValue
      repository.insert(item3).futureValue

      service.notifyFailedItems().futureValue

      verify(mockCallbackConnector, times(3)).notify(any())

      repository.get(item1.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
      repository.get(item2.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Failed
      repository.get(item3.sdesCorrelationId).futureValue.value.status mustEqual SubmissionItemStatus.Completed
    }
  }
}
