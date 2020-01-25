package com.couchmate.data.schema

import com.liyaos.forklift.slick._

trait MigrationManager extends SlickMigrationManager {
  this.migrations = this.migrations ++ Seq(
    SourceDAO.init,
    UserDAO.init,
    ProviderDAO.init,
    UserProviderDAO.init,
    ChannelDAO.init,
    ProviderChannelDAO.init,
    SeriesDAO.init,
    EpisodeDAO.init,
    SportOrganizationDAO.init,
    SportEventDAO.init,
    ShowDAO.init,
    AiringDAO.init,
    ZipProviderDAO.init,
    RoomActivityDAO.init,
    UserMetaDAO.init,
    UserActivityDAO.init,
    UserExtDAO.init,
    UserPrivateDAO.init,
    ListingCacheDAO.init,
    ProviderOwnerDAO.init,
  ).zipWithIndex.map { case (migration, i) =>
    APIMigration(i)(migration)
  }
}

trait Codegen extends SlickCodegen {
  override val generatedDir: String =
    System.getProperty("user.dir") + "/src/main/resources"
  override def tableNames: Seq[String] = Seq(
    "airing",
    "channel",
    "episode",
    "lineup",
    "listing_cache",
    "provider_channel",
    "provider",
    "provider_owner",
    "room_activity",
    "series",
    "show",
    "source",
    "sport_event",
    "sport_organization",
    "user_activity",
    "user",
    "user_ext",
    "user_meta",
    "user_private",
    "user_provider",
    "zip_provider",
  )
}

object Migrations
  extends MigrationManager
  with SlickMigrationCommandLineTool
  with SlickMigrationCommands
  with Codegen {
  def main(args: Array[String]): Unit = {
    execCommands(args.toList)
  }
}
