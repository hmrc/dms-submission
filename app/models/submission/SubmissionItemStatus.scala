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

package models.submission

import play.api.libs.json._

sealed trait SubmissionItemStatus extends Product with Serializable

object SubmissionItemStatus {

  case object Submitted extends SubmissionItemStatus { override def toString: String = "submitted" }
  case object Forwarded extends SubmissionItemStatus { override def toString: String = "forwarded" }
  case object Processed extends SubmissionItemStatus { override def toString: String = "processed" }
  case object Failed extends SubmissionItemStatus { override def toString: String = "failed" }
  case object Completed extends SubmissionItemStatus { override def toString: String = "completed" }

  lazy val reads: Reads[SubmissionItemStatus] =
    __.read[String].flatMap {
      case "Submitted" => Reads.pure(Submitted)
      case "Forwarded" => Reads.pure(Forwarded)
      case "Processed" => Reads.pure(Processed)
      case "Failed"    => Reads.pure(Failed)
      case "Completed" => Reads.pure(Completed)
      case _           => Reads.failed("Invalid value for submission item status")
    }

  lazy val writes: Writes[SubmissionItemStatus] =
    Writes {
      case Submitted => JsString("Submitted")
      case Forwarded => JsString("Forwarded")
      case Processed => JsString("Processed")
      case Failed    => JsString("Failed")
      case Completed => JsString("Completed")
    }

  implicit lazy val format: Format[SubmissionItemStatus] = Format(reads, writes)
}