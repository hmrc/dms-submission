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

import models.submission.{SubmissionMetadata, SubmissionRequest}
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}

class SubmissionFormProviderSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val form = new SubmissionFormProvider().form

  private val timeOfReceipt = LocalDateTime.of(2022, 2, 1, 0, 0, 0)
  private val completeRequest = SubmissionRequest(
    correlationId = Some("correlationId"),
    callbackUrl = "callbackUrl",
    metadata = SubmissionMetadata(
      store = false,
      source = "source",
      timeOfReceipt = timeOfReceipt.toInstant(ZoneOffset.UTC),
      formId = "formId",
      numberOfPages = 1,
      customerId = "customerId",
      submissionMark = "submissionMark",
      casKey = "casKey",
      classificationType = "classificationType",
      businessArea = "businessArea"
    )
  )

  private val completeData = Map(
    "correlationId" -> "correlationId",
    "callbackUrl" -> "callbackUrl",
    "metadata.store" -> "false",
    "metadata.source" -> "source",
    "metadata.timeOfReceipt" -> DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt),
    "metadata.formId" -> "formId",
    "metadata.numberOfPages" -> "1",
    "metadata.customerId" -> "customerId",
    "metadata.submissionMark" -> "submissionMark",
    "metadata.casKey" -> "casKey",
    "metadata.classificationType" -> "classificationType",
    "metadata.businessArea" -> "businessArea"
  )

  "must return a `SubmissionRequest` when given valid input" in {
    form.bind(completeData).value.value mustEqual completeRequest
  }

  "correlationId" - {

    "must being `None` if there is no correlationId" in {
      form.bind(completeData - "correlationId").value.value.correlationId mustBe None
    }

    "must bind `None` if correlationId is an empty string" in {
      form.bind(completeData + ("correlationId" -> "")).value.value.correlationId mustBe None
    }
  }

  "callbackUrl" - {
    behave like requiredField("callbackUrl")
  }

  "metadata.store" - {

    behave like requiredField("metadata.store")

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.store" -> "foobar"))("metadata.store")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.source" - {
    behave like requiredField("metadata.source")
  }

  "metadata.timeOfReceipt" - {

    behave like requiredField("metadata.timeOfReceipt")

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.timeOfReceipt" -> "foobar"))("metadata.timeOfReceipt")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.formId" - {
    behave like requiredField("metadata.formId")
  }

  "metadata.numberOfPages" - {

    behave like requiredField("metadata.numberOfPages")

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.numberOfPages" -> "foobar"))("metadata.numberOfPages")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.customerId" - {
    behave like requiredField("metadata.customerId")
  }

  "metadata.submissionMark" - {
    behave like requiredField("metadata.submissionMark")
  }

  "metadata.casKey" - {
    behave like requiredField("metadata.casKey")
  }

  "metadata.classificationType" - {
    behave like requiredField("metadata.classificationType")
  }

  "metadata.businessArea" - {
    behave like requiredField("metadata.businessArea")
  }

  private def requiredField(key: String)(implicit pos: Position): Unit = {
    "must fail if it isn't provided" in {
      val boundField = form.bind(Map.empty[String, String])(key)
      boundField.hasErrors mustEqual true
    }
  }
}
