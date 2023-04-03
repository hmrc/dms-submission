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

import better.files.File
import cats.data.{EitherNec, EitherT, NonEmptyChain}
import cats.implicits._
import com.codahale.metrics.{MetricRegistry, Timer}
import com.kenshoo.play.metrics.Metrics
import models.Pdf
import models.submission.{Attachment, SubmissionRequest, SubmissionResponse}
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, MultipartFormData}
import services.{PdfService, SubmissionService}
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.io.IOException
import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject() (
                                       override val controllerComponents: ControllerComponents,
                                       submissionService: SubmissionService,
                                       pdfService: PdfService,
                                       submissionFormProvider: SubmissionFormProvider,
                                       auth: BackendAuthComponents,
                                       clock: Clock,
                                       metrics: Metrics
                                     )(implicit ec: ExecutionContext) extends BackendBaseController with I18nSupport {

  private val metricRegistry: MetricRegistry = metrics.defaultRegistry
  private val timer: Timer = metricRegistry.timer("submission-response.timer")

  private val permission = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType("dms-submission"),
      resourceLocation = ResourceLocation("submit")
    ),
    action = IAAction("WRITE")
  )

  private val authorised = auth.authorizedAction(permission, Retrieval.username)

  def submit = authorised.compose(Action(parse.multipartFormData(false))).async { implicit request =>
    withTimer {
      val result: EitherT[Future, NonEmptyChain[String], String] = (
        EitherT.fromEither[Future](getSubmissionRequest(request.body)),
        getPdf(request.body),
        getAttachments(request.body)
      ).parTupled.flatMap { case (submissionRequest, file, attachments) =>
        EitherT(submissionService.submit(submissionRequest, file, attachments, request.retrieval.value))
      }
      result.fold(
        errors => BadRequest(Json.toJson(SubmissionResponse.Failure(errors))),
        correlationId => Accepted(Json.toJson(SubmissionResponse.Success(correlationId)))
      )
    }
  }

  private def getSubmissionRequest(formData: MultipartFormData[Files.TemporaryFile])(implicit messages: Messages): EitherNec[String, SubmissionRequest] =
    submissionFormProvider.form.bindFromRequest(formData.dataParts).fold(
      formWithErrors => Left(NonEmptyChain.fromSeq(formWithErrors.errors.map(error => formatError(error.key, error.format))).get), // always safe
      _.rightNec[String]
    )

  private def getPdf(formData: MultipartFormData[Files.TemporaryFile])(implicit messages: Messages): EitherT[Future, NonEmptyChain[String], Pdf] =
    for {
      file <- EitherT.fromEither[Future] {
        formData
          .file("form")
          .map(file => File(file.ref))
          .toRight(NonEmptyChain.one(formatError("form", Messages("error.required"))))
        }
      pdf <- EitherT[Future, NonEmptyChain[String], Pdf] {
        pdfService.getPdf(file)
          .map(Right(_))
          .recover[EitherNec[String, Pdf]] { case _: IOException =>
            formatError("form", Messages("error.pdf.invalid")).leftNec
          }
      }
    } yield pdf

  private def getAttachments(formData: MultipartFormData[Files.TemporaryFile])(implicit messages: Messages): EitherT[Future, NonEmptyChain[String], Seq[Attachment]] = {

    val attachments = formData.files.filter(_.key == "attachment")

    lazy val duplicateFiles = attachments.flatMap { attachment =>
      if (attachments.map(_.filename).count(_ == attachment.filename) > 1) Some(attachment.filename) else None
    }.toSet

    if (attachments.length > 5) {
      EitherT.fromEither[Future](formatError("attachments", Messages("error.maxNumber", 5)).leftNec[Seq[Attachment]])
    } else if (duplicateFiles.nonEmpty) {
      EitherT.fromEither[Future](formatError("attachments", Messages("error.duplicate-names", duplicateFiles.mkString(", "))).leftNec[Seq[Attachment]])
    } else {
      EitherT.pure(attachments.map(file => Attachment(file.filename, File(file.ref.path))))
    }
  }

  private def formatError(key: String, message: String): String = s"$key: $message"

  private def withTimer[A](f: => Future[A]): Future[A] = {
    val startTime = clock.instant()
    val future = f
    future.onComplete { _ =>
      timer.update(Duration.between(startTime, clock.instant()))
    }
    future
  }
}
