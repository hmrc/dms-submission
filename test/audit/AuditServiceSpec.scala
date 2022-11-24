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
      "auditing.submit-request-event-name" -> "submit-request-event"
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
}
