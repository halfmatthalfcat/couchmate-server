package com.couchmate.services.thirdparty.gracenote

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.models.Provider
import com.couchmate.data.thirdparty.gracenote.GracenoteProvider

import scala.concurrent.{ExecutionContext, Future}

class ProviderIngestor(
  gnService: GracenoteService,
  database: CMDatabase,
) {
  import database._

  def ingestProvider(
    zipCode: Option[String],
    country: Option[String],
    gracenoteProvider: GracenoteProvider,
  )(
    implicit
    ec: ExecutionContext
  ): Future[Provider] = for {
    owner <- providerOwner.getProviderOwnerFromGracenote(
      gracenoteProvider,
      country,
    )
    provider <- provider.getProviderFromGracenote(
      gracenoteProvider,
      owner,
      country,
    )
    _ <- zipProvider.getZipProviderFromGracenote(
      zipCode.get,
      provider,
    ) if zipCode.isDefined
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
}
