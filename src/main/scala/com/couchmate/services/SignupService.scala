//package com.couchmate.services
//
//import com.couchmate.api.JwtProvider
//import com.couchmate.data.models.{CMError, UserMeta, UserPrivate, UserProvider, UserRole, User => DataUser}
//import com.couchmate.api.models.User
//import com.couchmate.api.models.signup.SignupError.{EmailExists, UsernameExists}
//import com.couchmate.api.models.signup.{AnonSignup, EmailSignup, ValidateSignup}
//import com.github.halfmatthalfcat.moniker.Moniker
//import com.github.t3hnar.bcrypt._
//import com.typesafe.config.Config
//import com.typesafe.scalalogging.LazyLogging
//
//import scala.concurrent.{ExecutionContext, Future}
//
//trait SignupService extends JwtProvider with LazyLogging {
//  implicit val ec: ExecutionContext
//  val config: Config
//
//  val moniker: Moniker = Moniker()
//
//  def anonSignup(signup: AnonSignup): Future[User] = for {
//    user <- db.user.upsertUser(DataUser(
//      userId = None,
//      username = moniker
//        .getRandom()
//        .split(' ')
//        .map(_.capitalize)
//        .mkString(" "),
//      role = UserRole.Anon,
//      active = true,
//      verified = false,
//    ))
//    token <- Future.fromTry(create(user.userId.toString))
//  } yield User(
//    userId = user.userId.get,
//    username = user.username,
//    token = token,
//    zipCode = signup.zipCode,
//    providerId = signup.providerId,
//  )
//
//  def validateSignup(email: String, username: String): Future[ValidateSignup] = for {
//    e <- db.userMeta.emailExists(email)
//    u <- db.user.usernameExists(username)
//  } yield ValidateSignup(u, e)
//
//  def emailSignup(signup: EmailSignup): Future[User] = for {
//    v <- validateSignup(signup.email, signup.username)
//    _ = {
//      logger.debug(s"$v")
//      if (v.email) {
//        throw new CMError(EmailExists)
//      } else if (v.username) {
//        throw new CMError(UsernameExists)
//      } else { () }
//    }
//    user <- db.user.upsertUser(DataUser(
//      userId = None,
//      username = signup.username,
//      active = true,
//      verified = false,
//      role = UserRole.Registered,
//    ))
//    token <- Future.fromTry(create(user.userId.get.toString))
//    _ <- db.userMeta.upsertUserMeta(UserMeta(
//      userId =  user.userId.get,
//      email = signup.email,
//    ))
//    _ <- db.userPrivate.upsertUserPrivate(UserPrivate(
//      userId = user.userId.get,
//      password = signup.password.bcrypt(10)
//    ))
//    _ <- db.userProvider.addUserProvider(UserProvider(
//      userId = user.userId.get,
//      zipCode = signup.zipCode,
//      providerId = signup.providerId,
//    ))
//  } yield User(
//    userId = user.userId.get,
//    username = user.username,
//    token = token,
//    zipCode = signup.zipCode,
//    providerId = signup.providerId,
//  )
//
//}
