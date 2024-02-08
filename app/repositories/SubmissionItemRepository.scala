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

package repositories

import models.submission.{QueryResult, SubmissionItem, SubmissionItemStatus}
import models.{DailySummary, Done, ListResult}
import org.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.{Clock, Duration, LocalDate, ZoneOffset}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionItemRepository @Inject() (
                                           mongoComponent: MongoComponent,
                                           clock: Clock,
                                           configuration: Configuration
                                         )(implicit ec: ExecutionContext
  ) extends PlayMongoRepository[SubmissionItem](
  collectionName = "submissions",
  mongoComponent = mongoComponent,
  domainFormat = SubmissionItem.format,
  indexes = Seq(
    IndexModel(
      Indexes.ascending("created"),
      IndexOptions()
        .name("createdIdx")
        .expireAfter(30, TimeUnit.DAYS)
    ),
    IndexModel(
      Indexes.compoundIndex(
        Indexes.ascending("owner"),
        Indexes.ascending("id")
      ),
      IndexOptions()
        .name("ownerIdIdx")
        .unique(true)
    ),
    IndexModel(
      Indexes.ascending("sdesCorrelationId"),
      IndexOptions()
        .name("sdesCorrelationIdIdx")
        .unique(true)
    ),
    IndexModel(
      Indexes.ascending("status"),
      IndexOptions()
        .name("statusIdx")
    ),
    IndexModel(
      Indexes.ascending("owner"),
      IndexOptions()
        .name("ownerIdx")
    )
  ),
  extraCodecs =
    Codecs.playFormatSumCodecs(SubmissionItemStatus.format) ++
    Seq(
      Codecs.playFormatCodec(ListResult.format),
      Codecs.playFormatCodec(DailySummary.mongoFormat)
    )
  ) {

  private val lockTtl: Duration = Duration.ofSeconds(configuration.get[Int]("lock-ttl"))

  def insert(item: SubmissionItem): Future[Done] =
    Mdc.preservingMdc {
      collection.insertOne(item.copy(lastUpdated = clock.instant()))
        .toFuture()
        .map(_ => Done)
    }

  def update(owner: String, id: String, status: SubmissionItemStatus, failureReason: Option[String]): Future[SubmissionItem] =
    update(Filters.and(
      Filters.equal("id", id),
      Filters.equal("owner", owner)
    ), status, failureReason)

  def update(sdesCorrelationId: String, status: SubmissionItemStatus, failureReason: Option[String]): Future[SubmissionItem] =
    update(Filters.equal("sdesCorrelationId", sdesCorrelationId), status, failureReason)

  private def update(filter: Bson, status: SubmissionItemStatus, failureReason: Option[String]): Future[SubmissionItem] = {

    val updates = List(
      Updates.set("lastUpdated", clock.instant()),
      Updates.set("status", status),
      failureReason.map(Updates.set("failureReason", _))
        .getOrElse(Updates.unset("failureReason"))
    )

    Mdc.preservingMdc {
      collection.findOneAndUpdate(
        filter = filter,
        update = Updates.combine(updates: _*),
        options = FindOneAndUpdateOptions()
          .returnDocument(ReturnDocument.AFTER)
          .upsert(false)
      ).headOption().flatMap {
        _.map(Future.successful)
          .getOrElse(Future.failed(SubmissionItemRepository.NothingToUpdateException))
      }
    }
  }

  def remove(owner: String, id: String): Future[Done] =
    Mdc.preservingMdc {
      collection.findOneAndDelete(Filters.and(
        Filters.equal("id", id),
        Filters.equal("owner", owner)
      )).toFuture().map(_ => Done)
    }

  def get(owner: String, id: String): Future[Option[SubmissionItem]] =
    Mdc.preservingMdc {
      collection.find(Filters.and(
        Filters.equal("id", id),
        Filters.equal("owner", owner)
      )).headOption()
    }

  def get(sdesCorrelationId: String): Future[Option[SubmissionItem]] =
    Mdc.preservingMdc {
      collection.find(Filters.equal("sdesCorrelationId", sdesCorrelationId))
        .headOption()
    }

  def list(owner: String,
           status: Option[SubmissionItemStatus] = None,
           created: Option[LocalDate] = None,
           limit: Int = 50,
           offset: Int = 0
          ): Future[ListResult] = {

    val ownerFilter = Filters.equal("owner", owner)
    val statusFilter = status.toList.map(Filters.equal("status", _))
    val createdFilter = created.toList.flatMap { date =>
      List(
        Filters.gte("created", date.atStartOfDay(ZoneOffset.UTC).toInstant),
        Filters.lt("created", date.atStartOfDay(ZoneOffset.UTC).plusDays(1).toInstant)
      )
    }
    val filters = Filters.and(List(List(ownerFilter), statusFilter, createdFilter).flatten: _*)

    val findCount =
      Json.obj(
        "$ifNull" -> Json.arr(
          Json.obj(
            "$let" -> Json.obj(
              "vars" -> Json.obj(
                "countValue" -> Json.obj(
                  "$arrayElemAt" -> Json.arr("$totalCount", 0)
                )
              ),
              "in" -> "$$countValue.count"
            )
          ), 0
        )
      )

    Mdc.preservingMdc {
      collection.aggregate[ListResult](List(
        Aggregates.`match`(filters),
        Aggregates.sort(Sorts.descending("created")),
        Aggregates.facet(
          Facet("totalCount", Aggregates.count()),
          Facet("summaries", Aggregates.skip(offset), Aggregates.limit(limit))
        ),
        Aggregates.project(Json.obj(
          "totalCount" -> findCount,
          "summaries" -> "$summaries"
        ).toDocument())
      )).head()
    }
  }

  def countByStatus(status: SubmissionItemStatus): Future[Long] =
    Mdc.preservingMdc {
      collection.countDocuments(Filters.equal("status", status)).toFuture()
    }

  def lockAndReplaceOldestItemByStatus(status: SubmissionItemStatus)(f: SubmissionItem => Future[SubmissionItem]): Future[QueryResult] =
    lockAndReplace(
      filter = Filters.equal("status", status),
      sort = Sorts.ascending("lastUpdated")
    )(f)

  private def lockAndReplace(filter: Bson, sort: Bson)(f: SubmissionItem => Future[SubmissionItem]): Future[QueryResult] = {
    Mdc.preservingMdc(collection.findOneAndUpdate(
      filter = Filters.and(
        filter,
        Filters.or(
          Filters.exists("lockedAt", exists = false),
          Filters.lt("lockedAt", clock.instant().minus(lockTtl))
        )
      ),
      update = Updates.set("lockedAt", clock.instant()),
      options = FindOneAndUpdateOptions().sort(sort)
    ).headOption()).flatMap {
      _.map { item =>
        f(item)
          .flatMap { updatedItem =>
            Mdc.preservingMdc(collection.replaceOne(
              filter = Filters.equal("sdesCorrelationId", item.sdesCorrelationId),
              replacement = updatedItem.copy(
                lastUpdated = clock.instant(),
                lockedAt = None
              )
            ).toFuture())
          }
          .map(_ => QueryResult.Found)
      }.getOrElse(Future.successful(QueryResult.NotFound))
    }
  }

  def dailySummaries(owner: String): Future[Seq[DailySummary]] = {

    import SubmissionItemStatus._

    @annotation.nowarn("msg=possible missing interpolator")
    def countStatus(status: SubmissionItemStatus): JsObject = Json.obj(
      "$sum" -> Json.obj(
        "$cond" -> Json.obj(
          "if" -> Json.obj(
            "$eq" -> Json.arr("$status", status)
          ),
          "then" -> 1,
          "else" -> 0
        )
      )
    )

    val groupExpression = Json.obj(
      "$group" -> Json.obj(
        "_id" -> Json.obj(
          "$dateToString" -> Json.obj(
            "format" -> "%Y-%m-%d",
            "date" -> "$created"
          )
        ),
        Submitted.toString.toLowerCase -> countStatus(Submitted),
        Forwarded.toString.toLowerCase -> countStatus(Forwarded),
        Processed.toString.toLowerCase -> countStatus(Processed),
        Failed.toString.toLowerCase    -> countStatus(Failed),
        Completed.toString.toLowerCase -> countStatus(Completed)
      )
    )

    Mdc.preservingMdc {
      collection.aggregate[DailySummary](List(
        Aggregates.`match`(Filters.eq("owner", owner)),
        groupExpression.toDocument()
      )).toFuture()
    }
  }

  def owners: Future[Set[String]] =
    Mdc.preservingMdc {
      collection.distinct[String]("owner").toFuture().map(_.toSet)
    }
}

object SubmissionItemRepository {

  case object NothingToUpdateException extends Exception {
    override def getMessage: String = "Unable to find submission item"
  }
}