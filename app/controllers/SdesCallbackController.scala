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

package controllers

import cats.implicits._
import audit.{AuditService, SdesCallbackEvent}
import controllers.SdesCallbackController.SubmissionLockedException
import logging.Logging
import models.Done
import models.sdes.{NotificationCallback, NotificationType}
import models.submission.{SubmissionItem, SubmissionItemStatus}
import play.api.Configuration
import play.api.mvc.ControllerComponents
import repositories.SubmissionItemRepository
import services.RetryService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class SdesCallbackController @Inject() (
                                         override val controllerComponents: ControllerComponents,
                                         submissionItemRepository: SubmissionItemRepository,
                                         clock: Clock,
                                         configuration: Configuration,
                                         retryService: RetryService,
                                         auditService: AuditService
                                       )(implicit ec: ExecutionContext) extends BackendBaseController with Logging {

  private val lockTtl: Duration = Duration.ofSeconds(configuration.get[Int]("lock-ttl"))

  def callback = Action.async(parse.json[NotificationCallback]) { implicit request =>
    logger.info(s"SDES Callback received for correlationId: ${request.body.correlationID}, with status: ${request.body.notification}")
    retryService.retry(
      submissionItemRepository.get(request.body.correlationID).flatMap {
        _.map { item =>
          if (isLocked(item)) {
            logger.warn(s"correlationId: ${request.body.correlationID} was locked!")
            Future.failed(SubmissionLockedException(item.sdesCorrelationId))
          } else {
            getNewItemStatus(request.body.notification).map { newStatus =>
              submissionItemRepository.update(item.sdesCorrelationId, newStatus, request.body.failureReason)
                .map(_ => Ok)
            }.getOrElse(Future.successful(Ok))
          }.flatTap(_ => auditSdesCallback(item, request.body))
        }.getOrElse {
          logger.warn(s"SDES Callback received for correlationId: ${request.body.correlationID}, with status: ${request.body.notification} but no matching submission was found")
          Future.successful(NotFound)
        }
      }, retriable = isSubmissionLockedException
    )
  }

  private def getNewItemStatus(notificationType: NotificationType): Option[SubmissionItemStatus] =
    notificationType match {
      case NotificationType.FileProcessed         => Some(SubmissionItemStatus.Processed)
      case NotificationType.FileProcessingFailure => Some(SubmissionItemStatus.Failed)
      case _                                      => None
    }

  private def isLocked(item: SubmissionItem): Boolean =
    item.lockedAt.exists(_.isAfter(clock.instant().minus(lockTtl)))

  private def isSubmissionLockedException(e: Throwable): Boolean =
    e.isInstanceOf[SubmissionLockedException]

  private def auditSdesCallback(item: SubmissionItem, request: NotificationCallback)(implicit hc: HeaderCarrier): Future[Done] =
    Future.successful {
      val event = SdesCallbackEvent(
        id = item.id,
        owner = item.owner,
        sdesCorrelationId = item.sdesCorrelationId,
        status = request.notification.toString,
        failureReason = request.failureReason
      )
      auditService.auditSdesCallback(event)
      Done
    }

}

object SdesCallbackController {

  final case class SubmissionLockedException(sdesCorrelationId: String) extends Throwable with NoStackTrace {
    override def getMessage: String = s"Item with sdesCorrelationId $sdesCorrelationId was locked"
  }
}