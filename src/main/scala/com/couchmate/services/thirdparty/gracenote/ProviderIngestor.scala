package com.couchmate.services.thirdparty.gracenote

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.couchmate.common.models.{ProviderOwner, ZipProvider}
import com.couchmate.data.db.{CMContext, CMDatabase, ProviderDAO, ProviderOwnerDAO, ZipProviderDAO}
import com.couchmate.data.models.{Provider, ProviderOwner, ZipProvider}
import com.couchmate.data.thirdparty.gracenote.{GracenoteProvider, GracenoteProviderOwner}

import scala.concurrent.{ExecutionContext, Future}

class ProviderIngestor(
  gnService: GracenoteService,
  database: CMDatabase,
) {
  import database._

  private[this] def addOwner(
    gracenoteProvider: GracenoteProvider,
  )(implicit ec: ExecutionContext): Future[ProviderOwner] =
    ctx.transaction { implicit ec =>
      gracenoteProvider match {
        case GracenoteProvider(_, name, t, _, location, None) =>
          val cleanedName: String =
            if (t == "OTA") {
              "OTA"
            } else {
              cleanName(name, t, location)
            }

            for {
              ownerExists <- providerOwner.getProviderOwnerForName(cleanedName)
              owner <- ownerExists.fold(providerOwner.upsertProviderOwner(ProviderOwner(
                providerOwnerId = None,
                extProviderOwnerId = None,
                name = cleanedName
              )))(Future.successful)
            } yield owner

          .headOption match {
            case None =>
            case Some(providerOwner: ProviderOwner) => providerOwner
          }
        case GracenoteProvider(_, _, t, _, location, Some(GracenoteProviderOwner(id, ownerName))) =>
          providerOwner.getProviderOwnerForExt(id).headOption match {
            case None => providerOwner.upsertProviderOwner(ProviderOwner(
              providerOwnerId = None,
              extProviderOwnerId = Some(id),
              name = cleanName(ownerName, t, location),
            ))
            case Some(providerOwner: ProviderOwner) => providerOwner
          }
      }
    }

    private[this] def addProvider(
      gracenoteProvider: GracenoteProvider,
      providerOwner: ProviderOwner,
      country: Option[String],
    )(implicit ec: ExecutionContext): Future[Provider] = Future {
      ctx.transaction {
        provider.getProviderForExtAndOwner(
          gracenoteProvider.lineupId,
          providerOwner.providerOwnerId,
          ) match {
            case None => provider.upsertProvider(Provider(
              providerId = None,
              providerOwnerId = providerOwner.providerOwnerId,
              extId = gracenoteProvider.lineupId,
              name = cleanProviderName(gracenoteProvider, country),
              location = gracenoteProvider.location,
              `type` = gracenoteProvider.`type`,
              ))
            case Some(provider: Provider) => provider
        }
      }
    }

  private[this] def linkProvider(
    zipCode: String,
    provider: Provider,
  )(implicit ec: ExecutionContext): Future[ZipProvider] = Future {
    ctx.transaction {
      zipProvider.providerExistsForProviderAndZip(
        provider.providerId.get,
        zipCode,
      ) match {
        case false => zipProvider.insertZipProvider(ZipProvider(
          providerId = provider.providerId.get,
          zipCode = zipCode,
        ))
        case true => ZipProvider(
          providerId = provider.providerId.get,
          zipCode = zipCode,
        )
      }
    }
  }

  def ingestProvider(
    zipCode: Option[String],
    country: Option[String],
    gracenoteProvider: GracenoteProvider,
  )(
    implicit
    ec: ExecutionContext
  ): Future[Provider] = for {
    owner <- addOwner(gracenoteProvider)
    provider <- addProvider(gracenoteProvider, owner, country)
    _ <- linkProvider(zipCode.get, provider) if zipCode.isDefined
  } yield provider

  def ingestProviders(
    zipCode: String,
    country: Option[String],
  )(implicit ec: ExecutionContext, mat: Materializer): Source[Seq[Provider], NotUsed] =
    Source
      .future(gnService.getProviders(zipCode, country))
      .mapConcat(identity)
      .filter(_.name.toLowerCase.contains("c-band"))
      .mapAsync(1)(ingestProvider(Some(zipCode), country, _))
      .fold(Seq[Provider]())(_ :+ _)

  private[this] def cleanName(
      name: String,
      `type`: String,
      location: Option[String],
    ): String = {
      Seq(
        s"(?i)(${`type`})".r,
        s"(?i)(${location.getOrElse("")})".r,
        "(?i)(digital)".r,
        "-".r
      )
        .foldRight(name)(_.replaceAllIn(_, ""))
        .split(" ")
        .map(_.trim)
        .filter(!_.isEmpty)
        .mkString(" ")
    }

  private[this] def cleanProviderName(
    gracenoteProvider: GracenoteProvider,
    country: Option[String],
  ): String = {
    gracenoteProvider match {
      case GracenoteProvider(_, name, t, _, Some(location), _)
        if location != "None" && country.getOrElse("") != location.toLowerCase =>
          if (name.toLowerCase.contains("digital")) {
            s"${cleanName(name, t, Some(location))} Digital ($location)"
          } else {
            s"${cleanName(name, t, Some(location))} ($location)"
          }
      case GracenoteProvider(_, name, t, _, location, _) =>
        cleanName(name, t, location)
    }
  }
}
