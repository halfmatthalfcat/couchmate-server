package com.couchmate.data.db

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.data.db.PgProfile.api._

class DatabaseExtension(system: ActorSystem[_]) extends Extension {
  val db: Database = Database.forConfig("db")
}

object DatabaseExtension extends ExtensionId[DatabaseExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): DatabaseExtension = new DatabaseExtension(system)
}