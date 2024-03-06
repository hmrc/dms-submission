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
import services.SdesService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

@Singleton
class SdesNotificationWorker @Inject()(
                                        configuration: Configuration,
                                        sdesService: SdesService,
                                        lifecycle: ApplicationLifecycle,
                                        actorSystem: ActorSystem
                                      )(implicit ec: ExecutionContext) extends Logging {

  private val scheduler = actorSystem.scheduler

  private val interval = configuration.get[FiniteDuration]("workers.sdes-notification-worker.interval")
  private val initialDelay = configuration.get[FiniteDuration]("workers.sdes-notification-worker.initial-delay")
  private val enabled = configuration.get[Boolean]("workers.sdes-notification-worker.enabled")

  if (enabled) {

    logger.info("Starting SdesNotificationWorker")
    val cancel = scheduler.scheduleWithFixedDelay(initialDelay, interval) { () =>
      sdesService.notifySubmittedItems().onComplete {
        case Success(_) => ()
        case Failure(e) => logger.error("Error when sending SDES notifications", e)
      }
    }

    lifecycle.addStopHook(() => Future.successful(cancel.cancel()))
  } else {
    logger.info("SdesNotificationWorker disabled")
  }
}
