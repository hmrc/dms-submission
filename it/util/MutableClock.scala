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

package util

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}

final class MutableClock(
                    private var now: Instant,
                    zone: ZoneId
                  ) extends Clock {

  override def getZone: ZoneId = zone

  override def withZone(zone: ZoneId): Clock = new MutableClock(now, zone)

  override def instant(): Instant = now

  def set(instant: Instant): Unit =
    this.now = instant

  def advance(duration: Duration): Unit =
    set(now.plus(duration))
}

object MutableClock {

  def apply(now: Instant, zone: ZoneId = ZoneOffset.UTC): MutableClock =
    new MutableClock(now, zone)
}
