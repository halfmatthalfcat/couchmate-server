package com.couchmate.data.db

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.Airing

class AiringDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  private[this] implicit val airingInsertMeta =
    insertMeta[Airing](_.airingId)

  def getAiring = quote { (airingId: UUID) =>
    query[Airing]
      .filter(_.airingId.contains(airingId))
  }

  def getAiringByShowAndStart(showId: Long, startTime: LocalDateTime) = quote {
    query[Airing]
      .filter { airing =>
        airing.showId == lift(showId) &&
        airing.startTime == lift(startTime)
      }
  }

  def getAiringsByStart(startTime: LocalDateTime) = quote {
    query[Airing]
      .filter(_.startTime == lift(startTime))
  }

  def getAiringsByEnd(endTime: LocalDateTime) = quote {
    query[Airing]
      .filter(_.endTime == lift(endTime))
  }

  def getAiringsForStartAndDuration(startTime: LocalDateTime, duration: Int) = {
    val endTime = startTime.plusMinutes(duration)
    quote {
      query[Airing]
        .filter { airing =>
          (airing.startTime <> (lift(startTime), lift(endTime))) &&
            (airing.endTime <> (lift(startTime), lift(endTime))) && (
            airing.startTime <= lift(startTime) &&
              airing.endTime >= lift(endTime)
            )
        }
    }
  }

  def upsertAiring(airing: Airing) = quote {
    query[Airing]
      .insert(lift(airing))
      .onConflictUpdate(_.airingId)(
        (from, to) => from.showId -> to.showId,
        (from, to) => from.startTime -> to.startTime,
        (from, to) => from.endTime -> to.endTime,
        (from, to) => from.duration -> to.duration,
      ).returningGenerated(a => a)
  }

}
