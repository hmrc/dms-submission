package services

import connectors.SdesConnector
import models.Done
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest}
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SdesService @Inject() (
                              connector: SdesConnector,
                              configuration: Configuration
                            ) {

  private val informationType: String = configuration.get[String]("services.sdes.information-type")
  private val recipientOrSender: String = configuration.get[String]("services.sdes.recipient-or-sender")

  def notify(objectSummary: ObjectSummaryWithMd5, correlationId: String)(implicit hc: HeaderCarrier): Future[Done] =
    connector.notify(createRequest(objectSummary, correlationId))

  private def createRequest(objectSummary: ObjectSummaryWithMd5, correlationId: String): FileNotifyRequest =
    FileNotifyRequest(
      informationType = informationType,
      file = FileMetadata(
        recipientOrSender = recipientOrSender,
        name = objectSummary.location.fileName,
        location = objectSummary.location.asUri,
        checksum = FileChecksum("md5", objectSummary.contentMd5.value),
        size = objectSummary.contentLength,
        properties = List.empty
      ),
      audit = FileAudit(correlationId)
    )
}
