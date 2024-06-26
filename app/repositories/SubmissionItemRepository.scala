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

import models.submission.{FailureTypeQuery, QueryResult, SubmissionItem, SubmissionItemStatus}
import models.{DailySummary, DailySummaryV2, Done, ErrorSummary, ListResult}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.Configuration
import play.api.libs.json.{JsNull, JsObject, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

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
    Codecs.playFormatSumCodecs(SubmissionItem.FailureType.format) ++
    Seq(
      Codecs.playFormatCodec(ListResult.format),
      Codecs.playFormatCodec(DailySummary.mongoFormat),
      Codecs.playFormatCodec(DailySummaryV2.mongoFormat),
      Codecs.playFormatCodec(ErrorSummary.format)
    )
  ) {

  private val lockTtl: Duration = Duration.ofSeconds(configuration.get[Int]("lock-ttl"))
  private val timeoutDuration: Duration = configuration.get[Duration]("item-timeout")

  def insert(item: SubmissionItem): Future[Done] =
    Mdc.preservingMdc {
      collection.insertOne(item.copy(lastUpdated = clock.instant()))
        .toFuture()
        .map(_ => Done)
    }

  def update(
              owner: String,
              id: String,
              status: SubmissionItemStatus,
              failureType: Option[SubmissionItem.FailureType],
              failureReason: Option[String]): Future[SubmissionItem] =
    update(Filters.and(
      Filters.equal("id", id),
      Filters.equal("owner", owner)
    ), status, failureType, failureReason)

  def update(
              sdesCorrelationId: String,
              status: SubmissionItemStatus,
              failureType: Option[SubmissionItem.FailureType],
              failureReason: Option[String]
            ): Future[SubmissionItem] =
    update(Filters.equal("sdesCorrelationId", sdesCorrelationId), status, failureType, failureReason)

  private def update(filter: Bson, status: SubmissionItemStatus, failureType: Option[SubmissionItem.FailureType], failureReason: Option[String]): Future[SubmissionItem] = {

    val updates = List(
      Updates.set("lastUpdated", clock.instant()),
      Updates.set("status", status),
      failureReason.map(Updates.set("failureReason", _))
        .getOrElse(Updates.unset("failureReason")),
      failureType.map(Updates.set("failureType", _))
        .getOrElse(Updates.unset("failureType"))
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
           status: Seq[SubmissionItemStatus] = Seq.empty,
           created: Option[LocalDate] = None,
           failureType: Option[FailureTypeQuery] = None,
           limit: Int = 50,
           offset: Int = 0
          ): Future[ListResult] = {

    val ownerFilter = Filters.equal("owner", owner)

    val statusFilter = Option.when(status.nonEmpty)(Filters.or(status.map(Filters.equal("status", _)): _*))

    val failureTypeFilter = failureType.map {
      case FailureTypeQuery.None =>
        Filters.exists("failureType", exists = false)
      case FailureTypeQuery.IsSet =>
        Filters.exists("failureType", exists = true)
      case FailureTypeQuery.These(failureTypes@_*) =>
        failureTypes.map(Filters.eq("failureType", _))
        .reduceLeft((m, n) => Filters.or(m, n))
    }

    val createdFilter = created.toList.flatMap { date =>
      List(
        Filters.gte("created", date.atStartOfDay(ZoneOffset.UTC).toInstant),
        Filters.lt("created", date.atStartOfDay(ZoneOffset.UTC).plusDays(1).toInstant)
      )
    }

    val filters = Filters.and(List(List(ownerFilter), statusFilter, createdFilter, failureTypeFilter).flatten: _*)

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
        ).toDocument)
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
        groupExpression.toDocument
      )).toFuture()
    }
  }

  def dailySummariesV2(owner: String): Future[Seq[DailySummaryV2]] = {

    val processingStatuses = List(
      SubmissionItemStatus.Submitted,
      SubmissionItemStatus.Processed,
      SubmissionItemStatus.Failed,
      SubmissionItemStatus.Forwarded
    )

    Mdc.preservingMdc {
      collection.aggregate[DailySummaryV2](List(
        Aggregates.`match`(Filters.eq("owner", owner)),
        Aggregates.group(
          Json.obj("$dateTrunc" -> Json.obj("date" -> "$created", "unit" -> "day")).toDocument,
          Accumulators.sum("completed",
            Json.obj("$cond" -> Json.obj(
              "if" -> Json.obj("$and" -> Json.arr(
                Json.obj("$eq" -> Json.arr("$status", SubmissionItemStatus.Completed: SubmissionItemStatus)),
                Json.obj("$lt" -> Json.arr("$failureType", JsNull))
              )),
              "then" -> 1,
              "else" -> 0
            )).toDocument
          ),
          Accumulators.sum("processing",
            Json.obj("$cond" -> Json.obj(
              "if" -> Json.obj("$in" -> Json.arr("$status", processingStatuses)),
              "then" -> 1,
              "else" -> 0
            )).toDocument
          ),
          Accumulators.sum("failed",
            Json.obj("$cond" -> Json.obj(
              "if" -> Json.obj("$and" -> Json.arr(
                Json.obj("$eq" -> Json.arr("$status", SubmissionItemStatus.Completed: SubmissionItemStatus)),
                Json.obj("$gt" -> Json.arr("$failureType", JsNull))
              )),
              "then" -> 1,
              "else" -> 0
            )).toDocument
          )
        ),
        Aggregates.project(
          Json.obj(
            "date" -> "$_id",
            "_id" -> 0,
            "completed" -> 1,
            "processing" -> 1,
            "failed" -> 1
          ).toDocument
        )
      )).toFuture()
    }
  }

  def errorSummary(owner: String): Future[ErrorSummary] = Mdc.preservingMdc {
    collection.aggregate[ErrorSummary](Seq(
      Aggregates.`match`(Filters.and(
        Filters.eq("owner", owner),
        Filters.eq("status", SubmissionItemStatus.Completed),
        Filters.exists("failureType")
      )),
      Aggregates.facet(
        Facet("sdesFailureCount", Aggregates.`match`(Filters.eq("failureType", SubmissionItem.FailureType.Sdes)), Aggregates.count()),
        Facet("timeoutFailureCount", Aggregates.`match`(Filters.eq("failureType", SubmissionItem.FailureType.Timeout)), Aggregates.count())
      ),
      Aggregates.unwind("$sdesFailureCount", UnwindOptions().preserveNullAndEmptyArrays(true)),
      Aggregates.unwind("$timeoutFailureCount", UnwindOptions().preserveNullAndEmptyArrays(true)),
      Aggregates.project(Json.obj(
        "sdesFailureCount" -> Json.obj("$ifNull" -> Json.arr("$sdesFailureCount.count", 0)),
        "timeoutFailureCount" -> Json.obj("$ifNull" -> Json.arr("$timeoutFailureCount.count", 0))
      ).toDocument)
    )).head()
  }

  def owners: Future[Set[String]] =
    Mdc.preservingMdc {
      collection.distinct[String]("owner").toFuture().map(_.toSet)
    }

  def failTimedOutItems: Future[Long] = {

    val filter = Filters.and(
      Filters.eq("status", SubmissionItemStatus.Forwarded),
      Filters.lte("lastUpdated", clock.instant().minus(timeoutDuration))
    )

    val updates = Updates.combine(
      Updates.set("status", SubmissionItemStatus.Failed),
      Updates.set("lastUpdated", clock.instant()),
      Updates.set("failureType", SubmissionItem.FailureType.Timeout),
      Updates.set("failureReason", s"Did not receive a callback from SDES within $timeoutDuration")
    )

    Mdc.preservingMdc {
      collection.updateMany(filter, updates)
        .map(_.getModifiedCount)
        .head()
    }
  }

  def getTimedOutItems(owner: String): Source[SubmissionItem, NotUsed] = {

    val filter = Filters.and(
      Filters.eq("owner", owner),
      Filters.eq("status", SubmissionItemStatus.Completed),
      Filters.eq("failureType", SubmissionItem.FailureType.Timeout)
    )

    Source.fromPublisher(collection.find(filter))
  }
}

object SubmissionItemRepository {

  case object NothingToUpdateException extends Exception {
    override def getMessage: String = "Unable to find submission item"
  }
}