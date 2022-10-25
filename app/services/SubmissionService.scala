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
import models.{Done, SubmissionRequest}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionService @Inject() (
                                    objectStoreClient: PlayObjectStoreClient,
                                    fileService: FileService,
                                    sdesService: SdesService
                                  )(implicit ec: ExecutionContext) extends Logging {

  // TODO use separate blocking EC for file stuff
  def submit(request: SubmissionRequest, pdf: File)(implicit hc: HeaderCarrier): Future[Done] = {
    withWorkingDir { workDir =>
      val zip = fileService.createZip(workDir, pdf)
      for {
        objectSummary <- objectStoreClient.putObject(Path.File("file"), zip.path.toFile)
        _             <- sdesService.notify(objectSummary, UUID.randomUUID().toString)
      } yield Done
    }
  }

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
