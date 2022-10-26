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

package models

import models.sdes.NotificationType
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsString, Json}

class NotificationTypeSpec extends AnyFreeSpec with Matchers with OptionValues {

  "read" - {

    "must read FileReady" in {
      JsString("FileReady").as[NotificationType] mustEqual NotificationType.FileReady
    }

    "must read FileReceived" in {
      JsString("FileReceived").as[NotificationType] mustEqual NotificationType.FileReceived
    }

    "must read FileProcessingFailure" in {
      JsString("FileProcessingFailure").as[NotificationType] mustEqual NotificationType.FileProcessingFailure
    }

    "must read FileProcessed" in {
      JsString("FileProcessed").as[NotificationType] mustEqual NotificationType.FileProcessed
    }

    "must fail to read anything else" in {
      JsString("foobar").validate[NotificationType].isError mustBe true
    }
  }

  "write" - {

    "must write FileReady" in {
      Json.toJson[NotificationType](NotificationType.FileReady) mustEqual JsString("FileReady")
    }

    "must write FileReceived" in {
      Json.toJson[NotificationType](NotificationType.FileReceived) mustEqual JsString("FileReceived")
    }

    "must write FileProcessingFailure" in {
      Json.toJson[NotificationType](NotificationType.FileProcessingFailure) mustEqual JsString("FileProcessingFailure")
    }

    "must write FileProcessed" in {
      Json.toJson[NotificationType](NotificationType.FileProcessed) mustEqual JsString("FileProcessed")
    }
  }
}
