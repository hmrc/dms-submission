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

package models

import models.submission.{SubmissionItem, SubmissionItemStatus}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class SubmissionSummary(
                                    id: String,
                                    status: SubmissionItemStatus,
                                    failureType: Option[SubmissionItem.FailureType],
                                    failureReason: Option[String],
                                    lastUpdated: Instant
                                  )

object SubmissionSummary extends MongoJavatimeFormats.Implicits {

  implicit lazy val format: OFormat[SubmissionSummary] = Json.format

  def apply(submissionItem: SubmissionItem): SubmissionSummary =
    SubmissionSummary(
      id = submissionItem.id,
      status = submissionItem.status,
      failureType = submissionItem.failureType,
      failureReason = submissionItem.failureReason,
      lastUpdated = submissionItem.lastUpdated
    )
}
