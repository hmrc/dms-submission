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

import cats.implicits.toTraverseOps
import play.api.mvc.QueryStringBindable

sealed trait FailureTypeQuery extends Product with Serializable

object FailureTypeQuery {

  case object None extends FailureTypeQuery
  case object IsSet extends FailureTypeQuery
  case class These(types: SubmissionItem.FailureType*) extends FailureTypeQuery

  implicit lazy val queryStringBindable: QueryStringBindable[FailureTypeQuery] =
    new QueryStringBindable[FailureTypeQuery] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, FailureTypeQuery]] =
        params.get(key).map { values =>
          if (values.contains("none")) {
            Right(FailureTypeQuery.None)
          } else if (values.contains("set")) {
            Right(FailureTypeQuery.IsSet)
          } else {
            values.traverse {
              case "sdes"    => Right(SubmissionItem.FailureType.Sdes)
              case "timeout" => Right(SubmissionItem.FailureType.Timeout)
              case _         => Left("invalid failure type")
            }.map(failureTypes => FailureTypeQuery.These(failureTypes: _*))
          }
        }

      override def unbind(key: String, value: FailureTypeQuery): String =
        value match {
          case FailureTypeQuery.None =>
            s"$key=none"
          case FailureTypeQuery.IsSet =>
            s"$key=set"
          case FailureTypeQuery.These(failureTypes @ _*) =>
            failureTypes.map {
              case SubmissionItem.FailureType.Sdes =>
                s"$key=sdes"
              case SubmissionItem.FailureType.Timeout =>
                s"$key=timeout"
            }.mkString("&")
        }
    }
}
