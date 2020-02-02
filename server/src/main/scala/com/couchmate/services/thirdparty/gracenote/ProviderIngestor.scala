package com.couchmate.services.thirdparty.gracenote

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.couchmate.common.models.{Provider, ProviderOwner, ZipProvider}
import com.couchmate.data.db.{CMContext, CMDatabase, ProviderDAO, ProviderOwnerDAO, ZipProviderDAO}
import com.couchmate.data.thirdparty.gracenote.{GracenoteProvider, GracenoteProviderOwner}

class ProviderIngestor(
  gnService: GracenoteService,
  database: CMDatabase,
) {
  import database._

  private[this] def ingestOwner(
      gracenoteProvider: GracenoteProvider,
    ): ProviderOwner = {
      gracenoteProvider match {
        case GracenoteProvider(_, name, t, _, location, None) =>
          val cleanedName: String =
            if (t == "OTA") {
              "OTA"
            } else {
              cleanName(name, t, location)
            }

          providerOwner.getProviderOwnerForName(cleanedName).headOption match {
            case None => providerOwner.upsertProviderOwner(ProviderOwner(
              providerOwnerId = None,
              extProviderOwnerId = None,
              name = cleanedName
            ))
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

    private[this] def ingestProvider(
      gracenoteProvider: GracenoteProvider,
      providerOwner: ProviderOwner,
      country: String,
    ): Provider = {
      provider.getProviderForExtAndOwner(
        gracenoteProvider.lineupId,
        providerOwner.providerOwnerId.get,
      ).headOption match {
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

  private[this] def linkProvider(
    zipCode: String,
    provider: Provider,
  ): ZipProvider = {
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

  def ingestProvider(
    zipCode: String,
    country: String,
  )(gracenoteProvider: GracenoteProvider): Provider = {
    val owner = ingestOwner(gracenoteProvider)
    val provider = ingestProvider(gracenoteProvider, owner, country)
    linkProvider(zipCode, provider)
    provider
  }

  def ingestProviders(
    zipCode: String,
    country: Option[String],
  ): Source[Provider, NotUsed] =
    gnService.getProviders(zipCode, country)
      .filter(gnp => (
        !gnp.name.toLowerCase.contains("c-band")
      ))
      .map[Provider](ingestProvider(
        zipCode,
        country,
      ))

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
    country: String,
  ): String = {
    gracenoteProvider match {
      case GracenoteProvider(_, name, t, _, Some(location), _)
        if location != "None" && country.toLowerCase != location.toLowerCase =>
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
