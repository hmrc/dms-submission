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
import models.Done
import play.api.Configuration
import services.CallbackService

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

@Singleton
class ProcessedItemWorker @Inject() (
                                         configuration: Configuration,
                                         service: CallbackService
                                       ) extends Worker {

  private val interval: FiniteDuration =
    configuration.get[FiniteDuration]("workers.processed-item-worker.interval")

  private val initialDelay: FiniteDuration =
    configuration.get[FiniteDuration]("workers.initial-delay")

  private val notifyProcessedItems: Stream[IO, Done] = {
    debug("Starting job") >> Stream.eval(IO.fromFuture(IO(service.notifyProcessedItems()))).attempt.flatMap {
      case Right(_) => debug("Job completed") >> Stream.emit(Done)
      case Left(e)  => error("Job failed", e) >> Stream.empty
    }
  }

  val stream: Stream[IO, Done] =
    wait(initialDelay) >> doRepeatedlyStartingNow(notifyProcessedItems, interval)
}
