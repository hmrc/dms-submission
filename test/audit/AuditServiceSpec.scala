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

package audit

import org.mockito.ArgumentMatchers.{eq => eqTo, any}
import org.mockito.Mockito.{times, verify}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class AuditServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  private val mockAuditConnector = mock[AuditConnector]

  private val app = GuiceApplicationBuilder()
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .configure(
      "auditing.submit-request-event-name" -> "submit-request-event",
      "auditing.sdes-callback-event-name" -> "sdes-callback-event",
      "auditing.retry-request-event-name" -> "retry-request-event"
    )
    .build()

  private val service = app.injector.instanceOf[AuditService]

  "auditSubmitRequest" - {

    "must call the audit connector with the given event" in {

      val hc = HeaderCarrier()

      val request = SubmitRequestEvent(
        id = "id",
        owner = "owner",
        sdesCorrelationId = "sdesCorrelationId",
        customerId = "customerId",
        formId = "formId",
        classificationType = "classificationType",
        businessArea = "businessArea",
        hash = "hash"
      )

      service.auditSubmitRequest(request)(hc)

      verify(mockAuditConnector, times(1)).sendExplicitAudit(eqTo("submit-request-event"), eqTo(request))(eqTo(hc), any(), any())
    }
  }

  "auditSdesCallback" - {

    "must call the audit connector with the given event" in {

      val hc = HeaderCarrier()

      val request = SdesCallbackEvent(
        id = "id",
        owner = "owner",
        sdesCorrelationId = "sdesCorrelationId",
        status = "status",
        failureReason = Some("failureReason")
      )

      service.auditSdesCallback(request)(hc)

      verify(mockAuditConnector, times(1)).sendExplicitAudit(eqTo("sdes-callback-event"), eqTo(request))(eqTo(hc), any(), any())
    }
  }

  "auditRetryRequest" - {

    "must call the audit connector with the given event" in {

      val hc = HeaderCarrier()

      val request = RetryRequestEvent(
        id = "id",
        owner = "owner",
        sdesCorrelationId = "sdesCorrelationId",
        user = "user"
      )

      service.auditRetryRequest(request)(hc)

      verify(mockAuditConnector, times(1)).sendExplicitAudit(eqTo("retry-request-event"), eqTo(request))(eqTo(hc), any(), any())
    }
  }
}
