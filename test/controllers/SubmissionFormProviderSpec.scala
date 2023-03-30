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

import models.submission.{Attachment, SubmissionMetadata, SubmissionRequest}
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}

class SubmissionFormProviderSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val configuration = Configuration("allow-localhost-callbacks" -> false)
  private val form = new SubmissionFormProvider(configuration).form("owner")

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
      submissionMark = "submissionMark",
      casKey = "casKey",
      classificationType = "classificationType",
      businessArea = "businessArea"
    ),
    attachments = Seq(
      Attachment(
        location = "foo/bar.pdf",
        contentMd5 = "OFj2IjCsPJFfMAxmQxLGPw==",
        owner = "owner"
      ),
      Attachment(
        location = "foo/baz.pdf",
        contentMd5 = "lpSKrT/K6AwIo1ybWVjNiQ==",
        owner = "owner2"
      )
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
    "metadata.businessArea" -> "businessArea",
    "attachments[0].location" -> "foo/bar.pdf",
    "attachments[0].contentMd5" -> "OFj2IjCsPJFfMAxmQxLGPw==",
    "attachments[1].location" -> "foo/baz.pdf",
    "attachments[1].contentMd5" -> "lpSKrT/K6AwIo1ybWVjNiQ==",
    "attachments[1].owner" -> "owner2"
  )

  "must return a `SubmissionRequest` when given valid input" in {
    form.bind(completeData).value.value mustEqual completeRequest
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
      val form = new SubmissionFormProvider(configuration).form("owner")
      val boundField = form.bind(completeData + ("callbackUrl" -> "http://localhost/callback"))("callbackUrl")
      boundField.hasErrors mustEqual false
    }
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
  }

  "metadata.customerId" - {
    behave like requiredField("metadata.customerId")
    behave like nonEmptyField("metadata.customerId")
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
    behave like nonEmptyField("metadata.businessArea")
  }

  "attachments.location" - {
    behave like requiredField("attachments[0].location")
    behave like nonEmptyField("attachments[0].location")
  }

  "attachments.contentMd5" - {

    "must fail if it isn't a valid base64 string" in {
      val boundField = form.bind(Map("attachments[0].contentMd5" -> "!!"))("attachments[0].contentMd5")
      boundField.hasErrors mustEqual true
      boundField.error.value.message mustEqual "attachments.contentMd5.invalid"
    }

    behave like requiredField("attachments[0].contentMd5")
  }

  "attachments" - {

    "must fail if there are more than 5 attachments" in {

      val data = (0 to 5).foldLeft(Map.empty[String, String]) { (m, i) =>
        m ++ Map(
          s"attachments[$i].location" -> s"$i.pdf",
          s"attachments[$i].contentMd5" -> "OFj2IjCsPJFfMAxmQxLGPw=="
        )
      }

      val boundField = form.bind(data)("attachments")
      boundField.hasErrors mustEqual true
      boundField.error.value.message mustEqual "attachments.max"
    }

    "must fail if there are duplicate file names" in {

      val data = Map(
        "attachments[0].location" -> "file.pdf",
        "attachments[0].contentMd5" -> "OFj2IjCsPJFfMAxmQxLGPw==",
        "attachments[1].location" -> "file.pdf",
        "attachments[1].contentMd5" -> "OFj2IjCsPJFfMAxmQxLGPw=="
      )

      val boundField = form.bind(data)("attachments")
      boundField.hasErrors mustEqual true
      boundField.error.value.message mustEqual "attachments.duplicateFilenames"
    }
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
}
