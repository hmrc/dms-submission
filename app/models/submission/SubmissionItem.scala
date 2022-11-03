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

import play.api.libs.functional.syntax._
import play.api.libs.json.{OFormat, OWrites, Reads, __}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class SubmissionItem(
                                 correlationId: String,
                                 callbackUrl: String,
                                 status: SubmissionItemStatus,
                                 objectSummary: ObjectSummary,
                                 failureReason: Option[String],
                                 lastUpdated: Instant
                               )

object SubmissionItem extends MongoJavatimeFormats.Implicits {

  lazy val reads: Reads[SubmissionItem] = (
    (__ \ "_id").read[String] and
    (__ \ "callbackUrl").read[String] and
    (__ \ "status").read[SubmissionItemStatus] and
    (__ \ "objectSummary").read[ObjectSummary] and
    (__ \ "failureReason").readNullable[String] and
    (__ \ "lastUpdated").read[Instant]
  )(SubmissionItem.apply _)

  lazy val writes: OWrites[SubmissionItem] = (
    (__ \ "_id").write[String] and
    (__ \ "callbackUrl").write[String] and
    (__ \ "status").write[SubmissionItemStatus] and
    (__ \ "objectSummary").write[ObjectSummary] and
    (__ \ "failureReason").writeNullable[String] and
    (__ \ "lastUpdated").write[Instant]
  )(unlift(SubmissionItem.unapply))

  implicit lazy val format: OFormat[SubmissionItem] = OFormat(reads, writes)
}
