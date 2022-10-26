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

import models.SubmissionRequest
import play.api.libs.Files.TemporaryFileCreator
import play.api.mvc.ControllerComponents
import services.SubmissionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SubmissionController @Inject() (
                                       override val controllerComponents: ControllerComponents,
                                       temporaryFileCreator: TemporaryFileCreator,
                                       submissionService: SubmissionService
                                     )(implicit ec: ExecutionContext) extends BackendBaseController {

  def submit = Action.async(parse.multipartFormData(false)) { implicit request =>
    val file = better.files.File(temporaryFileCreator.create().path).deleteOnExit()
    submissionService.submit(SubmissionRequest(""), file)
      .map(_ => Accepted)
  }

  // validate request
  // generate metadata xml
  // zip file contents and metadata xml
  // upload to object-store
  // send notification to sdes
  // return accepted
}
