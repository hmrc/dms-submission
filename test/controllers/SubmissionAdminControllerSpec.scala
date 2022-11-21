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

package controllers

import models.SubmissionSummary
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SubmissionItemRepository

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class SubmissionAdminControllerSpec
  extends AnyFreeSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  private val mockSubmissionItemRepository = mock[SubmissionItemRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(mockSubmissionItemRepository)
    super.beforeEach()
  }

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[Clock].toInstance(clock),
      bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository)
    )
    .build()

  private val item = SubmissionItem(
    id = "id",
    owner = "owner",
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

  "list" - {

    "must return a list of submissions for the owner" in {

      when(mockSubmissionItemRepository.list(any())).thenReturn(Future.successful(Seq(item)))

      val request = FakeRequest(routes.SubmissionAdminController.list("owner"))

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResult = List(SubmissionSummary("id", SubmissionItemStatus.Submitted, None, clock.instant()))
      contentAsJson(result) mustEqual Json.toJson(expectedResult)
    }
  }
}
