package com.couchmate.db.dao

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.Airing
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.AiringQueries
import com.couchmate.db.table.AiringTable

import scala.concurrent.{ExecutionContext, Future}

class AiringDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends AiringQueries {

  def getAiring(airingId: UUID): Future[Option[Airing]] = {
    db.run(super.getAiring(airingId).result.headOption)
  }

  def getAiringsByStart(startTime: LocalDateTime): Future[Seq[Airing]] = {
    db.run(super.getAiringsByStart(startTime).result)
  }

  def getAiringsByEnd(endTime: LocalDateTime): Future[Seq[Airing]] = {
    db.run(super.getAiringsByEnd(endTime).result)
  }

  def getAiringsByStartAndDuration(startTime: LocalDateTime, duration: Int): Future[Seq[Airing]] = {
    val endTime: LocalDateTime = startTime.plusMinutes(duration)
    db.run(super.getAiringsBetweenStartAndEnd(startTime, endTime).result)
  }

  def upsertAiring(airing: Airing): Future[Airing] =
    airing.airingId.fold(
      db.run((AiringTable.table returning AiringTable.table) += airing)
    ) { (airingId: UUID) => db.run(for {
      _ <- AiringTable.table.update(airing)
      updated <- super.getAiring(airingId)
    } yield updated.result.head.transactionally)}

}
