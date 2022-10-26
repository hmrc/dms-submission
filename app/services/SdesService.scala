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
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SdesService @Inject() (
                              connector: SdesConnector,
                              configuration: Configuration
                            ) {

  private val informationType: String = configuration.get[String]("services.sdes.information-type")
  private val recipientOrSender: String = configuration.get[String]("services.sdes.recipient-or-sender")

  def notify(objectSummary: ObjectSummaryWithMd5, correlationId: String)(implicit hc: HeaderCarrier): Future[Done] =
    connector.notify(createRequest(objectSummary, correlationId))

  private def createRequest(objectSummary: ObjectSummaryWithMd5, correlationId: String): FileNotifyRequest =
    FileNotifyRequest(
      informationType = informationType,
      file = FileMetadata(
        recipientOrSender = recipientOrSender,
        name = objectSummary.location.fileName,
        location = objectSummary.location.asUri,
        checksum = FileChecksum("md5", objectSummary.contentMd5.value),
        size = objectSummary.contentLength,
        properties = List.empty
      ),
      audit = FileAudit(correlationId)
    )
}
