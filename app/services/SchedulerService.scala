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

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

@Singleton
class SchedulerService @Inject() ()(implicit runtime: IORuntime) {

  private def wait(initialDelay: FiniteDuration): Stream[IO, FiniteDuration] =
    Stream.awakeDelay[IO](initialDelay)

  private def doRepeatedlyStartingNow[A](stream: Stream[IO, A], interval: FiniteDuration): Stream[IO, A] =
    stream ++ (Stream.awakeEvery[IO](interval) >> stream)

  private def buildStream[A](interval: FiniteDuration, initialDelay: FiniteDuration, work: => Future[A]): Stream[IO, Either[Throwable, A]] =
    wait(initialDelay) >> doRepeatedlyStartingNow(Stream.eval(IO.fromFuture(IO(work))), interval).attempt

  def schedule[A](interval: FiniteDuration, initialDelay: FiniteDuration = 0.seconds)(work: => Future[A]): (Future[Unit], () => Future[Unit]) =
    buildStream(interval, initialDelay, work).compile.drain.unsafeToFutureCancelable()
}
