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

import logging.Logging
import org.quartz._
import org.quartz.impl.StdSchedulerFactory
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SchedulerProvider @Inject() (
                                    lifecycle: ApplicationLifecycle,
                                    jobFactory: GuiceJobFactory,
                                    configuration: Configuration
                                  )(implicit ec: ExecutionContext) extends Provider[Scheduler] with Logging {

  private val schedulerInitialDelay: Int =
    configuration.get[Duration]("workers.initial-delay")
      .toSeconds.toInt

  private val sdesNotificationInterval: Int =
    configuration.get[Duration]("workers.sdes-notification-worker.interval")
      .toSeconds.toInt

  private val processedItemInterval: Int =
    configuration.get[Duration]("workers.processed-item-worker.interval")
      .toSeconds.toInt

  private val failedItemInterval: Int =
    configuration.get[Duration]("workers.failed-item-worker.interval")
      .toSeconds.toInt

  private val factory: SchedulerFactory = new StdSchedulerFactory()
  private val scheduler: Scheduler = factory.getScheduler()

  /*
   * Set the job factory to the `GuiceJobFactory` to allow job instances
   * to be created by Guice, allowing us to inject dependencies into jobs
   */
  scheduler.setJobFactory(jobFactory)

  /*
   * This needs to be here as the scheduler uses some static state.
   * Since we create multiple applications in a test run, this causes
   * errors if we try to add the jobs repeatedly
   */
  scheduler.clear()

  /*
   * SDES Notification Worker
   */
  private val sdesNotificationJob = JobBuilder.newJob(classOf[SdesNotificationJob])
    .withIdentity("sdesNotificationJob")
    .build()
  private val sdesNotificationJobTrigger = TriggerBuilder.newTrigger()
    .withIdentity("sdesNotificationJobTrigger")
    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(sdesNotificationInterval))
    .startNow()
    .build()
  logger.info("Scheduling SDES Notifications")
  scheduler.scheduleJob(sdesNotificationJob, sdesNotificationJobTrigger)

  /*
   * Processed Item Worker
   */
  private val processedItemJob = JobBuilder.newJob(classOf[ProcessedItemJob])
    .withIdentity("processedItemJob")
    .build()
  private val processedItemJobTrigger = TriggerBuilder.newTrigger()
    .withIdentity("processedItemJobTrigger")
    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(processedItemInterval))
    .startNow()
    .build()
  logger.info("Scheduling Processed Item job")
  scheduler.scheduleJob(processedItemJob, processedItemJobTrigger)

  /*
   * Failed Item Worker
   */
  private val failedItemJob = JobBuilder.newJob(classOf[FailedItemJob])
    .withIdentity("failedItemJob")
    .build()
  private val failedItemJobTrigger = TriggerBuilder.newTrigger()
    .withIdentity("failedItemJobTrigger")
    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(failedItemInterval))
    .startNow()
    .build()
  logger.info("Scheduling Failed Item job")
  scheduler.scheduleJob(failedItemJob, failedItemJobTrigger)

  logger.info(s"Starting scheduler in $schedulerInitialDelay seconds")
  scheduler.startDelayed(schedulerInitialDelay)

  lifecycle.addStopHook { () =>
    Future {
      logger.info("Shutting down scheduler")
      scheduler.shutdown(true)
      logger.info("Scheduler shut down")
    }
  }

  override def get(): Scheduler = scheduler
}
