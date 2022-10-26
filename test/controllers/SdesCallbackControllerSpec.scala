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

package controllers

import models.sdes.{NotificationCallback, NotificationType}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._

class SdesCallbackControllerSpec extends AnyFreeSpec with Matchers with OptionValues {

  "callback" - {

    val app = GuiceApplicationBuilder().build()

    "must return OK when the request is valid" in {

      val requestBody = NotificationCallback(
        notification = NotificationType.FileProcessed,
        filename = "filename",
        correlationID = "correlationID",
        failureReason = None
      )

      val request = FakeRequest(routes.SdesCallbackController.callback)
        .withJsonBody(Json.toJson(requestBody))

      val response = route(app, request).value

      status(response) mustEqual OK
    }

    "must return BAD_REQUEST when the request is invalid" in {
      val request = FakeRequest(routes.SdesCallbackController.callback).withJsonBody(JsObject.empty)
      val response = route(app, request).value
      status(response) mustEqual BAD_REQUEST
    }
  }
}
