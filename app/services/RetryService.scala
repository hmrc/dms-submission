/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import play.api.Configuration
import services.RetryService.BackoffStrategy

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

@Singleton
class RetryService @Inject() (
                               configuration: Configuration
                             )(implicit runtime: IORuntime) {

  private val defaultDelay: FiniteDuration =
    configuration.get[FiniteDuration]("retry.delay")

  private val defaultMaxAttempts: Int =
    configuration.get[Int]("retry.max-attempts")

  def retry[A](
                f: => Future[A],
                delay: FiniteDuration = defaultDelay,
                backoff: FiniteDuration => FiniteDuration = BackoffStrategy.constant,
                maxAttempts: Int = defaultMaxAttempts,
                retriable: Throwable => Boolean = NonFatal.apply
              ): Future[A] =
    Stream.retry[IO, A](IO.fromFuture(IO(f)), delay, backoff, maxAttempts, retriable)
      .compile.lastOrError.unsafeToFuture()
}

object RetryService {

  object BackoffStrategy {
    val constant: FiniteDuration => FiniteDuration = identity
    val exponential: FiniteDuration => FiniteDuration = _ * 2
  }
}