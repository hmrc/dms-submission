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

package worker

import logging.Logging
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MetricOrchestratorWorker @Inject()(
                                          configuration: Configuration,
                                          metricOrchestrator: MetricOrchestrator,
                                          lifecycle: ApplicationLifecycle,
                                          actorSystem: ActorSystem
                                        )(implicit ec: ExecutionContext) extends Logging {

  private val scheduler = actorSystem.scheduler

  private val interval = configuration.get[FiniteDuration]("workers.metric-orchestrator-worker.interval")

  private val cancel = scheduler.scheduleWithFixedDelay(0.seconds, interval) { () =>
    metricOrchestrator.attemptMetricRefresh().onComplete {
      case Success(_) => ()
      case Failure(e) => logger.error("Error when updating metrics", e)
    }
  }

  lifecycle.addStopHook(() => Future.successful(cancel.cancel()))
}
