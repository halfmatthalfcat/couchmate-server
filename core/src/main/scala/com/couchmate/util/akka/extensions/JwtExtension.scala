package com.couchmate.util.akka.extensions

import java.time.{Duration, LocalDateTime, ZoneId, ZoneOffset}
import java.util.{Date, UUID}

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.util.jwt.Jwt.{ExpiredJwtError, InvalidClaimsError, InvalidJwtError, JwtClaims}
import com.nimbusds.jose.{JOSEException, JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.{Failure, Success, Try}

class JwtExtension(system: ActorSystem[_]) extends Extension {
  private[this] val config: Config = ConfigFactory.load()

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
    expiry: Duration = expiry,
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
      signedJwt.sign(signer)
      Success(signedJwt.serialize())
    } catch {
      case ex: JOSEException => Failure(ex)
    }
  }

  def validateToken(
    token: String,
    claims: Map[String, String] = Map()
  ): Try[UUID] = {
    try {
      val signedJWT = SignedJWT.parse(token)
      val verified = signedJWT.verify(verifier)
      val expired = signedJWT
        .getJWTClaimsSet
        .getExpirationTime
        .after(
          Date.from(
            LocalDateTime.now(ZoneId.of("UTC"))
                         .toInstant(ZoneOffset.UTC)
          )
        )

      val hasClaims = claims.isEmpty || claims
        .foldLeft(true) {
          case (hasClaims, (k, v)) =>
            if (!hasClaims) hasClaims
            else {
              val claim = Option(signedJWT.getJWTClaimsSet.getStringClaim(k))
              claim.nonEmpty && claim.contains(v)
            }
        }

      if (!expired) {
        Failure(ExpiredJwtError)
      } else if (!verified) {
        Failure(InvalidJwtError)
      } else if (!hasClaims) {
        Failure(InvalidClaimsError)
      } else {
        Success(UUID.fromString(signedJWT.getJWTClaimsSet.getSubject))
      }
    } catch {
      case ex: Throwable => Failure(ex)
    }
  }
}

object JwtExtension extends ExtensionId[JwtExtension] {
  override def createExtension(
    system: ActorSystem[_],
  ): JwtExtension = new JwtExtension(system)
}