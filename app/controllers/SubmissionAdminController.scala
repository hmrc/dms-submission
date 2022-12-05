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
import cats.implicits.{toFlatMapOps, toFunctorOps}
import models.submission.{SubmissionItem, SubmissionItemStatus}
import models.{Done, SubmissionSummary}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.Retrieval.Username
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionAdminController @Inject()(
                                           override val controllerComponents: ControllerComponents,
                                           submissionItemRepository: SubmissionItemRepository,
                                           auditService: AuditService,
                                           auth: BackendAuthComponents
                                         )(implicit ec: ExecutionContext) extends BackendBaseController {

  private val read = IAAction("READ")
  private val write = IAAction("WRITE")

  private val authorised = (owner: String, action: IAAction) =>
    auth.authorizedAction(Permission(
      Resource(
        ResourceType("dms-submission"),
        ResourceLocation(owner)
      ),
      action
    ), Retrieval.username)

  def list(
            owner: String,
            status: Option[SubmissionItemStatus],
            created: Option[LocalDate],
            limit: Int,
            offset: Int
          ): Action[AnyContent] = {

    authorised(owner, read).async { implicit request =>
      submissionItemRepository.list(owner, status, created, limit, offset).map {
        _.map(SubmissionSummary.apply)
      }.map(items => Ok(Json.toJson(items)))
    }
  }

  def show(owner: String, id: String): Action[AnyContent] =
    authorised(owner, read).async { implicit request =>
      submissionItemRepository.get(owner, id).map {
        _.map(item => Ok(Json.toJson(item)))
          .getOrElse(NotFound)
      }
    }

  def retry(owner: String, id: String): Action[AnyContent] = authorised(owner, write).async { implicit request =>
    submissionItemRepository
      .update(owner, id, SubmissionItemStatus.Submitted, None)
      .flatTap(auditRetryRequest(_, request.retrieval))
      .as(Accepted)
      .recover {
        case SubmissionItemRepository.NothingToUpdateException => NotFound
      }
  }

  def dailySummaries(owner: String): Action[AnyContent] = authorised(owner, read).async { implicit request =>
    submissionItemRepository
      .dailySummaries(owner)
      .map(summaries => Ok(Json.obj("summaries" -> summaries)))
  }

  private def auditRetryRequest(item: SubmissionItem, username: Username)(implicit hc: HeaderCarrier): Future[Done] =
    Future.successful {
      val event = RetryRequestEvent(
        id = item.id,
        owner = item.owner,
        sdesCorrelationId = item.sdesCorrelationId,
        user = username.value
      )
      auditService.auditRetryRequest(event)
      Done
    }
}
