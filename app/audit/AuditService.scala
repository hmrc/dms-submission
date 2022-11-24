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

import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AuditService @Inject() (
                               auditConnector: AuditConnector,
                               configuration: Configuration
                             )(implicit ec: ExecutionContext) {

  private val submitRequestEventName: String = configuration.get[String]("auditing.submit-request-event-name")
  private val sdesCallbackEventName: String = configuration.get[String]("auditing.sdes-callback-event-name")

  def auditSubmitRequest(event: SubmitRequestEvent)(implicit hc: HeaderCarrier): Unit =
    auditConnector.sendExplicitAudit(submitRequestEventName, event)

  def auditSdesCallback(event: SdesCallbackEvent)(implicit hc: HeaderCarrier): Unit =
    auditConnector.sendExplicitAudit(sdesCallbackEventName, event)
}
