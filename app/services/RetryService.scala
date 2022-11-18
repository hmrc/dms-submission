package services

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import play.api.Configuration

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RetryService @Inject() (
                               configuration: Configuration
                             )(implicit runtime: IORuntime, ec: ExecutionContext) {

  private val defaultDelay: FiniteDuration =
    configuration.get[FiniteDuration]("retry.delay")

  private val defaultMaxAttempts: Int =
    configuration.get[Int]("retry.max-attempts")

  def retry[A](f: => Future[A], delay: FiniteDuration = defaultDelay, maxAttempts: Int = defaultMaxAttempts): Future[A] =
    Stream.retry[IO, A](IO.fromFuture(IO(f)), delay, identity, maxAttempts)
      .compile.lastOrError.unsafeToFuture()
}
