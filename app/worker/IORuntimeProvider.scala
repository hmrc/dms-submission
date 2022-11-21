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

import cats.effect.unsafe.{IORuntime, IORuntimeBuilder}
import config.BlockingExecutionContext

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class IORuntimeProvider @Inject() (
                                    defaultEc: ExecutionContext,
                                    blockingEc: BlockingExecutionContext
                                  ) extends Provider[IORuntime] {

  private val doNothing: () => Unit =
    () => ()

  override def get(): IORuntime = IORuntimeBuilder()
    .setCompute(defaultEc, doNothing)
    .setBlocking(blockingEc, doNothing)
    .build()
}
