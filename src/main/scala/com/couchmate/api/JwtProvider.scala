package com.couchmate.api

import java.time.{Duration, LocalDateTime, ZoneId, ZoneOffset}
import java.util.{Date, UUID}

import com.nimbusds.jose.{JOSEException, JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.{Failure, Success, Try}

object JwtProvider {
  case object InvalidJwtError extends Throwable
  case object ExpiredJwtError extends Throwable
}

trait JwtProvider {
  import JwtProvider._

  private[this] lazy val config: Config =
    ConfigFactory.load()

  private[this] lazy val secret =
    config.getString("jwt.secret")
  private[this] lazy val issuer =
    config.getString("jwt.issuer")
  private[this] lazy val expiry =
    config.getDuration("jwt.expiry")

  private[this] lazy val signer: MACSigner =
    new MACSigner(this.secret)

  private[this] lazy val verifier: MACVerifier =
    new MACVerifier(this.secret)

  def createToken(
    subject: String,
    claims: Map[String, String] = Map(),
    expiry: Duration = this.expiry,
  ): Try[String] = {
    val claimsSet = new JWTClaimsSet.Builder()
    claimsSet.subject(subject)
    claims.foreach {
      case (key, value) =>
        claimsSet.claim(key, value)
    }
    claimsSet.expirationTime(
      Date.from(
        LocalDateTime
          .now(ZoneId.of("UTC"))
          .plus(expiry)
          .toInstant(ZoneOffset.UTC)
      )
    )
    val signedJwt: SignedJWT = new SignedJWT(
      new JWSHeader(JWSAlgorithm.HS256),
      claimsSet.build()
    )

    try {
      signedJwt.sign(this.signer)
      Success(signedJwt.serialize())
    } catch {
      case ex: JOSEException => Failure(ex)
    }
  }

  def validateToken(token: String): Try[UUID] = {
    try {
      val signedJWT = SignedJWT.parse(token)
      val verified = signedJWT.verify(this.verifier)
      val expired = signedJWT
        .getJWTClaimsSet
        .getExpirationTime
        .after(
          Date.from(
            LocalDateTime.now(ZoneId.of("UTC"))
                         .toInstant(ZoneOffset.UTC)
          )
        )

      if (expired) {
        Failure(ExpiredJwtError)
      } else if (!verified) {
        Failure(InvalidJwtError)
      } else {
        Success(UUID.fromString(signedJWT.getJWTClaimsSet.getSubject))
      }
    } catch {
      case ex: Throwable => Failure(ex)
    }
  }
}
