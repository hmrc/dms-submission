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

package repositories

import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus}
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class SubmissionItemRepositorySpec extends AnyFreeSpec
  with Matchers with OptionValues
  with DefaultPlayMongoRepositorySupport[SubmissionItem]
  with ScalaFutures with IntegrationPatience {

  private val clock: Clock = Clock.fixed(Instant.now.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)

  override protected def repository = new SubmissionItemRepository(
    mongoComponent = mongoComponent,
    clock = clock
  )

  private val item = SubmissionItem(
    correlationId = "correlationId",
    callbackUrl = "callbackUrl",
    status = SubmissionItemStatus.Submitted,
    objectSummary = ObjectSummary(
      location = "location",
      contentLength = 1337,
      contentMd5 = "hash",
      lastModified = clock.instant().minus(2, ChronoUnit.DAYS)
    ),
    failureReason = None,
    lastUpdated = clock.instant().minus(1, ChronoUnit.DAYS)
  )

  "insert" - {

    "must insert a new record and return successfully" in {
      val expected = item.copy(lastUpdated = clock.instant())
      repository.get("correlationId").futureValue mustBe None
      repository.insert(item).futureValue
      repository.get("correlationId").futureValue.value mustEqual expected
    }

    "must fail to insert an item for an existing correlationId" in {
      repository.insert(item).futureValue
      repository.insert(item).failed.futureValue
    }
  }

  "update" - {

    "must update a record if it exists and return it" in {
      val expected = item.copy(status = SubmissionItemStatus.Ready, failureReason = Some("failure"), lastUpdated = clock.instant())
      repository.insert(item).futureValue
      repository.update("correlationId", SubmissionItemStatus.Ready, failureReason = Some("failure")).futureValue mustEqual expected
      repository.get("correlationId").futureValue.value mustEqual expected
    }

    "must fail if no record exists" in {
      repository.insert(item).futureValue
      repository.update("foobar", SubmissionItemStatus.Ready, failureReason = Some("failure")).failed.futureValue mustEqual SubmissionItemRepository.NothingToUpdateException
    }

    "must remove failure reason if it's passed as `None`" in {
      val newItem = item.copy(failureReason = Some("failure"))
      val expected = item.copy(lastUpdated = clock.instant())
      repository.insert(newItem).futureValue
      repository.update("correlationId", SubmissionItemStatus.Submitted, failureReason = None).futureValue
      repository.get("correlationId").futureValue.value mustEqual expected
    }

    "must succeed when there is no failure reason to remove" in {
      val expected = item.copy(lastUpdated = clock.instant())
      repository.insert(item).futureValue
      repository.update("correlationId", SubmissionItemStatus.Submitted, failureReason = None).futureValue
      repository.get("correlationId").futureValue.value mustEqual expected
    }
  }

  "get" - {

    "must return an item that matches the correlationId" in {
      repository.insert(item).futureValue
      repository.get("correlationId").futureValue.value mustEqual item.copy(lastUpdated = clock.instant())
    }

    "must return `None` when there is no item matching the correlationId" in {
      repository.insert(item).futureValue
      repository.get("foobar").futureValue mustNot be (defined)
    }
  }

  "remove" - {

    "must remove an item if it matches the correlation id" in {
      repository.insert(item).futureValue
      repository.insert(item.copy(correlationId = "foobar")).futureValue
      repository.remove("correlationId").futureValue
      repository.get("correlationId").futureValue mustNot be (defined)
      repository.get("foobar").futureValue mustBe defined
    }

    "must fail silently when trying to remove something that doesn't exist" in {
      repository.insert(item.copy(correlationId = "foobar")).futureValue
      repository.remove("correlationId").futureValue
      repository.get("correlationId").futureValue mustNot be (defined)
      repository.get("foobar").futureValue mustBe defined
    }
  }
}
