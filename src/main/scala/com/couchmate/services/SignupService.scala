package com.couchmate.services

import java.util.UUID

import com.couchmate.data.models.{UserMeta, UserPrivate, UserProvider, UserRole, User => DataUser}
import com.couchmate.api.models.User
import com.couchmate.api.models.signup.{AnonSignup, EmailSignup}
import com.couchmate.data.db.CMDatabase
import com.github.halfmatthalfcat.moniker.Moniker
import com.github.t3hnar.bcrypt._

import scala.concurrent.{ExecutionContext, Future}

trait SignupService {
  implicit val ec: ExecutionContext
  val db: CMDatabase

  val moniker: Moniker = Moniker()

  def anonSignup(signup: AnonSignup): Future[User] = for {
    user <- db.user.upsertUser(DataUser(
      userId = None,
      username = moniker
        .getRandom()
        .split(' ')
        .map(_.capitalize)
        .mkString(" "),
      role = UserRole.Anon,
      active = true,
      verified = false,
    ))
  } yield User(
    userId = user.userId.get,
    username = user.username,
  )

  def emailSignup(signup: EmailSignup): Future[User] = for {
    user <- db.user.upsertUser(DataUser(
      userId = None,
      username = moniker
        .getRandom()
        .split(' ')
        .map(_.capitalize)
        .mkString(" "),
      active = true,
      verified = false,
      role = UserRole.Registered,
    ))
    _ <- db.userMeta.upsertUserMeta(UserMeta(
      userId =  user.userId.get,
      email = signup.email,
    ))
    _ <- db.userPrivate.upsertUserPrivate(UserPrivate(
      userId = user.userId.get,
      password = signup.password.bcrypt(10)
    ))
    _ <- db.userProvider.addUserProvider(UserProvider(
      userId = user.userId.get,
      zipCode = signup.zipCode,
      providerId = signup.providerId,
    ))
  } yield User(
    userId = user.userId.get,
    username = user.username,
  )

}
