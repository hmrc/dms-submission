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
                                    submissionItemRepository: SubmissionItemRepository,
                                    sdesService: SdesService,
                                    clock: Clock
                                  )(implicit ec: ExecutionContext) extends Logging {

  // TODO use separate blocking EC for file stuff
  def submit(request: SubmissionRequest, pdf: File)(implicit hc: HeaderCarrier): Future[String] = {
    withWorkingDir { workDir =>
      val correlationId = UUID.randomUUID().toString
      val zip = fileService.createZip(workDir, pdf, request.metadata, correlationId)
      for {
        objectSummary <- objectStoreClient.putObject(Path.File("file"), zip.path.toFile)
        item          =  createSubmissionItem(request, objectSummary, correlationId)
        _             <- submissionItemRepository.insert(item)
        _             <- sdesService.notify(objectSummary, item.correlationId)
      } yield item.correlationId
    }
  }

  private def createSubmissionItem(request: SubmissionRequest, objectSummary: ObjectSummaryWithMd5, correlationId: String): SubmissionItem =
    SubmissionItem(
      correlationId = correlationId,
      callbackUrl = request.callbackUrl,
      status = SubmissionItemStatus.Submitted,
      objectSummary = ObjectSummary(
        location = objectSummary.location.asUri,
        contentLength = objectSummary.contentLength,
        contentMd5 = objectSummary.contentMd5.value,
        lastModified = objectSummary.lastModified
      ),
      failureReason = None,
      lastUpdated = clock.instant()
    )

  private def withWorkingDir[A](f: File => Future[A]): Future[A] = {
    Future(fileService.workDir()).flatMap { workDir =>
      val future = Future(f(workDir)).flatten
      future.onComplete { _ =>
        try {
          workDir.delete()
          logger.debug(s"Deleted working dir at ${workDir.pathAsString}")
        } catch { case e: Throwable =>
          logger.error(s"Failed to delete working dir at ${workDir.pathAsString}", e)
        }
      }
      future
    }
  }
}
