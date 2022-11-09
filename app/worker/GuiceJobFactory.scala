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

import org.quartz.{Job, Scheduler}
import org.quartz.spi.{JobFactory, TriggerFiredBundle}
import play.api.inject.Injector

import javax.inject.{Singleton, Inject}

@Singleton
class GuiceJobFactory @Inject() (
                                  injector: Injector
                                ) extends JobFactory {

  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job = {
    val detail = bundle.getJobDetail
    val clazz = detail.getJobClass
    injector.instanceOf(clazz)
  }
}
