package com.couchmate.util.jwt

import java.math.BigInteger
import java.security.SecureRandom

object Jwt {
  private[this] val random = new SecureRandom()

  def main(args: Array[String]): Unit = {
    System.out.println(new BigInteger(32 * 5, random).toString(32))
  }

  case object InvalidJwtError     extends Throwable
  case object ExpiredJwtError     extends Throwable
  case object InvalidClaimsError  extends Throwable
}