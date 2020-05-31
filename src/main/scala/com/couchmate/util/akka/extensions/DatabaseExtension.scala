package com.couchmate.util.akka.extensions

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.couchmate.data.db.PgProfile
import com.couchmate.data.db.PgProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class DatabaseExtension(system: ActorSystem[_]) extends Extension {
  private[this] implicit val ec: ExecutionContext = system.executionContext

  val db: Database = Database.forConfig("db")

  val session: SlickSession = SlickSession
    .forDbAndProfile(db, PgProfile)

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "dbShutdown")(() => Future {
    db.close()
    session.close()
    Done
  })
}

object DatabaseExtension extends ExtensionId[DatabaseExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): DatabaseExtension = new DatabaseExtension(system)
}