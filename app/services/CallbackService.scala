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

package services

import com.codahale.metrics.{MetricRegistry, Timer}
import com.kenshoo.play.metrics.Metrics
import connectors.CallbackConnector
import logging.Logging
import models.Done
import models.submission.{QueryResult, SubmissionItem, SubmissionItemStatus}
import repositories.SubmissionItemRepository

import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class CallbackService @Inject() (
                                  callbackConnector: CallbackConnector,
                                  repository: SubmissionItemRepository,
                                  clock: Clock,
                                  metrics: Metrics
                                )(implicit ec: ExecutionContext) extends Logging {

  private val metricRegistry: MetricRegistry = metrics.defaultRegistry

  private val timer: Timer = metricRegistry.timer("submission.timer")

  def notifyOldestProcessedItem(): Future[QueryResult] = {
    repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Processed) { item =>
      val result = callbackConnector.notify(item).map { _ =>
        item.copy(status = SubmissionItemStatus.Completed)
      }
      updateTimerForItem(item, result)
    }.recover { case e =>
      logger.error("Error notifying the callback url for an item", e)
      QueryResult.Found
    }
  }

  def notifyProcessedItems(): Future[Done] =
    notifyOldestProcessedItem().flatMap {
      case QueryResult.Found    => notifyProcessedItems()
      case QueryResult.NotFound => Future.successful(Done)
    }

  def notifyOldestFailedItem(): Future[QueryResult] = {
    repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Failed) { item =>
      val result = callbackConnector.notify(item).map { _ =>
        item.copy(status = SubmissionItemStatus.Completed)
      }
      updateTimerForItem(item, result)
    }.recover { case e =>
      logger.error("Error notifying the callback url for an item", e)
      QueryResult.Found
    }
  }

  def notifyFailedItems(): Future[Done] =
    notifyOldestFailedItem().flatMap {
      case QueryResult.Found    => notifyFailedItems()
      case QueryResult.NotFound => Future.successful(Done)
    }

  private def updateTimerForItem(item: SubmissionItem, future: Future[SubmissionItem]): Future[SubmissionItem] = {
    future.onComplete {
      case Success(_) =>
        timer.update(Duration.between(item.created, clock.instant()))
      case _          => ()
    }
    future
  }
}
