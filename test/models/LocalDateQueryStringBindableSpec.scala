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

import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.mvc.QueryStringBindable

import java.time.LocalDate

class LocalDateQueryStringBindableSpec extends AnyFreeSpec with Matchers with OptionValues with EitherValues {

  "javaLocalDateQueryStringBindable" - {

    val bindable: QueryStringBindable[LocalDate] =
      implicitly[QueryStringBindable[LocalDate]]

    def bind(string: String): Either[String, LocalDate] =
      bindable.bind("date", Map("date" -> Seq(string))).value


    "must successfully parse a local date" in {
      bind("2022-02-01").value mustEqual LocalDate.of(2022, 2, 1)
    }

    "must fail to parse an invalid date" in {
      bind("foobar") mustBe Left("date: invalid date")
    }

    "must unbind a date" in {
      bindable.unbind("date", LocalDate.of(2022, 2, 1)) mustEqual "date=2022-02-01"
    }
  }
}
