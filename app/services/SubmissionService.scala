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

package services

import better.files.File
import config.FileSystemExecutionContext
import models.submission.{ObjectSummary, SubmissionItem, SubmissionItemStatus, SubmissionRequest}
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
                                    submissionItemRepository: SubmissionItemRepository,
                                    clock: Clock,
                                    fileSystemExecutionContext: FileSystemExecutionContext
                                  )(implicit ec: ExecutionContext) extends Logging {

  def submit(request: SubmissionRequest, pdf: File, owner: String)(implicit hc: HeaderCarrier): Future[String] = {
    fileService.withWorkingDirectory { workDir =>
      val id = request.id.getOrElse(UUID.randomUUID().toString)
      val path = Path.Directory(owner).file(id)
      for {
        zip           <- zipService.createZip(workDir, pdf, request.metadata, id)
        objectSummary <- objectStoreClient.putObject(path, zip.path.toFile)
        item          =  createSubmissionItem(request, objectSummary, id, owner)
        _             <- submissionItemRepository.insert(item)
      } yield item.id
    }
  }

  private def createSubmissionItem(request: SubmissionRequest, objectSummary: ObjectSummaryWithMd5, id: String, service: String): SubmissionItem =
    SubmissionItem(
      id = id,
      owner = service,
      callbackUrl = request.callbackUrl,
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = objectSummary.location.asUri,
        contentLength = objectSummary.contentLength,
        contentMd5 = objectSummary.contentMd5.value,
        lastModified = objectSummary.lastModified
      ),
      failureReason = None,
      lastUpdated = clock.instant(),
      sdesCorrelationId = UUID.randomUUID().toString
    )
}
