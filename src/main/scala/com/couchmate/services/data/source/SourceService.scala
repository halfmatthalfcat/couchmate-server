package com.couchmate.services.data.source

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.couchmate.data.models.Source
import com.couchmate.data.schema.PgProfile.api._
import com.couchmate.data.schema.SourceDAO

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


object SourceService {
  sealed trait Command
  case class AddSource(source: Source, replyTo: ActorRef[Either[(Int, Source), (Int, String)]]) extends Command

  val SourceServiceKey: ServiceKey[Command] =
    ServiceKey[Command]("sourceService")

  def apply()(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Register(SourceServiceKey, ctx.self)

    Behaviors.receiveMessage[Command] {
      case AddSource(source, replyTo) =>
        SourceDAO.addSource(source) onComplete {
          case Success(source: Source) =>
            replyTo ! Left(201 -> source)
          case Failure(ex) =>
            replyTo ! Right(500 -> ex.getMessage)
        }
        Behaviors.same
    }
  }
}
