package services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Future

class RetryServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures {

  private val app = GuiceApplicationBuilder()
    .configure(
      "retry.delay" -> "10 milliseconds",
      "retry.max-attempts" -> 3
    )
    .build()

  private val service = app.injector.instanceOf[RetryService]

  "retry" - {

    "must return successfully when the future is successful" in {
      service.retry(Future.successful("foobar")).futureValue mustEqual "foobar"
    }

    "must return successfully when the future returns after an initial failure" in {

      var times = 0

      def future: Future[String] = {
        times += 1
        if (times < 2) {
          Future.failed(new RuntimeException())
        } else {
          Future.successful("foobar")
        }
      }

      service.retry(future).futureValue mustEqual "foobar"
      times mustBe 2
    }

    "must fail when the future fails consistently" in {

      var times = 0

      def future: Future[String] = {
        times += 1
        Future.failed(new RuntimeException())
      }

      service.retry(future).failed.futureValue
      times mustBe 3
    }
  }
}
