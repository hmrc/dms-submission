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

package services

import better.files.File
import config.FileSystemExecutionContext
import models.Pdf
import models.submission.SubmissionMetadata
import play.api.Configuration

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.xml.{Node, Utility, XML}

@Singleton
class ZipService @Inject() (
                             clock: Clock,
                             configuration: Configuration
                           )(implicit ec: FileSystemExecutionContext) {

  private val format: String = configuration.get[String]("metadata.format")
  private val mimeType: String = configuration.get[String]("metadata.mimeType")
  private val target: String = configuration.get[String]("metadata.target")

  private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

  def createZip(workDir: File, pdf: Pdf, metadata: SubmissionMetadata, correlationId: String): Future[File] = Future {
    val tmpDir = File.newTemporaryDirectory(parent = Some(workDir))
    pdf.file.copyTo(tmpDir / s"${correlationId}iform.pdf")
    val metadataFile = tmpDir / s"${correlationId}metadata.xml"
    XML.save(metadataFile.pathAsString, Utility.trim(createMetadata(metadata, pdf.numberOfPages, correlationId)), xmlDecl = true)
    val zip = File.newTemporaryFile(parent = Some(workDir))
    tmpDir.zipTo(zip)
  }

  private def createMetadata(metadata: SubmissionMetadata, numberOfPages: Int, correlationId: String): Node =
    <documents xmlns="http://govtalk.gov.uk/hmrc/gis/content/1">
      <document>
        <header>
          <title>{correlationId}</title>
          <format>{format}</format>
          <mime_type>{mimeType}</mime_type>
          <store>{metadata.store}</store>
          <source>{metadata.source}</source>
          <target>{target}</target>
          <reconciliation_id>{correlationId}</reconciliation_id>
        </header>
        <metadata>
          { Seq(
          createAttribute("hmrc_time_of_receipt", "time", dateTimeFormatter.format(LocalDateTime.ofInstant(metadata.timeOfReceipt, ZoneOffset.UTC))),
          createAttribute("time_xml_created", "time", dateTimeFormatter.format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))),
          createAttribute("submission_reference", "string", correlationId),
          createAttribute("form_id", "string", metadata.formId),
          createAttribute("number_pages", "integer", numberOfPages.toString),
          createAttribute("source", "string", metadata.source),
          createAttribute("customer_id", "string", metadata.customerId),
          createAttribute("submission_mark", "string", metadata.submissionMark),
          createAttribute("cas_key", "string", metadata.casKey),
          createAttribute("classification_type", "string", metadata.classificationType),
          createAttribute("business_area", "string", metadata.businessArea),
          createAttribute("attachment_count", "int", "0")
        ) }
        </metadata>
      </document>
    </documents>

  private def createAttribute(attributeName: String, attributeType: String, attributeValue: String): Node =
    <attribute>
      <attribute_name>{attributeName}</attribute_name>
      <attribute_type>{attributeType}</attribute_type>
      <attribute_values>
        <attribute_value>{attributeValue}</attribute_value>
      </attribute_values>
    </attribute>
}
