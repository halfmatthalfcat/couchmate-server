package com.couchmate.data.db.dao

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.AiringTable
import com.couchmate.data.models.Airing
import com.couchmate.data.thirdparty.gracenote.GracenoteAiring
import slick.lifted.{Compiled, Rep}

import scala.concurrent.{ExecutionContext, Future}

class AiringDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getAiring(airingId: UUID): Future[Option[Airing]] = {
    db.run(AiringDAO.getAiring(airingId).result.headOption)
  }

  def getAiringsByStart(startTime: LocalDateTime): Future[Seq[Airing]] = {
    db.run(AiringDAO.getAiringsByStart(startTime).result)
  }

  def getAiringsByEnd(endTime: LocalDateTime): Future[Seq[Airing]] = {
    db.run(AiringDAO.getAiringsByEnd(endTime).result)
  }

  def getAiringsByStartAndDuration(startTime: LocalDateTime, duration: Int): Future[Seq[Airing]] = {
    val endTime: LocalDateTime = startTime.plusMinutes(duration)
    db.run(AiringDAO.getAiringsBetweenStartAndEnd(startTime, endTime).result)
  }

  def upsertAiring(airing: Airing): Future[Airing] = db.run(
    airing.airingId.fold[DBIO[Airing]](
      (AiringTable.table returning AiringTable.table) += airing
    ) { (airingId: UUID) => for {
      _ <- AiringTable.table.update(airing)
      updated <- AiringDAO.getAiring(airingId).result.head
    } yield updated}.transactionally
  )

  def getAiringFromGracenote(
    showId: Long,
    airing: GracenoteAiring,
  ): Future[Airing] = db.run((for {
    exists <- AiringDAO.getAiringByShowStartAndEnd(
      showId,
      airing.startTime,
      airing.endTime,
    ).result.headOption
    airing <- exists.fold[DBIO[Airing]](
      (AiringTable.table returning AiringTable.table) += Airing(
        airingId = Some(UUID.randomUUID()),
        showId = showId,
        startTime = airing.startTime,
        endTime = airing.endTime,
        duration = airing.duration
      )
    )(DBIO.successful)
  } yield airing).transactionally)

}

object AiringDAO {
  private[dao] lazy val getAiring = Compiled { (airingId: Rep[UUID]) =>
    AiringTable.table.filter(_.airingId === airingId)
  }

  private[dao] lazy val getAiringByShowStartAndEnd = Compiled {
    (showId: Rep[Long], startTime: Rep[LocalDateTime], endTime: Rep[LocalDateTime]) =>
      AiringTable.table.filter { airing =>
        airing.showId === showId &&
        airing.startTime === startTime &&
        airing.endTime === endTime
      }
  }

  private[dao] lazy val getAiringsByStart = Compiled { (startTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.startTime === startTime)
  }

  private[dao] lazy val getAiringsByEnd = Compiled { (endTime: Rep[LocalDateTime]) =>
    AiringTable.table.filter(_.endTime === endTime)
  }

  private[dao] lazy val getAiringsBetweenStartAndEnd = Compiled {
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
