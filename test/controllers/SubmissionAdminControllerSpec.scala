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

import audit.{AuditService, RetryRequestEvent}
import models.{DailySummary, SubmissionSummary}
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import repositories.SubmissionItemRepository
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.Retrieval.Username
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDate, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SubmissionAdminControllerSpec
  extends AnyFreeSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  private val mockSubmissionItemRepository: SubmissionItemRepository = mock[SubmissionItemRepository]
  private val mockAuditService: AuditService = mock[AuditService]

  override def beforeEach(): Unit = {
    Mockito.reset[Any](
      mockSubmissionItemRepository,
      mockStubBehaviour,
      mockAuditService
    )
    super.beforeEach()
  }

  private val clock = Clock.fixed(Instant.now, ZoneOffset.UTC)

  private val mockStubBehaviour = mock[StubBehaviour]
  private val stubBackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), implicitly)

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[Clock].toInstance(clock),
      bind[SubmissionItemRepository].toInstance(mockSubmissionItemRepository),
      bind[BackendAuthComponents].toInstance(stubBackendAuthComponents),
      bind[AuditService].toInstance(mockAuditService)
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

    "must return a list of submissions for an authorised user" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("READ"))
      when(mockSubmissionItemRepository.list(any(), any(), any(), any(), any())).thenReturn(Future.successful(Seq(item)))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request =
        FakeRequest(routes.SubmissionAdminController.list(
          owner = "owner",
          status = Some(SubmissionItemStatus.Completed),
          created = Some(LocalDate.now(clock)),
          limit = 10,
          offset = 5
        )).withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResult = List(SubmissionSummary("id", SubmissionItemStatus.Submitted, None, clock.instant()))
      contentAsJson(result) mustEqual Json.toJson(expectedResult)

      verify(mockSubmissionItemRepository).list(
        owner = "owner",
        status = Some(SubmissionItemStatus.Completed),
        created = Some(LocalDate.now(clock)),
        limit = 10,
        offset = 5
      )
    }

    "must return unauthorised for an unauthenticated user" in {

      val request = FakeRequest(routes.SubmissionAdminController.list("owner")) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).list(any(), any(), any(), any(), any())
    }

    "must return unauthorised for an unauthorised user" in {

      when(mockStubBehaviour.stubAuth(any(), eqTo(Retrieval.username))).thenReturn(Future.failed(new Exception("foo")))

      val request =
        FakeRequest(routes.SubmissionAdminController.list("owner"))
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).list(any(), any(), any(), any(), any())
    }
  }

  "show" - {

    "must return the data for the requested submission item when it exists and the user is authorised" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("READ"))
      when(mockSubmissionItemRepository.get(eqTo("owner"), eqTo("id"))).thenReturn(Future.successful(Some(item)))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request =
        FakeRequest(routes.SubmissionAdminController.show("owner", "id"))
          .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(item)
      verify(mockSubmissionItemRepository, times(1)).get(eqTo("owner"), eqTo("id"))
    }

    "must return Not Found when the requested submission item does not exist and the user is authorised" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("READ"))
      when(mockSubmissionItemRepository.get(eqTo("owner"), eqTo("id"))).thenReturn(Future.successful(None))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request = FakeRequest(routes.SubmissionAdminController.show("owner", "id"))
        .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
      verify(mockSubmissionItemRepository, times(1)).get(eqTo("owner"), eqTo("id"))
    }

    "must fail for an unauthenticated user" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("READ"))
      when(mockSubmissionItemRepository.get(eqTo("owner"), eqTo("id"))).thenReturn(Future.successful(Some(item)))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request = FakeRequest(routes.SubmissionAdminController.show("owner", "id")) // No auth header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).get(any(), any())
    }

    "must fail for an unauthorised user" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("READ"))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.failed(new RuntimeException()))

      val request =
        FakeRequest(routes.SubmissionAdminController.show("owner", "id"))
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).get(eqTo("owner"), eqTo("id"))
    }
  }

  "retry" - {

    "must update a submission item to Submitted and return Accepted when the user is authorised" in {

      val expectedAudit = RetryRequestEvent(
        id = "id",
        owner = "owner",
        sdesCorrelationId = "sdesCorrelationId",
        user = "username"
      )

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("WRITE"))
      when(mockSubmissionItemRepository.update(eqTo("owner"), eqTo("id"), any(), any())).thenReturn(Future.successful(item))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request =
        FakeRequest(routes.SubmissionAdminController.retry("owner", "id"))
          .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual ACCEPTED
      verify(mockSubmissionItemRepository, times(1)).update(eqTo("owner"), eqTo("id"), eqTo(SubmissionItemStatus.Submitted), eqTo(None))
      verify(mockAuditService, times(1)).auditRetryRequest(eqTo(expectedAudit))(any())
    }

    "must return Not Found when an authorised user attempts to retry a submission item that cannot be found" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("WRITE"))
      when(mockSubmissionItemRepository.update(eqTo("owner"), eqTo("id"), any(), any())).thenReturn(Future.failed(SubmissionItemRepository.NothingToUpdateException))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request =
        FakeRequest(routes.SubmissionAdminController.retry("owner", "id"))
          .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
      verify(mockAuditService, never()).auditRetryRequest(any())(any())
    }

    "must fail for an unauthenticated user" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("WRITE"))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request = FakeRequest(routes.SubmissionAdminController.retry("owner", "id")) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).update(any(), any(), any(), any())
    }

    "must fail when the user is not authorised" in {

      when(mockStubBehaviour.stubAuth(any(), eqTo(Retrieval.username))).thenReturn(Future.failed(new Exception("foo")))

      val request =
        FakeRequest(routes.SubmissionAdminController.retry("owner", "id"))
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).update(any(), any(), any(), any())
    }
  }

  "dailySummaries" - {

    "must return a list of summaries for an authorised user" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("READ"))
      val dailySummaries = List(DailySummary(LocalDate.now, 1, 2, 3, 4, 5))
      when(mockSubmissionItemRepository.dailySummaries(any())).thenReturn(Future.successful(dailySummaries))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request =
        FakeRequest(routes.SubmissionAdminController.dailySummaries("owner"))
          .withHeaders("Authorization" -> "Token foo")

      val result = route(app, request).value

      status(result) mustEqual OK

      val expectedResult = Json.obj("summaries" -> dailySummaries)
      contentAsJson(result) mustEqual Json.toJson(expectedResult)
    }

    "must fail for an unauthenticated user" in {

      val predicate = Permission(Resource(ResourceType("dms-submission"), ResourceLocation("owner")), IAAction("WRITE"))
      when(mockStubBehaviour.stubAuth(eqTo(Some(predicate)), eqTo(Retrieval.username))).thenReturn(Future.successful(Username("username")))

      val request = FakeRequest(routes.SubmissionAdminController.dailySummaries("owner")) // No Authorization header

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).dailySummaries(any())
    }

    "must fail when the user is not authorised" in {

      when(mockStubBehaviour.stubAuth(any(), eqTo(Retrieval.username))).thenReturn(Future.failed(new Exception("foo")))

      val request =
        FakeRequest(routes.SubmissionAdminController.dailySummaries("owner"))
          .withHeaders("Authorization" -> "Token foo")

      route(app, request).value.failed.futureValue
      verify(mockSubmissionItemRepository, never()).dailySummaries(any())
    }
  }
}
