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
import models.Done
import org.quartz.{Job, JobExecutionContext}
import services.CallbackService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class ProcessedItemJob @Inject() (
                                   service: CallbackService
                                 )(implicit ec: ExecutionContext) extends Job with Logging {

  override def execute(context: JobExecutionContext): Unit = {
    logger.info("Starting job")
    service.notifyProcessedItems().onComplete {
      case Success(Done) => logger.info("Job completed")
      case Failure(e)    => logger.error("Job failed", e)
    }
  }
}
