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

package controllers

import models.submission.{SubmissionMetadata, SubmissionRequest}
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}

class SubmissionFormProviderSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val configuration = Configuration("allow-localhost-callbacks" -> false)
  private val form = new SubmissionFormProvider(configuration).form

  private val timeOfReceipt = LocalDateTime.of(2022, 2, 1, 0, 0, 0)

  private val completeRequest = SubmissionRequest(
    submissionReference = Some("1234567890AB"),
    callbackUrl = "http://test-service.protected.mdtp/callback",
    metadata = SubmissionMetadata(
      store = false,
      source = "source",
      timeOfReceipt = timeOfReceipt.toInstant(ZoneOffset.UTC),
      formId = "formId",
      customerId = "customerId",
      submissionMark = Some("submissionMark"),
      casKey = Some("casKey"),
      classificationType = "classificationType",
      businessArea = "businessArea"
    ),
    attachments = Seq.empty
  )

  private val minimalRequest = completeRequest.copy(
    metadata = completeRequest.metadata.copy(
      store = true,
      submissionMark = None,
      casKey = None
    )
  )

  private val completeData = Map(
    "submissionReference" -> "1234567890AB",
    "callbackUrl" -> "http://test-service.protected.mdtp/callback",
    "metadata.store" -> "false",
    "metadata.source" -> "source",
    "metadata.timeOfReceipt" -> DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt),
    "metadata.formId" -> "formId",
    "metadata.customerId" -> "customerId",
    "metadata.submissionMark" -> "submissionMark",
    "metadata.casKey" -> "casKey",
    "metadata.classificationType" -> "classificationType",
    "metadata.businessArea" -> "businessArea"
  )

  private val minimalData =
    completeData - "metadata.store" - "metadata.submissionMark" - "metadata.casKey"

  "must return a `SubmissionRequest` when given valid input" in {
    form.bind(completeData).value.value mustEqual completeRequest
  }

  "must return a `SubmissionRequest` when given minimal input" in {
    form.bind(minimalData).value.value mustEqual minimalRequest
  }

  "submissionReference" - {

    "must bind `None` if there is no submissionReference" in {
      form.bind(completeData - "submissionReference").value.value.submissionReference mustBe None
    }

    "must bind `None` if submissionReference is an empty string" in {
      form.bind(completeData + ("submissionReference" -> "")).value.value.submissionReference mustBe None
    }

    "must bind when the submission reference includes hyphens" in {
      form.bind(completeData + ("submissionReference" -> "1234-5678-90AB")).value.value.submissionReference.value mustEqual "1234-5678-90AB"
    }

    "must fail if the value is invalid" in {
      val boundField = form.bind(completeData + ("submissionReference" -> "foobar"))("submissionReference")
      boundField.error.value.message mustEqual "submissionReference.invalid"
    }
  }

  "callbackUrl" - {
    behave like requiredField("callbackUrl")

    "must fail if the value is not a valid url" in {
      val boundField = form.bind(completeData + ("callbackUrl" -> "foobar"))("callbackUrl")
      boundField.error.value.message mustEqual "callbackUrl.invalid"
    }

    "must fail if the domain doesn't end in .mdtp" in {
      val boundField = form.bind(completeData + ("callbackUrl" -> "http://localhost/callback"))("callbackUrl")
      boundField.error.value.message mustEqual "callbackUrl.invalidHost"
    }

    "must succeed when the domain is localhost, when `allow-localhost-callbacks` is enabled" in {
      val configuration = Configuration("allow-localhost-callbacks" -> true)
      val form = new SubmissionFormProvider(configuration).form
      val boundField = form.bind(completeData + ("callbackUrl" -> "http://localhost/callback"))("callbackUrl")
      boundField.hasErrors mustEqual false
    }
  }

  "metadata.store" - {

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.store" -> "foobar"))("metadata.store")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.source" - {
    behave like requiredField("metadata.source")
    behave like nonEmptyField("metadata.source")
    behave like fieldWithMaxLength("metadata.source", 32)
  }

  "metadata.submissionMark" - {
    behave like nonEmptyField("metadata.submissionMark")
    behave like fieldWithMaxLength("metadata.submissionMark", 32)
  }

  "metadata.timeOfReceipt" - {

    "must bind a time with nanos" in {
      val timeOfReceipt = LocalDateTime.of(2020, 2, 1, 12, 30, 20, 1337)
      val boundField = form.bind(completeData + ("metadata.timeOfReceipt" -> DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)))
      boundField.hasErrors mustEqual false
    }

    behave like requiredField("metadata.timeOfReceipt")

    "must fail if it is invalid" in {
      val boundField = form.bind(Map("metadata.timeOfReceipt" -> "foobar"))("metadata.timeOfReceipt")
      boundField.hasErrors mustEqual true
    }
  }

  "metadata.formId" - {
    behave like requiredField("metadata.formId")
    behave like nonEmptyField("metadata.formId")
    behave like fieldWithMaxLength("metadata.formId", 12)
  }

  "metadata.customerId" - {
    behave like requiredField("metadata.customerId")
    behave like nonEmptyField("metadata.customerId")
    behave like fieldWithMaxLength("metadata.customerId", 32)
  }

  "metadata.casKey" - {
    behave like nonEmptyField("metadata.casKey")
    behave like fieldWithMaxLength("metadata.casKey", 65)
  }

  "metadata.classificationType" - {
    behave like requiredField("metadata.classificationType")
    behave like nonEmptyField("metadata.businessArea")
    behave like fieldWithMaxLength("metadata.classificationType", 64)
  }

  "metadata.businessArea" - {
    behave like requiredField("metadata.businessArea")
    behave like nonEmptyField("metadata.businessArea")
    behave like fieldWithMaxLength("metadata.businessArea", 32)
  }

  private def requiredField(key: String)(implicit pos: Position): Unit = {
    "must fail if it isn't provided" in {
      val boundField = form.bind(completeData - key)(key)
      boundField.hasErrors mustEqual true
    }
  }

  private def nonEmptyField(key: String)(implicit pos: Position): Unit = {
    "must fail if it's empty" in {
      val boundField = form.bind(completeData - key + (key -> "   "))(key)
      boundField.hasErrors mustEqual true
    }
  }

  private def fieldWithMaxLength(key: String, maxLength: Int)(implicit pos: Position): Unit = {
    s"must fail if input is longer than $maxLength" in {
      val boundField = form.bind(completeData - key + (key -> "a" * (maxLength + 1)))(key)
      boundField.hasErrors mustEqual true
    }
  }
}
