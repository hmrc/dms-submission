package models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsSuccess, Json}

import java.time.LocalDate

class DailySummarySpec extends AnyFreeSpec with Matchers {

  "mongoFormats" - {

    "must serialise and deserialise to / from json" in {

      val now = LocalDate.of(2022, 1, 2)
      val summary = DailySummary(now, 1, 2, 3, 4, 5)
      val expectedJson = Json.obj(
        "_id" -> "2022-01-02",
        "submitted" -> 1,
        "forwarded" -> 2,
        "processed" -> 3,
        "failed" -> 4,
        "completed" -> 5
      )

      Json.toJson(summary)(DailySummary.mongoFormat) mustEqual expectedJson
      expectedJson.validate[DailySummary](DailySummary.mongoFormat) mustEqual JsSuccess(summary)
    }
  }

  "formats" - {

    "must serialise and deserialise to / from json" in {

      val now = LocalDate.of(2022, 1, 2)
      val summary = DailySummary(now, 1, 2, 3, 4, 5)
      val expectedJson = Json.obj(
        "date" -> "2022-01-02",
        "submitted" -> 1,
        "forwarded" -> 2,
        "processed" -> 3,
        "failed" -> 4,
        "completed" -> 5
      )

      Json.toJson(summary)(DailySummary.format) mustEqual expectedJson
      expectedJson.validate[DailySummary](DailySummary.format) mustEqual JsSuccess(summary)
    }
  }
}
