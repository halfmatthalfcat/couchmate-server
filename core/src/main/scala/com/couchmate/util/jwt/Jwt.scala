package com.couchmate.util.jwt

import enumeratum._

object Jwt {
  sealed trait JwtClaims extends EnumEntry

  object JwtClaims
    extends Enum[JwtClaims]
      with PlayJsonEnum[JwtClaims] {

    val values = findValues

    case object Access        extends JwtClaims
    case object Registration  extends JwtClaims
    case object PasswordReset extends JwtClaims

  }

  case object InvalidJwtError     extends Throwable
  case object ExpiredJwtError     extends Throwable
  case object InvalidClaimsError  extends Throwable
}