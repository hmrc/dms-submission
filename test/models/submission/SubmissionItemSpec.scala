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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits.jatInstantFormat

import java.time.Instant
import java.time.temporal.ChronoUnit

class SubmissionItemSpec extends AnyFreeSpec with Matchers {

  private val lastUpdated = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val lastModified = Instant.now.minusSeconds(1).truncatedTo(ChronoUnit.MILLIS)

  private val objectSummary = ObjectSummary(
    location = "location",
    contentLength = 1337,
    contentMd5 = "md5",
    lastModified = lastModified,
  )

  private val json = Json.obj(
    "_id" -> "correlationId",
    "status" -> Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Submitted),
    "objectSummary" -> objectSummary,
    "failureReason" -> "failure",
    "lastUpdated" -> lastUpdated
  )

  private val model = SubmissionItem(
    correlationId = "correlationId",
    status = SubmissionItemStatus.Submitted,
    objectSummary = objectSummary,
    failureReason = Some("failure"),
    lastUpdated = lastUpdated
  )

  "read" - {

    "must read a valid payload" in {
      json.as[SubmissionItem] mustEqual model
    }

    "must read a payload without failureReason" in {
      val newModel = model.copy(failureReason = None)
      val newJson = json - "failureReason"
      newJson.as[SubmissionItem] mustEqual newModel
    }
  }

  "write" - {

    "must write a payload" in {
      Json.toJson(model) mustEqual json
    }
  }
}
