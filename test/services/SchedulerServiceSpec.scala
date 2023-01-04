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

package services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.{Future, Promise}

class SchedulerServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures {

  private val app = GuiceApplicationBuilder().build()

  private val service = app.injector.instanceOf[SchedulerService]

  "schedule" - {

    "must repeat the given function repeatedly until cancelled" in {

      val promise: Promise[Unit] = Promise()
      var times = 0
      var cancel: () => Future[Unit] = () => Future.unit

      val result =
        service.schedule[Unit](interval = 10.milliseconds) {
          times += 1
          if (times > 3) {
            cancel()
          } else promise.future
        }

      cancel = result._2
      promise.success(())

      result._1.failed.futureValue
      times mustEqual 4
    }

    "must be resilient to transient failures in the function" in {

      val promise: Promise[Unit] = Promise()
      var times = 0
      var cancel: () => Future[Unit] = () => Future.unit

      val result =
        service.schedule[Unit](interval = 10.milliseconds) {
          times += 1
          if (times == 0) {
            promise.future
          } else if (times > 3) {
            cancel()
          } else if (times % 2 == 0) {
            Future.failed(new RuntimeException())
          } else {
            Future.unit
          }
        }

      cancel = result._2
      promise.success(())

      result._1.failed.futureValue
      times mustEqual 4
    }
  }
}
