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
import logging.Logging
import models.Done
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest}
import models.submission.{ObjectSummary, QueryResult, SubmissionItemStatus}
import play.api.Configuration
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesService @Inject() (
                              connector: SdesConnector,
                              repository: SubmissionItemRepository,
                              configuration: Configuration
                            )(implicit ec: ExecutionContext) extends Logging {

  private val informationType: String = configuration.get[String]("services.sdes.information-type")
  private val recipientOrSender: String = configuration.get[String]("services.sdes.recipient-or-sender")

  def notifyOldestSubmittedItem(): Future[QueryResult] =
    repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Submitted) { item =>
      notify(item.objectSummary, item.sdesCorrelationId)(HeaderCarrier()).map { _ =>
        item.copy(status = SubmissionItemStatus.Forwarded)
      }
    }.recover { case e =>
      logger.error("Error notifying SDES about a submitted item")
      QueryResult.Found
    }

  def notifySubmittedItems(): Future[Done] =
    notifyOldestSubmittedItem().flatMap {
      case QueryResult.Found    => notifySubmittedItems()
      case QueryResult.NotFound => Future.successful(Done)
    }

  // TODO remove this once we only notify using the worker
  def notify(objectSummary: ObjectSummaryWithMd5, correlationId: String)(implicit hc: HeaderCarrier): Future[Done] =
    connector.notify(createRequest(objectSummary, correlationId))

  private def notify(objectSummary: ObjectSummary, correlationId: String)(implicit hc: HeaderCarrier): Future[Done] =
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

  private def createRequest(objectSummary: ObjectSummary, correlationId: String): FileNotifyRequest =
    FileNotifyRequest(
      informationType = informationType,
      file = FileMetadata(
        recipientOrSender = recipientOrSender,
        name = objectSummary.location,
        location = objectSummary.location,
        checksum = FileChecksum("md5", objectSummary.contentMd5),
        size = objectSummary.contentLength,
        properties = List.empty
      ),
      audit = FileAudit(correlationId)
    )
}
