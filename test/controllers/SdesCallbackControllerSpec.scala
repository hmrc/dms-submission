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

import connectors.CallbackConnector
import models.Done
import models.sdes.{NotificationCallback, NotificationType}
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.inject.bind
import repositories.SubmissionItemRepository

import java.time.{Clock, Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class SdesCallbackControllerSpec extends AnyFreeSpec with Matchers with OptionValues with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  private val mockSubmissionItemRepository = mock[SubmissionItemRepository]
  private val mockCallbackConnector = mock[CallbackConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSubmissionItemRepository,
      mockCallbackConnector
    )
  }

  "callback" - {

    val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

    val app = GuiceApplicationBuilder()
      .overrides(
        bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository),
        bind[CallbackConnector].toInstance(mockCallbackConnector)
      )
      .build()

    val requestBody = NotificationCallback(
      notification = NotificationType.FileReady,
      filename = "filename",
      correlationID = "correlationID",
      failureReason = None
    )

    val item = SubmissionItem(
      correlationId = "correlationID",
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

    "must update the status of the submission to Ready, send a callback notification and return OK when the status is updated to Ready" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))
      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Ready, None)
      verify(mockCallbackConnector, times(1)).notify(item)
    }

    "must update the status of the submission to Received, send a callback notification and return OK when the status is updated to Received" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))
      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileReceived)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Received, None)
      verify(mockCallbackConnector, times(1)).notify(item)
    }

    "must update the status of the submission to Processed, send a callback to notification and return OK when the status is updated to Processed" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))
      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileProcessed)))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Processed, None)
      verify(mockCallbackConnector, times(1)).notify(item)
    }

    "must update the status of the submission to Failed, send a callback to notification and return OK when the status is updated to Failed" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.successful(item))
      when(mockCallbackConnector.notify(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody.copy(notification = NotificationType.FileProcessingFailure, failureReason = Some("failure reason"))))

      val response = route(app, request).value

      status(response) mustEqual OK
      verify(mockSubmissionItemRepository, times(1)).get(requestBody.correlationID)
      verify(mockSubmissionItemRepository, times(1)).update(requestBody.correlationID, SubmissionItemStatus.Failed, Some("failure reason"))
      verify(mockCallbackConnector, times(1)).notify(item)
    }

    "must return NOT_FOUND when there is no matching submission" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      val response = route(app, request).value

      status(response) mustEqual NOT_FOUND
    }

    "must return BAD_REQUEST when the request is invalid" in {
      val request = FakeRequest(routes.SdesCallbackController.callback).withJsonBody(JsObject.empty)
      val response = route(app, request).value
      status(response) mustEqual BAD_REQUEST
    }

    "must fail when the call to get an item fails" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      route(app, request).value.failed.futureValue
    }

    "must fail when the call to update an item fails" in {

      when(mockSubmissionItemRepository.get(any())).thenReturn(Future.successful(Some(item)))
      when(mockSubmissionItemRepository.update(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      route(app, request).value.failed.futureValue
    }
  }
}
