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

import models.submission.{NoFailureType, SubmissionItem}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues}
import play.api.mvc.QueryStringBindable

class FailureTypeQueryStringBindableSpec extends AnyFreeSpec with Matchers with OptionValues with EitherValues {

  "failureTypeQueryStringBindable" - {

    val bindable: QueryStringBindable[Either[NoFailureType, SubmissionItem.FailureType]] =
      implicitly[QueryStringBindable[Either[NoFailureType, SubmissionItem.FailureType]]]

    def bind(string: String): Either[String, Either[NoFailureType, SubmissionItem.FailureType]] =
      bindable.bind("date", Map("date" -> Seq(string))).value


    "must successfully bind NoFailureType" in {
      bind("none").value mustBe Left(NoFailureType)
    }

    "must successfully bind Sdes" in {
      bind("sdes").value mustBe Right(SubmissionItem.FailureType.Sdes)
    }

    "must successfully bind Timeout" in {
      bind("timeout").value mustBe Right(SubmissionItem.FailureType.Timeout)
    }

    "must fail to parse an invalid failure type" in {
      bind("foobar") mustBe Left("invalid failure type")
    }

    "must unbind NoFailureType" in {
      bindable.unbind("date", Left(NoFailureType)) mustEqual "date=none"
    }

    "must unbind Sdes" in {
      bindable.unbind("date", Right(SubmissionItem.FailureType.Sdes)) mustEqual "date=sdes"
    }

    "must unbind Timeout" in {
      bindable.unbind("date", Right(SubmissionItem.FailureType.Timeout)) mustEqual "date=timeout"
    }
  }
}
