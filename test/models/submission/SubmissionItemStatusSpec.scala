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
import play.api.libs.json.{JsString, Json}

class SubmissionItemStatusSpec extends AnyFreeSpec with Matchers {

  "read" - {

    "must read Submitted" in {
      JsString("Submitted").as[SubmissionItemStatus] mustEqual SubmissionItemStatus.Submitted
    }

    "must read Forwarded" in {
      JsString("Forwarded").as[SubmissionItemStatus] mustEqual SubmissionItemStatus.Forwarded
    }

    "must read Failed" in {
      JsString("Failed").as[SubmissionItemStatus] mustEqual SubmissionItemStatus.Failed
    }

    "must read Processed" in {
      JsString("Processed").as[SubmissionItemStatus] mustEqual SubmissionItemStatus.Processed
    }

    "must fail to read anything else" in {
      JsString("foobar").validate[SubmissionItemStatus].isError mustBe true
    }
  }

  "write" - {

    "must write Submitted" in {
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Submitted) mustEqual JsString("Submitted")
    }

    "must write Forwarded" in {
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Forwarded) mustEqual JsString("Forwarded")
    }

    "must write Failed" in {
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Failed) mustEqual JsString("Failed")
    }

    "must write Processed" in {
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Processed) mustEqual JsString("Processed")
    }
  }
}
