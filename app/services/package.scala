import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

package object services {

  type OsObject = uk.gov.hmrc.objectstore.client.Object[Source[ByteString, NotUsed]]
}
