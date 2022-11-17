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

package worker

import cats.effect.IO
import fs2.Stream
import logging.Logging

import scala.concurrent.duration.FiniteDuration

abstract class Worker extends Logging {

  protected def debug(message: String): Stream[IO, Unit] =
    Stream.eval(IO(logger.debug(message)))

  protected def error(message: String, e: Throwable): Stream[IO, Unit] =
    Stream.eval(IO(logger.error(message, e)))

  protected def doRepeatedlyStartingNow[A](stream: Stream[IO, A], interval: FiniteDuration): Stream[IO, A] =
    stream ++ (Stream.awakeEvery[IO](interval) >> stream)

  protected def wait(duration: FiniteDuration): Stream[IO, FiniteDuration] =
    debug(s"Waiting for $duration") >> Stream.awakeDelay[IO](duration)
}
