package services

import better.files.File
import models.{Done, SubmissionRequest}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.play.Implicits._
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionService @Inject() (
                                    objectStoreClient: PlayObjectStoreClient,
                                    fileService: FileService,
                                  )(implicit ec: ExecutionContext) extends Logging {

  // TODO use separate blocking EC for file stuff
  def submit(request: SubmissionRequest, pdf: File)(implicit hc: HeaderCarrier): Future[Done] = {
    withWorkingDir { workDir =>
      val zip = fileService.createZip(workDir, pdf)
      objectStoreClient.putObject(Path.File("file"), zip.path.toFile)
        .map(_ => Done)
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
