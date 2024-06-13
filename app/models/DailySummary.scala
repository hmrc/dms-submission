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

import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.time.LocalDate

final case class DailySummary(
                               date: LocalDate,
                               submitted: Int,
                               forwarded: Int,
                               processed: Int,
                               failed: Int,
                               completed: Int
                             )

object DailySummary {

  lazy val mongoReads: Reads[DailySummary] = (
    (__ \ "_id").read[LocalDate] and
    (__ \ "submitted").read[Int] and
    (__ \ "forwarded").read[Int] and
    (__ \ "processed").read[Int] and
    (__ \ "failed").read[Int] and
    (__ \ "completed").read[Int]
  )(DailySummary.apply _)

  lazy val mongoWrites: OWrites[DailySummary] = (
    (__ \ "_id").write[LocalDate] and
    (__ \ "submitted").write[Int] and
    (__ \ "forwarded").write[Int] and
    (__ \ "processed").write[Int] and
    (__ \ "failed").write[Int] and
    (__ \ "completed").write[Int]
  )(o => Tuple.fromProductTyped(o))

  lazy val mongoFormat: OFormat[DailySummary] = OFormat(mongoReads, mongoWrites)

  implicit lazy val format: OFormat[DailySummary] = Json.format
}
