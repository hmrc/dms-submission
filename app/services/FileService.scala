package services

import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import better.files.File
import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class FileService @Inject() (
                              objectStoreClient: PlayObjectStoreClient,
                              configuration: Configuration
                            ) {

  private val tmpDir: File = File(configuration.get[String]("play.temporaryFile.dir"))

  def workDir(): File = File.newTemporaryDirectory(parent = Some(tmpDir))

  def createZip(workDir: File, pdf: File): File = {
    val tmpDir = File.newTemporaryDirectory(parent = Some(workDir))
    pdf.copyTo(tmpDir / "iform.pdf")
    val zip = File.newTemporaryFile(parent = Some(workDir))
    tmpDir.zipTo(zip)
  }
}
