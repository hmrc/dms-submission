/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

final case class DailySummaryV2(
                                 date: LocalDate,
                                 processing: Int,
                                 completed: Int,
                                 failed: Int
                               )

object DailySummaryV2 {

  implicit lazy val format: OFormat[DailySummaryV2] = Json.format

  lazy val mongoFormat: OFormat[DailySummaryV2] = {
    // Intellij thinks this is not required, but it's required by the macro expansion of `Json.format`
    import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._
    Json.format
  }
}
