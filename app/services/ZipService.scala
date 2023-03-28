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
import cats.data.{EitherNec, EitherT, NonEmptyChain}
import config.FileSystemExecutionContext
import models.{Done, Pdf}
import models.submission.{SubmissionMetadata, SubmissionRequest}
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

  private val readableDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
  private val condensedDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  private val filenameDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  def createZip(workDir: File, pdf: Pdf, request: SubmissionRequest, id: String): Future[EitherNec[String, File]] = {
    val result: EitherT[Future, NonEmptyChain[String], File] = for {
      tmpDir           <- EitherT.liftF(createTmpDir(workDir))
      reconciliationId =  s"$id-${condensedDateFormatter.format(LocalDateTime.ofInstant(request.metadata.timeOfReceipt, ZoneOffset.UTC))}"
      filenamePrefix   =  s"$id-${filenameDateFormatter.format(LocalDateTime.ofInstant(request.metadata.timeOfReceipt, ZoneOffset.UTC))}"
      _                <- EitherT.liftF(copyPdfToZipDir(tmpDir, pdf, filenamePrefix))
      _                <- EitherT.liftF(createMetadatXmlFile(tmpDir, pdf, request, id, reconciliationId, filenamePrefix))
      zip              <- EitherT.liftF(createZip(workDir, tmpDir))
    } yield zip
    result.value
  }

  private def createTmpDir(workDir: File): Future[File] = Future {
    File.newTemporaryDirectory(parent = Some(workDir))
  }

  private def copyPdfToZipDir(tmpDir: File, pdf: Pdf, filenamePrefix: String): Future[Done] = Future {
    pdf.file.copyTo(tmpDir / s"$filenamePrefix-iform.pdf")
    Done
  }

  private def createMetadatXmlFile(tmpDir: File, pdf: Pdf, request: SubmissionRequest, id: String, reconciliationId: String, filenamePrefix: String): Future[Done] = Future {
    val metadataFile = tmpDir / s"$filenamePrefix-metadata.xml"
    XML.save(metadataFile.pathAsString, Utility.trim(createMetadata(request, pdf.numberOfPages, id, reconciliationId)), xmlDecl = true)
    Done
  }

  private def createZip(workDir: File, tmpDir: File): Future[File] = Future {
    val zip = File.newTemporaryFile(parent = Some(workDir))
    tmpDir.zipTo(zip)
  }

  private def createMetadata(request: SubmissionRequest, numberOfPages: Int, id: String, reconciliationId: String): Node =
    <documents xmlns="http://govtalk.gov.uk/hmrc/gis/content/1">
      <document>
        <header>
          <title>{id}</title>
          <format>{format}</format>
          <mime_type>{mimeType}</mime_type>
          <store>{request.metadata.store}</store>
          <source>{request.metadata.source}</source>
          <target>{target}</target>
          <reconciliation_id>{reconciliationId}</reconciliation_id>
        </header>
        <metadata>
          { Seq(
          createAttribute("hmrc_time_of_receipt", "time", readableDateFormatter.format(LocalDateTime.ofInstant(request.metadata.timeOfReceipt, ZoneOffset.UTC))),
          createAttribute("time_xml_created", "time", readableDateFormatter.format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))),
          createAttribute("submission_reference", "string", id),
          createAttribute("form_id", "string", request.metadata.formId),
          createAttribute("number_pages", "integer", numberOfPages.toString),
          createAttribute("source", "string", request.metadata.source),
          createAttribute("customer_id", "string", request.metadata.customerId),
          createAttribute("submission_mark", "string", request.metadata.submissionMark),
          createAttribute("cas_key", "string", request.metadata.casKey),
          createAttribute("classification_type", "string", request.metadata.classificationType),
          createAttribute("business_area", "string", request.metadata.businessArea),
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
