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
import logging.Logging
import models.sdes.{NotificationCallback, NotificationType}
import models.submission.SubmissionItemStatus
import play.api.mvc.ControllerComponents
import repositories.SubmissionItemRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesCallbackController @Inject() (
                                         override val controllerComponents: ControllerComponents,
                                         submissionItemRepository: SubmissionItemRepository,
                                         callbackConnector: CallbackConnector
                                       )(implicit ec: ExecutionContext) extends BackendBaseController with Logging {

  def callback = Action.async(parse.json[NotificationCallback]) { implicit request =>
    logger.info(s"SDES Callback received for correlationId: ${request.body.correlationID}, with status: ${request.body.notification}")
    submissionItemRepository.get(request.body.correlationID).flatMap {
      _.map { item =>
        request.body.notification match {
          case NotificationType.FileProcessed | NotificationType.FileProcessingFailure =>
            for {
              updatedItem <- submissionItemRepository.update(item.sdesCorrelationId, getItemStatus(request.body.notification), request.body.failureReason)
              _           <- callbackConnector.notify(updatedItem)
            } yield Ok
          case _ => Future.successful(Ok)
        }
      }.getOrElse {
        logger.warn(s"SDES Callback received for correlationId: ${request.body.correlationID}, with status: ${request.body.notification} but no matching submission was found")
        Future.successful(NotFound)
      }
    }
  }

  private def getItemStatus(notificationType: NotificationType): SubmissionItemStatus =
    notificationType match {
      case NotificationType.FileProcessed         => SubmissionItemStatus.Processed
      case NotificationType.FileProcessingFailure => SubmissionItemStatus.Failed
    }
}