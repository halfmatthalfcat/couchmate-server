package com.couchmate.db.query

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.AiringTable
import slick.lifted.{Compiled, Rep}

trait AiringQueries {

  private[db] lazy val getAiring = Compiled { (airingId: Rep[UUID]) =>
    AiringTable.table.filter(_.airingId === airingId)
  }

  private[db] lazy val getAiringByShowAndStart = Compiled {
    (showId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        airing.showId === showId &&
        airing.startTime === startTime
      }
  }

  private[db] lazy val getAiringsByStart = Compiled { (startTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.startTime === startTime)
  }

  private[db] lazy val getAiringsByEnd = Compiled { (endTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.endTime === endTime)
  }

  private[db] lazy val getAiringsBetweenStartAndEnd = Compiled {
    (startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        (airing.startTime between (startTime, endTime)) &&
        (airing.endTime between (startTime, endTime)) &&
        (
          airing.startTime <= startTime &&
          airing.endTime >= endTime
        )
      }
  }
}
