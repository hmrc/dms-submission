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

package config

import play.api.inject.Binding
import play.api.{Configuration, Environment}
import services.FileService
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import worker.{FailedItemWorker, ItemTimeoutWorker, MetricOrchestratorWorker, ProcessedItemWorker, SdesNotificationWorker}

import java.time.Clock

class Module extends play.api.inject.Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {

    val authTokenInitialiserBindings: Seq[Binding[_]] =
      if (configuration.get[Boolean]("create-internal-auth-token-on-start")) {
        Seq(bind[InternalAuthTokenInitialiser].to[InternalAuthTokenInitialiserImpl].eagerly())
      } else Seq(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser].eagerly())

    val metricOrchestratorWorkerBindings: Seq[Binding[_]] =
      if (configuration.get[Boolean]("metrics.enabled")) {
        Seq(bind[MetricOrchestratorWorker].toSelf.eagerly())
      } else Seq.empty

    Seq(
      bind[Clock].toInstance(Clock.systemUTC()),
      bind[FileService].toSelf.eagerly(),
      bind[MetricOrchestrator].toProvider[MetricOrchestratorProvider].eagerly(),
      bind[ProcessedItemWorker].toSelf.eagerly(),
      bind[FailedItemWorker].toSelf.eagerly(),
      bind[SdesNotificationWorker].toSelf.eagerly(),
      bind[ItemTimeoutWorker].toSelf.eagerly()
    ) ++ authTokenInitialiserBindings ++ metricOrchestratorWorkerBindings
  }
}
