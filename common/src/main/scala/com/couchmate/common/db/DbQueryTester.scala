package com.couchmate.common.db

import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.couchmate.common.db.PgProfile
import com.couchmate.common.db.PgProfile.api._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

object DbQueryTester {
  def main(args: Array[String]): Unit = {
    implicit val db: Database = Database.forConfig("db")

    implicit val session: SlickSession = SlickSession
      .forDbAndProfile(db, PgProfile)

//    println(Json.toJson(
//      Await.result(
//
//      )
//    ))
  }
}
