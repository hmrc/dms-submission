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

import better.files.File
import cats.data.{EitherNec, EitherT, NonEmptyChain}
import cats.implicits._
import models.submission.{SubmissionMetadata, SubmissionRequest, SubmissionResponse}
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, MultipartFormData}
import services.SubmissionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject() (
                                       override val controllerComponents: ControllerComponents,
                                       submissionService: SubmissionService
                                     )(implicit ec: ExecutionContext) extends BackendBaseController {

  def submit = Action.async(parse.multipartFormData(false)) { implicit request =>
    val result: EitherT[Future, NonEmptyChain[String], String] = (
      EitherT.fromEither[Future](getSubmissionRequest(request.body)),
      EitherT.fromEither[Future](getFile(request.body))
    ).parTupled.flatMap { case (submissionRequest, file) =>
      EitherT.liftF(submissionService.submit(submissionRequest, file))
    }
    result.fold(
      errors        => BadRequest(Json.toJson(SubmissionResponse.Failure(errors))),
      correlationId => Accepted(Json.toJson(SubmissionResponse.Success(correlationId)))
    )
  }

  // TODO move this to the companion object for the request so that it's easier to test
  // TODO some validation to make sure callback urls are ok for us to call?
  private def getSubmissionRequest(formData: MultipartFormData[Files.TemporaryFile]): EitherNec[String, SubmissionRequest] = {

    // TODO parse metadata from the request
    val metadata = SubmissionMetadata(
      store = false,
      source = "source",
      timeOfReceipt = LocalDateTime.of(2022, 2, 1, 0, 0, 0).toInstant(ZoneOffset.UTC),
      formId = "formId",
      numberOfPages = 1,
      customerId = "customerId",
      submissionMark = "submissionMark",
      casKey = "casKey",
      classificationType = "classificationType",
      businessArea = "businessArea"
    )

    formData.dataParts.get("callbackUrl")
      .flatMap(_.headOption)
      .map(callbackUrl => SubmissionRequest(callbackUrl, metadata))
      .toRight(NonEmptyChain.one("callbackUrl is required"))
  }

  private def getFile(formData: MultipartFormData[Files.TemporaryFile]): EitherNec[String, File] =
    formData.file("form")
      .map(file => File(file.ref))
      .toRight(NonEmptyChain.one("file is required"))
}
