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

import audit.{AuditService, RetryRequestEvent}
import cats.implicits.{toFlatMapOps, toFunctorOps}
import models.Done
import models.submission.{SubmissionItem, SubmissionItemStatus}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.Retrieval.Username
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionAdminController @Inject()(
                                           override val controllerComponents: ControllerComponents,
                                           submissionItemRepository: SubmissionItemRepository,
                                           auditService: AuditService,
                                           auth: BackendAuthComponents
                                         )(implicit ec: ExecutionContext, mat: Materializer) extends BackendBaseController {

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
            status: Seq[SubmissionItemStatus],
            created: Option[LocalDate],
            limit: Int,
            offset: Int
          ): Action[AnyContent] = {

    authorised(owner, read).async {
      submissionItemRepository.list(owner, status, created, limit, offset)
        .map(listResult => Ok(Json.toJson(listResult)))
    }
  }

  def show(owner: String, id: String): Action[AnyContent] =
    authorised(owner, read).async {
      submissionItemRepository.get(owner, id).map {
        _.map(item => Ok(Json.toJson(item)))
          .getOrElse(NotFound)
      }
    }

  def retry(owner: String, id: String): Action[AnyContent] = authorised(owner, write).async { implicit request =>
    retry(owner, id, request.retrieval)
      .as(Accepted)
      .recover {
        case SubmissionItemRepository.NothingToUpdateException =>
          NotFound
      }
  }

  def retryTimeouts(owner: String): Action[AnyContent] = authorised(owner, write).async { implicit request =>
    Mdc.preservingMdc {
      submissionItemRepository.getTimedOutItems(owner).mapAsync(1) { item =>
        retry(owner, item.id, request.retrieval).void
      }.runWith(Sink.fold(0)((m, _) => m + 1))
    }.map { count =>
      Accepted(Json.obj("numberOfItemsRetried" -> count))
    }
  }

  def dailySummaries(owner: String): Action[AnyContent] = authorised(owner, read).async {
    submissionItemRepository
      .dailySummaries(owner)
      .map(summaries => Ok(Json.obj("summaries" -> summaries)))
  }

  def summary(owner: String): Action[AnyContent] = authorised(owner, read).async {
    for {
      errorSummary   <- submissionItemRepository.errorSummary(owner)
      dailySummaries <- submissionItemRepository.dailySummariesV2(owner)
    } yield Ok(Json.obj("errors" -> errorSummary, "summaries" -> dailySummaries))
  }

  def listServices: Action[AnyContent] = Action.async {
    submissionItemRepository.owners.map { services =>
      Ok(Json.obj("services" -> services))
    }
  }

  private def retry(owner: String, id: String, username: Username)(implicit hc: HeaderCarrier): Future[SubmissionItem] =
    submissionItemRepository
      .update(owner, id, SubmissionItemStatus.Submitted, None, None)
      .flatTap(auditRetryRequest(_, username))

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
