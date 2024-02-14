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

import models.submission.{FailureTypeQuery, SubmissionItem}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues}
import play.api.mvc.QueryStringBindable

class FailureTypeQueryQueryStringBindableSpec extends AnyFreeSpec with Matchers with OptionValues with EitherValues {

  "queryStringBindable" - {

    val bindable: QueryStringBindable[FailureTypeQuery] =
      implicitly[QueryStringBindable[FailureTypeQuery]]

    def bind(string: String): Either[String, FailureTypeQuery] =
      bindable.bind("date", Map("date" -> Seq(string))).value


    "must successfully bind None" in {
      bind("none").value mustBe FailureTypeQuery.None
    }

    "must successfully bind IsSet" in {
      bind("set").value mustBe FailureTypeQuery.IsSet
    }

    "must successfully bind Sdes" in {
      bind("sdes").value mustBe FailureTypeQuery.These(SubmissionItem.FailureType.Sdes)
    }

    "must successfully bind Timeout" in {
      bind("timeout").value mustBe FailureTypeQuery.These(SubmissionItem.FailureType.Timeout)
    }

    "must successfully bind multiple failure types" in {
      bindable.bind("date", Map("date" -> Seq("sdes", "timeout"))).value.value mustBe FailureTypeQuery.These(SubmissionItem.FailureType.Sdes, SubmissionItem.FailureType.Timeout)
    }

    "must fail to parse an invalid failure type" in {
      bind("foobar") mustBe Left("invalid failure type")
    }

    "must unbind None" in {
      bindable.unbind("date", FailureTypeQuery.None) mustEqual "date=none"
    }

    "must unbind IsSet" in {
      bindable.unbind("date", FailureTypeQuery.IsSet) mustEqual "date=set"
    }

    "must unbind Sdes" in {
      bindable.unbind("date", FailureTypeQuery.These(SubmissionItem.FailureType.Sdes)) mustEqual "date=sdes"
    }

    "must unbind Timeout" in {
      bindable.unbind("date", FailureTypeQuery.These(SubmissionItem.FailureType.Timeout)) mustEqual "date=timeout"
    }

    "must unbind multiple failure types" in {
      bindable.unbind("date", FailureTypeQuery.These(SubmissionItem.FailureType.Sdes, SubmissionItem.FailureType.Timeout))
    }
  }
}
