package services

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import better.files.File
import models.submission.Attachment
import uk.gov.hmrc.objectstore.client.Path

import java.security.MessageDigest
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionMarkService @Inject() ()(implicit mat: Materializer, ec: ExecutionContext) {

  private val base64Encoder = Base64.getEncoder

  def generateSubmissionMark(workDir: File, pdf: File, attachments: Seq[Attachment]): Future[String] = {
    val digest = MessageDigest.getInstance("SHA1")
    attachments.sortBy { attachment =>
      Path.File(attachment.location).fileName
    }.foldLeft(FileIO.fromPath(pdf.path)) { (source, attachment) =>
      source ++ FileIO.fromPath((workDir / Path.File(attachment.location).fileName).path)
    }.runForeach(bs => digest.update(bs.asByteBuffer)).map { _ =>
      base64Encoder.encodeToString(digest.digest())
    }
  }
}
