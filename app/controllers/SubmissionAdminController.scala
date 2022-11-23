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

import models.SubmissionSummary
import models.submission.SubmissionItemStatus
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Resource, ResourceLocation, ResourceType}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SubmissionAdminController @Inject()(
                                           override val controllerComponents: ControllerComponents,
                                           submissionItemRepository: SubmissionItemRepository,
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
    ))

  def list(owner: String): Action[AnyContent] = authorised(owner, read).async { implicit request =>
    submissionItemRepository.list(owner).map {
      _.map(SubmissionSummary.apply)
    }.map(items => Ok(Json.toJson(items)))
  }

  def retry(owner: String, id: String): Action[AnyContent] = authorised(owner, write).async { implicit request =>
    submissionItemRepository
      .update(owner, id, SubmissionItemStatus.Submitted, None)
      .map(_ => Accepted)
      .recover {
        case SubmissionItemRepository.NothingToUpdateException => NotFound
      }
  }

  def dailySummaries(owner: String): Action[AnyContent] = authorised(owner, read).async { implicit request =>
    submissionItemRepository
      .dailySummaries(owner)
      .map(summaries => Ok(Json.obj("summaries" -> summaries)))
  }
}
