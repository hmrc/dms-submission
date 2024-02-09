/*
 * Copyright 2024 HM Revenue & Customs
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
import repositories.SubmissionItemRepository
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ItemTimeoutWorker @Inject() (
                                    configuration: Configuration,
                                    repository: SubmissionItemRepository,
                                    lifecycle: ApplicationLifecycle,
                                    actorSystem: ActorSystem,
                                    lockRepository: MongoLockRepository,
                                  )(implicit ec: ExecutionContext) extends Logging {

  private val scheduler = actorSystem.scheduler

  private val interval = configuration.get[FiniteDuration]("workers.item-timeout-worker.interval")
  private val initialDelay = configuration.get[FiniteDuration]("workers.initial-delay")
  private val lockTtl = configuration.get[FiniteDuration]("workers.item-timeout-worker.lock-ttl")

  private val lockService = LockService(
    lockRepository = lockRepository,
    lockId = "item-timeout-worker",
    ttl = lockTtl
  )

  private val cancel = scheduler.scheduleWithFixedDelay(initialDelay, interval) { () =>
    lockService.withLock {
      repository.failTimedOutItems.map { numberOfTimedOutItems =>
        if (numberOfTimedOutItems > 0) {
          logger.warn(s"Timed out $numberOfTimedOutItems items")
        }
      }
    }
  }

  lifecycle.addStopHook(() => Future.successful(cancel.cancel()))
}
