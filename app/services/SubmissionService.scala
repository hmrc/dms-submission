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

import audit.{AuditService, SubmitRequestEvent}
import better.files.File
import cats.data.{EitherT, NonEmptyChain}
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus, SubmissionRequest}
import models.{Done, Pdf}
import play.api.Logging
import repositories.SubmissionItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{ObjectSummaryWithMd5, Path}

import java.time.Clock
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionService @Inject() (
                                    objectStoreClient: PlayObjectStoreClient,
                                    fileService: FileService,
                                    zipService: ZipService,
                                    auditService: AuditService,
                                    submissionItemRepository: SubmissionItemRepository,
                                    clock: Clock,
                                    submissionReferenceService: SubmissionReferenceService,
                                    uuidService: UuidService
                                  )(implicit ec: ExecutionContext) extends Logging {


  def submit(request: SubmissionRequest, pdf: Pdf, owner: String)(implicit hc: HeaderCarrier): Future[Either[NonEmptyChain[String], String]] =
    fileService.withWorkingDirectory { workDir =>
      val id = request.submissionReference.getOrElse(submissionReferenceService.random())
      val correlationId = uuidService.random()
      val path = Path.Directory(s"sdes/$owner").file(s"$correlationId.zip")
      val result: EitherT[Future, NonEmptyChain[String], String] = for {
        zip           <- EitherT(zipService.createZip(workDir, pdf, request, id))
        objectSummary <- EitherT.liftF(objectStoreClient.putObject(path, zip.path.toFile))
        item          =  createSubmissionItem(request, objectSummary, id, owner, correlationId)
        _             <- EitherT.liftF(auditRequest(request, item))
        _             <- EitherT.liftF(submissionItemRepository.insert(item))
      } yield item.id
      result.value
    }

  private def createSubmissionItem(request: SubmissionRequest, objectSummary: ObjectSummaryWithMd5, id: String, owner: String, correlationId: String): SubmissionItem =
    SubmissionItem(
      id = id,
      owner = owner,
      callbackUrl = request.callbackUrl,
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = objectSummary.location.asUri,
        contentLength = objectSummary.contentLength,
        contentMd5 = objectSummary.contentMd5.value,
        lastModified = objectSummary.lastModified
      ),
      failureReason = None,
      created = clock.instant(),
      lastUpdated = clock.instant(),
      sdesCorrelationId = correlationId
    )

  private def auditRequest(request: SubmissionRequest, item: SubmissionItem)(implicit hc: HeaderCarrier): Future[Done] =
    Future.successful {
      val event = SubmitRequestEvent(
        id = item.id,
        owner = item.owner,
        sdesCorrelationId = item.sdesCorrelationId,
        customerId = request.metadata.customerId,
        formId = request.metadata.formId,
        classificationType = request.metadata.classificationType,
        businessArea = request.metadata.businessArea,
        hash = item.objectSummary.contentMd5
      )
      auditService.auditSubmitRequest(event)
      Done
    }
}
