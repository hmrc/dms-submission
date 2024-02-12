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

package models.submission

import models.submission.SubmissionItem.FailureType
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsString, Json}

class SubmissionItemSpec extends AnyFreeSpec with Matchers {

  "FailureType" - {

    "read" - {

      "must read Timeout" in {
        JsString("timeout").as[FailureType] mustEqual FailureType.Timeout
      }

      "must read Sdes" in {
        JsString("sdes").as[FailureType] mustEqual FailureType.Sdes
      }

      "must fail to read anything else" in {
        JsString("foobar").validate[FailureType].isError mustBe true
      }
    }

    "write" - {

      "must write Timeout" in {
        Json.toJson[FailureType](FailureType.Timeout) mustEqual JsString("timeout")
      }

      "must write Sdes" in {
        Json.toJson[FailureType](FailureType.Sdes) mustEqual JsString("sdes")
      }
    }
  }
}
