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

import cats.effect.unsafe.IORuntime

import javax.inject.{Inject, Singleton}

@Singleton
class WorkerOrchestrator @Inject() (
                                     sdesNotificationWorker: SdesNotificationWorker,
                                     processedItemWorker: ProcessedItemWorker,
                                     failedItemWorker: FailedItemWorker,
                                     metricOrchestratorWorker: MetricOrchestratorWorker
                                   )(implicit runtime: IORuntime) {

  sdesNotificationWorker.stream.compile.drain.unsafeToFuture()
  processedItemWorker.stream.compile.drain.unsafeToFuture()
  failedItemWorker.stream.compile.drain.unsafeToFuture()
  metricOrchestratorWorker.stream.compile.drain.unsafeToFuture()
}
