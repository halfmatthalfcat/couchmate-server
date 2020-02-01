//package com.couchmate.services.thirdparty.gracenote
//
//import akka.NotUsed
//import akka.stream.scaladsl.Source
//import com.couchmate.common.models.{Provider, ProviderOwner, ZipProvider}
//import com.couchmate.data.schema.PgProfile.api._
//import com.couchmate.data.schema.ZipProviderDAO
//import com.couchmate.data.thirdparty.gracenote.{GracenoteProvider, GracenoteProviderOwner}
//
//import scala.concurrent.{ExecutionContext, Future}
//
//object ProviderIngestor {
//
//  private[this] def cleanName(
//    name: String,
//    `type`: String,
//    location: Option[String],
//  ): String = {
//    Seq(
//      s"(?i)(${`type`})".r,
//      s"(?i)(${location.getOrElse("")})".r,
//      "(?i)(digital)".r,
//      "-".r
//    )
//      .foldRight(name)(_.replaceAllIn(_, ""))
//      .split(" ")
//      .map(_.trim)
//      .filter(!_.isEmpty)
//      .mkString(" ")
//  }
//
//  private[this] def cleanProviderName(
//    gracenoteProvider: GracenoteProvider,
//    country: String,
//  ): String = {
//    gracenoteProvider match {
//      case GracenoteProvider(_, name, t, _, Some(location), _)
//        if location != "None" && country.toLowerCase != location.toLowerCase =>
//          if (name.toLowerCase.contains("digital")) {
//            s"${cleanName(name, t, Some(location))} Digital ($location)"
//          } else {
//            s"${cleanName(name, t, Some(location))} ($location)"
//          }
//      case GracenoteProvider(_, name, t, _, location, _) =>
//        cleanName(name, t, location)
//    }
//  }
//
//  private[this] def ingestOwner(
//    gracenoteProvider: GracenoteProvider,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[ProviderOwner] = {
//    gracenoteProvider match {
//      case GracenoteProvider(_, name, t, _, location, None) =>
//        val cleanedName: String =
//          if (t == "OTA") {
//            "OTA"
//          } else {
//            cleanName(name, t, location)
//          }
//
//        ProviderOwnerDAO.getProviderOwnerForName(cleanedName) flatMap {
//          case None => ProviderOwnerDAO.upsertProviderOwner(ProviderOwner(
//            providerOwnerId = None,
//            extProviderOwnerId = None,
//            name = cleanedName
//          ))
//          case Some(providerOwner) =>
//            Future.successful(providerOwner)
//        }
//      case GracenoteProvider(_, providerName, t, _, location, Some(GracenoteProviderOwner(id, ownerName))) =>
//        ProviderOwnerDAO.getProviderOwnerForExt(id) flatMap {
//          case None => ProviderOwnerDAO.upsertProviderOwner(ProviderOwner(
//            providerOwnerId = None,
//            extProviderOwnerId = Some(id),
//            name = cleanName(ownerName, t, location),
//          ))
//          case Some(providerOwner) =>
//            Future.successful(providerOwner)
//        }
//    }
//  }
//
//  private[this] def ingestProvider(
//    gracenoteProvider: GracenoteProvider,
//    providerOwner: ProviderOwner,
//    country: String,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Provider] = {
//    ProviderDAO.getProviderForExtAndOwner(
//      gracenoteProvider.lineupId,
//      providerOwner.providerOwnerId,
//    ) flatMap {
//      case None =>
//        ProviderDAO.upsertProvider(Provider(
//          providerId = None,
//          providerOwnerId = providerOwner.providerOwnerId,
//          extId = gracenoteProvider.lineupId,
//          name = cleanProviderName(gracenoteProvider, country),
//          location = gracenoteProvider.location,
//          `type` = gracenoteProvider.`type`,
//        ))
//      case Some(provider) =>
//        Future.successful(provider)
//    }
//  }
//
//  private[this] def linkProvider(
//    zipCode: String,
//    provider: Provider,
//  )(
//    implicit
//    database: Database,
//    ec: ExecutionContext,
//  ): Future[ZipProvider] = {
//    ZipProviderDAO.providerExistsForProviderAndZip(
//      provider.providerId.get,
//      zipCode,
//    ) flatMap {
//      case false =>
//        ZipProviderDAO.insertZipProvider(ZipProvider(
//          providerId = provider.providerId.get,
//          zipCode = zipCode,
//        ))
//      case true => Future.successful(ZipProvider(
//        providerId = provider.providerId.get,
//        zipCode = zipCode,
//      ))
//    }
//  }
//
//  def ingestProvider(
//    zipCode: String,
//    country: String,
//  )(
//    gracenoteProvider: GracenoteProvider,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Provider] = for {
//    owner <- ingestOwner(gracenoteProvider)
//    provider <- ingestProvider(gracenoteProvider, owner, country)
//    _ <- linkProvider(zipCode, provider)
//  } yield provider
//
//  def ingestProviders(
//    zipCode: String,
//    country: String,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//    gs: GracenoteService,
//  ): Source[Provider, NotUsed] =
//    gs.getProviders(zipCode, country)
//      .filter(gnp => (
//        !gnp.name.toLowerCase.contains("c-band")
//      ))
//      .mapAsync[Provider](1)(ProviderIngestor.ingestProvider(
//        zipCode,
//        country,
//      ))
//}
