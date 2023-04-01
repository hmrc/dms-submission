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

package models.submission

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.objectstore.client.Path

final case class Attachment(location: Path.File, contentMd5: String, owner: String)

object Attachment {

  implicit val reads: Reads[Attachment] = (
    (__ \ "location").read[String].map(Path.File(_)) ~
    (__ \ "contentMd5").read[String] ~
    (__ \ "owner").read[String]
  )(Attachment(_, _, _))

  implicit val writes: Writes[Attachment] = (
    (__ \ "location").write[String].contramap[Path.File](_.asUri) ~
    (__ \ "contentMd5").write[String] ~
    (__ \ "owner").write[String]
  )(unlift(Attachment.unapply))
}