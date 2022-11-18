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

import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import services.{SchedulerService, SdesService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

@Singleton
class SdesNotificationWorker @Inject()(
                                        configuration: Configuration,
                                        sdesService: SdesService,
                                        schedulerService: SchedulerService,
                                        lifecycle: ApplicationLifecycle
                                      ) {

  private val interval = configuration.get[FiniteDuration]("workers.sdes-notification-worker.interval")
  private val initialDelay = configuration.get[FiniteDuration]("workers.initial-delay")

  private val (_, cancel) = schedulerService.schedule(interval, initialDelay)(sdesService.notifySubmittedItems())

  lifecycle.addStopHook(cancel)
}
