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
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import fs2.Stream
import models.Done
import play.api.Configuration

import javax.inject.{Singleton, Inject}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

@Singleton
class MetricOrchestratorWorker @Inject() (
                                           configuration: Configuration,
                                           orchestrator: MetricOrchestrator
                                         )(implicit ec: ExecutionContext) extends Worker {

  private val interval: FiniteDuration =
    configuration.get[FiniteDuration]("workers.metric-orchestrator-worker.interval")

  private val attemptRefresh =
    debug("Starting job") >> Stream.eval(IO.fromFuture(IO(orchestrator.attemptMetricRefresh().map(_.log())))).attempt.flatMap {
      case Right(_) => debug("Job completed") >> Stream.emit(Done)
      case Left(e) => error("Error updating orchestrated metrics", e) >> Stream.empty
    }

  val stream: Stream[IO, Done] =
    doRepeatedlyStartingNow(attemptRefresh, interval)
}
