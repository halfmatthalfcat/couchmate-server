package com.couchmate.api.ws

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.couchmate.api.JwtProvider
import com.couchmate.api.models.User
import com.couchmate.api.ws.protocol._
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.{UserDAO, UserMetaDAO, UserPrivateDAO, UserProviderDAO}
import com.couchmate.data.models.CountryCode
import com.couchmate.external.gracenote.listing.ListingCoordinator.RequestListing
import com.couchmate.external.gracenote.listing.{ListingCoordinator, ListingJob}
import com.couchmate.external.gracenote.provider.{ProviderCoordinator, ProviderJob}
import com.couchmate.util.akka.AkkaUtils
import com.couchmate.util.akka.extensions.{DatabaseExtension, SingletonExtension}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * A Connected WS Client
 */

class Connected private[ws] (
  ctx: ActorContext[Command],
  ws: ActorRef[Command]
) extends AkkaUtils
  with JwtProvider
  with UserMetaDAO
  with UserDAO
  with UserPrivateDAO
  with UserProviderDAO {
  implicit val ec: ExecutionContext = ctx.executionContext
  implicit val db: Database = DatabaseExtension(ctx.system).db
  val providerCoordinator: ActorRef[ProviderCoordinator.Command] =
    SingletonExtension(ctx.system).providerCoordinator
  val listingCoordinator: ActorRef[ListingCoordinator.Command] =
    SingletonExtension(ctx.system).listingCoordinator

  val providerAdapter: ActorRef[ProviderJob.Command] = ctx.messageAdapter[ProviderJob.Command] {
    case ProviderJob.JobEnded(_, _, providers) => Outgoing(GetProvidersResponse(providers))
  }

  val listingAdapter: ActorRef[ListingJob.Command] = ctx.messageAdapter[ListingJob.Command] {
    case ListingJob.JobEnded(_, grid) => Outgoing(GetGridResponse(grid))
  }

  def run: PartialCommand =
    chain(internal, incoming, outgoing)

  private def outgoing: PartialCommand = {
    case outgoing: Outgoing =>
      ws ! outgoing
      Behaviors.same
  }

  private def internal: PartialCommand =
    PartialFunction.empty

  private def incoming: PartialCommand = {
    case Incoming(ValidateNewAccount(email, username)) =>
      ctx.pipeToSelf(validateNewAccount(email, username)) {
        case Success(value) => Outgoing(ValidateNewAccountResponse(value))
        case Failure(_) => Outgoing(ValidateNewAccountResponse(
          ValidateNewAccountResponseStatus.UnknownError
        ))
      }
      Behaviors.same
    case Incoming(account: CreateNewAccount) =>
      ctx.pipeToSelf(createNewAccount(account)) {
        case Success(value) => value
        case Failure(_) => Outgoing(CreateNewAccountFailure(
          CreateNewAccountError.UnknownError
        ))
      }
      Behaviors.same
    case Incoming(GetProviders(zipCode, country)) =>
      providerCoordinator ! ProviderCoordinator.RequestProviders(
        zipCode,
        country,
        providerAdapter
      )
      Behaviors.same
    case Incoming(GetGrid(providerId)) =>
      listingCoordinator ! ListingCoordinator.RequestListing(
        providerId,
        listingAdapter
      )
      Behaviors.same
  }

  private def validateNewAccount(email: String, username: String): Future[ValidateNewAccountResponseStatus] = for {
    emailExists <- emailExists(email)
    usernameExists <- usernameExists(username)
  } yield {
    if (emailExists) { ValidateNewAccountResponseStatus.EmailExists }
    else if (usernameExists) { ValidateNewAccountResponseStatus.UsernameExists }
    else { ValidateNewAccountResponseStatus.Valid }
  }

  private def createNewAccount(account: CreateNewAccount): Future[Outgoing] = for {
    valid <- validateNewAccount(account.email, account.username)
    response <- if (valid == ValidateNewAccountResponseStatus.Valid) {
      for {
        user <- createEmailAccount(
          account.email,
          account.username,
          account.password,
          account.zipCode,
          account.providerId
        )
        token <- Future.fromTry(create(user.userId.get.toString))
      } yield Outgoing(CreateNewAccountSuccess(User(
        userId = user.userId.get,
        username = user.username,
        token = token,
        zipCode = account.zipCode,
        providerId = account.providerId
      )))
    } else if (valid == ValidateNewAccountResponseStatus.UsernameExists) {
      Future.successful(Outgoing(CreateNewAccountFailure(
        CreateNewAccountError.UsernameExists
      )))
    } else if (valid == ValidateNewAccountResponseStatus.EmailExists) {
      Future.successful(Outgoing(CreateNewAccountFailure(
        CreateNewAccountError.UsernameExists
      )))
    } else {
      Future.successful(Outgoing(CreateNewAccountFailure(
        CreateNewAccountError.UnknownError
      )))
    }
  } yield response
}
