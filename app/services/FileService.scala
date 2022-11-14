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
import com.codahale.metrics.{Gauge, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import config.FileSystemExecutionContext
import logging.Logging
import play.api.Configuration
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileService @Inject() (
                              objectStoreClient: PlayObjectStoreClient,
                              configuration: Configuration,
                              clock: Clock,
                              metrics: Metrics
                            )(implicit fileSystemExecutionContext: FileSystemExecutionContext) extends Logging {

  private val tmpDir: File = File(configuration.get[String]("play.temporaryFile.dir"))
    .createDirectories()

  private val metricRegistry: MetricRegistry = metrics.defaultRegistry

  metricRegistry.register("temporary-directory.size", new Gauge[Long] {
    override def getValue: Long = tmpDir.size() / 1000000L
  })

  def withWorkingDirectory[A](f: File => Future[A])(implicit ec: ExecutionContext): Future[A] =
    workDir().flatMap { workDir =>
      val future = Future(f(workDir))(ec).flatten
      future.onComplete { _ =>
        try {
          workDir.delete()
          logger.debug(s"Deleted working dir at ${workDir.pathAsString}")
        } catch { case e: Throwable =>
          logger.error(s"Failed to delete working dir at ${workDir.pathAsString}", e)
        }
      }(fileSystemExecutionContext)
      future
    }

  private def workDir(): Future[File] = Future {
    File.newTemporaryDirectory(parent = Some(tmpDir))
  }
}
