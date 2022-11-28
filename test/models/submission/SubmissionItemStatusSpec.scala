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

import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsString, Json}
import play.api.mvc.QueryStringBindable

class SubmissionItemStatusSpec extends AnyFreeSpec with Matchers with OptionValues with EitherValues {

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

    "must read Completed" in {
      JsString("Completed").as[SubmissionItemStatus] mustEqual SubmissionItemStatus.Completed
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

    "must write Completed" in {
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Completed) mustEqual JsString("Completed")
    }
  }

  "queryStringBindable" - {

    val bindable: QueryStringBindable[SubmissionItemStatus] =
      implicitly[QueryStringBindable[SubmissionItemStatus]]

    def bind(string: String): Either[String, SubmissionItemStatus] =
      bindable.bind("status", Map("status" -> Seq(string))).value

    def unbind(status: SubmissionItemStatus): String =
      bindable.unbind("status", status)

    "must bind Submitted" in {
      bind("Submitted").value mustEqual SubmissionItemStatus.Submitted
      bind("submitted").value mustEqual SubmissionItemStatus.Submitted
    }

    "must bind Forwarded" in {
      bind("Forwarded").value mustEqual SubmissionItemStatus.Forwarded
      bind("forwarded").value mustEqual SubmissionItemStatus.Forwarded
    }

    "must bind Failed" in {
      bind("Failed").value mustEqual SubmissionItemStatus.Failed
      bind("failed").value mustEqual SubmissionItemStatus.Failed
    }

    "must bind Processed" in {
      bind("Processed").value mustEqual SubmissionItemStatus.Processed
      bind("processed").value mustEqual SubmissionItemStatus.Processed
    }

    "must bind Completed" in {
      bind("Completed").value mustEqual SubmissionItemStatus.Completed
      bind("completed").value mustEqual SubmissionItemStatus.Completed
    }

    "must fail for an unknown status" in {
      bind("foobar") mustBe Left("status: invalid status")
    }

    "must unbind Submitted" in {
      unbind(SubmissionItemStatus.Submitted) mustEqual "status=submitted"
    }

    "must unbind Forwarded" in {
      unbind(SubmissionItemStatus.Forwarded) mustEqual "status=forwarded"
    }

    "must unbind Failed" in {
      unbind(SubmissionItemStatus.Failed) mustEqual "status=failed"
    }

    "must unbind Processed" in {
      unbind(SubmissionItemStatus.Processed) mustEqual "status=processed"
    }

    "must unbind Completed" in {
      unbind(SubmissionItemStatus.Completed) mustEqual "status=completed"
    }
  }
}
