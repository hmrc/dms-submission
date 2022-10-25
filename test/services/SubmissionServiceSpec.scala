package services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import better.files.File
import models.SubmissionRequest
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5, Path}

import java.time.Instant
import scala.concurrent.Future

class SubmissionServiceSpec extends AnyFreeSpec with Matchers
  with ScalaFutures with MockitoSugar with OptionValues with BeforeAndAfterEach
  with IntegrationPatience {

  private val mockObjectStoreClient = mock[PlayObjectStoreClient]
  private val mockFileService = mock[FileService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockObjectStoreClient, mockFileService)
  }

  "submit" - {

    val app = new GuiceApplicationBuilder()
      .overrides(
        bind[PlayObjectStoreClient].toInstance(mockObjectStoreClient),
        bind[FileService].toInstance(mockFileService)
      )
      .build()

    val service = app.injector.instanceOf[SubmissionService]

    val hc: HeaderCarrier = HeaderCarrier()
    val request = SubmissionRequest("")

    val pdf = File.newTemporaryFile()
      .deleteOnExit()
      .write("Hello, World!")
    val zip = File.newTemporaryFile()
      .deleteOnExit()
      .write("Some bytes")

    "must create a zip file of the contents of the request along with a metadata xml for routing, upload to object-store and notify SDES" in {
      val workDir = File.newTemporaryDirectory()
      val objectSummary = ObjectSummaryWithMd5(
        location = Path.File("file"),
        contentLength = 0L,
        contentMd5 = Md5Hash("hash"),
        lastModified = Instant.now()
      )

      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(objectSummary))

      service.submit(request, pdf)(hc).futureValue

      verify(mockObjectStoreClient).putObject(eqTo(Path.File("file")), eqTo(zip.path.toFile), any(), any(), any(), any())(any(), any())

      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when the file service fails to create a work directory" in {
      when(mockFileService.workDir()).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
    }

    "must fail when the fail service fails to create a zip file" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }

    "must fail when object store fails" in {
      val workDir = File.newTemporaryDirectory()
      when(mockFileService.workDir()).thenReturn(workDir)
      when(mockFileService.createZip(workDir, pdf)).thenReturn(zip)
      when(mockObjectStoreClient.putObject[Source[ByteString, _]](any(), any(), any(), any(), any(), any())(any(), any())).thenThrow(new RuntimeException())
      service.submit(request, pdf)(hc).failed.futureValue
      eventually {
        workDir.exists() mustEqual false
      }
    }
  }
}
