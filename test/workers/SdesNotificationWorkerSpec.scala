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

package workers

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import fs2.Stream
import fs2.timeseries.TimeStamped
import models.Done
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.Span.convertSpanToDuration
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import services.SdesService
import worker.SdesNotificationWorker

import scala.concurrent.Future

class SdesNotificationWorkerSpec extends AsyncFreeSpec with AsyncIOSpec
  with Matchers with ScalaFutures
  with MockitoSugar with BeforeAndAfterEach
  with OptionValues with EitherValues {

  private val mockSdesService = mock[SdesService]

  private val app = GuiceApplicationBuilder()
    .configure(
      "workers.initial-delay" -> "1 minute",
      "workers.sdes-notification-worker.interval" -> "30 seconds"
    )
    .overrides(
      bind[SdesService].toInstance(mockSdesService)
    )
    .build()

  private val worker = app.injector.instanceOf[SdesNotificationWorker]
  private val stream = withTimestamp(worker.stream)

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSdesService)
  }

  "stream" - {

    "must start streaming after 1 minute, then call the sdes service every 30 seconds" in {

      when(mockSdesService.notifySubmittedItems()).thenReturn(Future.successful(Done))

      TestControl.executeEmbed(stream.take(3).compile.toVector).map { results =>
        val Vector(result1, result2, result3) = results

        result1.time mustEqual convertSpanToDuration(60.seconds)
        result2.time mustEqual convertSpanToDuration(90.seconds)
        result3.time mustEqual convertSpanToDuration(120.seconds)

        verify(mockSdesService, times(3)).notifySubmittedItems()
        succeed
      }
    }

    "failed calls must not prevent the worker from continuing" in {

      when(mockSdesService.notifySubmittedItems())
        .thenReturn(Future.successful(Done))
        .thenReturn(Future.failed(new RuntimeException()))
        .thenReturn(Future.successful(Done))

      TestControl.executeEmbed(stream.take(2).compile.toVector).map { results =>
        val Vector(result1, result2) = results

        result1.time mustEqual convertSpanToDuration(60.seconds)
        result2.time mustEqual convertSpanToDuration(120.seconds)

        verify(mockSdesService, times(3)).notifySubmittedItems()
        succeed
      }
    }
  }

  private def withTimestamp[A](stream: Stream[IO, A]): Stream[IO, TimeStamped[A]] =
    stream.flatMap { value =>
      Stream.eval(IO.monotonic.map(TimeStamped(_, value)))
    }
}
