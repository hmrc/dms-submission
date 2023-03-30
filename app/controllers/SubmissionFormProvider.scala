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

import models.submission.{Attachment, SubmissionMetadata, SubmissionRequest}
import cats.implicits._
import play.api.Configuration
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid}

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class SubmissionFormProvider @Inject() (configuration: Configuration) {

  private val base64Decoder: Base64.Decoder = Base64.getDecoder

  private val allowLocalhostCallbacks: Boolean =
    configuration.get[Boolean]("allow-localhost-callbacks")

  def form(owner: String): Form[SubmissionRequest] = Form(
    mapping(
      "submissionReference" -> optional(text.verifying(validateSubmissionReference)),
      "callbackUrl" -> text.verifying(validateUrl),
      "metadata" -> metadata,
      "attachments" -> seq(attachment(owner)).verifying(attachmentsConstraint)
    )(SubmissionRequest.apply)(SubmissionRequest.unapply)
  )

  private def attachment(owner: String): Mapping[Attachment] = mapping(
    "location" -> text.verifying(nonEmptyString),
    "contentMd5" -> text.verifying(validateContentMd5),
    "owner" -> optional(text).transform[String](_.getOrElse(owner), _.some)
  )(Attachment.apply)(Attachment.unapply)

  private def metadata: Mapping[SubmissionMetadata] = mapping(
    "store" -> text
      .verifying("error.invalid", _.toBooleanOption.isDefined)
      .transform(_.toBoolean, (_: Boolean).toString),
    "source" -> text,
    "timeOfReceipt" -> text
      .verifying("timeOfReceipt.invalid", string => Try(parseDateTime(string)).isSuccess)
      .transform(parseDateTime(_).toInstant(ZoneOffset.UTC), DateTimeFormatter.ISO_DATE_TIME.format),
    "formId" -> text.verifying(nonEmptyString),
    "customerId" -> text.verifying(nonEmptyString),
    "submissionMark" -> text,
    "casKey" -> text,
    "classificationType" -> text,
    "businessArea" -> text.verifying(nonEmptyString),
  )(SubmissionMetadata.apply)(SubmissionMetadata.unapply)

  private val validateContentMd5: Constraint[String] =
    Constraint { string =>
      Try(base64Decoder.decode(string)) match {
        case Success(bytes) if bytes.length == 16 => Valid
        case _                                    => Invalid("attachments.contentMd5.invalid")
      }
    }

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

  private val attachmentsConstraint: Constraint[Seq[_]] =
    Constraint { attachments =>
      if (attachments.length > 5) {
        Invalid("attachments.max")
      } else {
        Valid
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

  private val nonEmptyString: Constraint[String] =
    Constraint { string =>
      if (string.trim.isEmpty) Invalid("error.required") else Valid
    }
}
