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

import connectors.CallbackConnector
import logging.Logging
import models.Done
import models.submission.{QueryResult, SubmissionItemStatus}
import repositories.SubmissionItemRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CallbackService @Inject() (
                                  callbackConnector: CallbackConnector,
                                  repository: SubmissionItemRepository
                                )(implicit ec: ExecutionContext) extends Logging {

  def notifyOldestProcessedItem(): Future[QueryResult] = {
    repository.lockAndReplaceOldestItemByStatus(SubmissionItemStatus.Processed) { item =>
      callbackConnector.notify(item).map { _ =>
        item.copy(status = SubmissionItemStatus.Completed)
      }
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
      callbackConnector.notify(item).map { _ =>
        item.copy(status = SubmissionItemStatus.Completed)
      }
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
}
