package com.couchmate.util.akka.extensions

import java.time.{Duration, LocalDateTime, ZoneId, ZoneOffset}
import java.util.{Date, UUID}

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.couchmate.util.jwt.Jwt.{ExpiredJwtError, InvalidClaimsError, InvalidJwtError}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier, PasswordBasedDecrypter, PasswordBasedEncrypter}
import com.nimbusds.jose._
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.{Failure, Success, Try}

case class CMJwtClaims(
  userId: UUID,
  claims: JWTClaimsSet
)

class JwtExtension(system: ActorSystem[_]) extends Extension {
  private[this] val config: Config = ConfigFactory.load()

  private[this] lazy val secret =
    config.getString("jwt.secret")
  private[this] lazy val encryptString =
    config.getString("jwt.encrypt")
  private[this] lazy val issuer =
    config.getString("jwt.issuer")
  private[this] lazy val expiry =
    config.getDuration("jwt.expiry")

  private[this] lazy val signer: MACSigner =
    new MACSigner(this.secret)

  private[this] lazy val encrypter: PasswordBasedEncrypter =
    new PasswordBasedEncrypter(
      encryptString,
      10,
      1000
    )

  private[this] lazy val decrypter: PasswordBasedDecrypter =
    new PasswordBasedDecrypter(encryptString)

  private[this] lazy val verifier: MACVerifier =
    new MACVerifier(this.secret)

  def createToken(
    subject: String,
    claims: Map[String, String] = Map(),
    expiry: Duration = expiry,
  ): Try[String] = {
    val claimsSet = new JWTClaimsSet.Builder()
    claimsSet.subject(subject)
    claimsSet.issuer(issuer)
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

      val jweObject: JWEObject = new JWEObject(
        new JWEHeader.Builder(
          JWEAlgorithm.PBES2_HS512_A256KW,
          EncryptionMethod.A256GCM
        ).contentType("JWT").build(),
        new Payload(signedJwt)
      )

      jweObject.encrypt(encrypter)

      Success(jweObject.serialize)
    } catch {
      case ex: JOSEException => Failure(ex)
    }
  }

  def validateToken(
    token: String,
    claims: Map[String, String] = Map()
  ): Try[CMJwtClaims] = {
    try {
      val jweObject: JWEObject =
        JWEObject.parse(token)

      jweObject.decrypt(decrypter)

      val signedJWT = jweObject.getPayload.toSignedJWT

      val verified =
        signedJWT.verify(verifier)

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
        Success(CMJwtClaims(
          UUID.fromString(signedJWT.getJWTClaimsSet.getSubject),
          signedJWT.getJWTClaimsSet
        ))
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