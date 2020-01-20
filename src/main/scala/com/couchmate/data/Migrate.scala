package com.couchmate.data

import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.data.schema.{SourceDAO, UserDAO}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

object Migrate {
  def main(args: Array[String]): Unit = {
    implicit val db: Database =
      Database.forURL(
        url = "jdbc:postgresql://localhost/",
        user = "postgres",
        driver = "org.postgresql.Driver",
      )

    Await.result(db.run(
      UserDAO.init(),
      ), Duration.Inf)
  }
}
