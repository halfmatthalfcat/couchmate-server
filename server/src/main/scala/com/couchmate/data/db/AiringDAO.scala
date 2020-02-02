package com.couchmate.data.db

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.Airing

class AiringDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val airingInsertMeta =
    insertMeta[Airing](_.airingId)

  def getAiring(airingId: UUID) = quote {
    query[Airing]
      .filter(_.airingId.contains(airingId))
  }

  def getAiringByShowAndStart(showId: Long, startTime: LocalDateTime) = quote {
    query[Airing]
      .filter { airing =>
        airing.showId == showId &&
        airing.startTime == startTime
      }
  }

  def getAiringsByStart(startTime: LocalDateTime) = quote {
    query[Airing]
      .filter(_.startTime == startTime)
  }

  def getAiringsByEnd(endTime: LocalDateTime) = quote {
    query[Airing]
      .filter(_.endTime == endTime)
  }

  def getAiringsForStartAndDuration(startTime: LocalDateTime, duration: Int) = {
    val endTime = startTime.plusMinutes(duration)
    quote {
      query[Airing]
        .filter { airing =>
          (airing.startTime <> (startTime, endTime)) &&
            (airing.endTime <> (startTime, endTime)) && (
            airing.startTime <= startTime &&
              airing.endTime >= endTime
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
