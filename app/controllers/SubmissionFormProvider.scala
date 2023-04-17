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

import models.submission.{SubmissionMetadata, SubmissionRequest}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.data.{Form, Mapping}

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class SubmissionFormProvider @Inject() (configuration: Configuration) {

  private val allowLocalhostCallbacks: Boolean =
    configuration.get[Boolean]("allow-localhost-callbacks")

  def form: Form[SubmissionRequest] = Form(
    mapping(
      "submissionReference" -> optional(text.verifying(validateSubmissionReference)),
      "callbackUrl" -> text.verifying(validateUrl),
      "metadata" -> metadata
    )(SubmissionRequest.apply)(SubmissionRequest.unapply)
  )

  private def metadata: Mapping[SubmissionMetadata] = mapping(
    "store" -> default(boolean, true),
    "source" -> text.verifying(nonEmpty, maxLength(10)),
    "timeOfReceipt" -> text
      .verifying("timeOfReceipt.invalid", string => Try(parseDateTime(string)).isSuccess)
      .transform(parseDateTime(_).toInstant(ZoneOffset.UTC), DateTimeFormatter.ISO_DATE_TIME.format),
    "formId" -> text.verifying(nonEmpty, maxLength(12)),
    "customerId" -> text.verifying(nonEmpty, maxLength(32)),
    "submissionMark" -> optional(text.verifying(nonEmpty, maxLength(32))),
    "casKey" -> optional(text.verifying(nonEmpty, maxLength(65))),
    "classificationType" -> text.verifying(nonEmpty, maxLength(64)),
    "businessArea" -> text.verifying(nonEmpty, maxLength(32))
  )(SubmissionMetadata.apply)(SubmissionMetadata.unapply)

  private val validateUrl: Constraint[String] =
    Constraint { string =>
      Try(new URL(string)) match {
        case Success(url) =>
          if (url.getHost.endsWith(".mdtp")) {
            Valid
          } else if (allowLocalhostCallbacks && url.getHost == "localhost") {
            Valid
          } else {
            Invalid("callbackUrl.invalidHost")
          }
        case Failure(_) => Invalid("callbackUrl.invalid")
      }
    }

  private def parseDateTime(string: String): LocalDateTime =
    LocalDateTime.parse(string, DateTimeFormatter.ISO_DATE_TIME)

  private val validateSubmissionReference: Constraint[String] =
    Constraint { string =>
      if (string.matches("""^[\dA-Z]{4}(-?)[\dA-Z]{4}\1[\dA-Z]{4}$""")) {
        Valid
      } else {
        Invalid("submissionReference.invalid")
      }
    }
}
