package com.couchmate.data.schema

import com.liyaos.forklift.slick._

trait MigrationManager extends SlickMigrationManager {
  this.migrations = this.migrations ++ Seq(
    APIMigration(1)(SourceDAO.init),
    APIMigration(2)(UserDAO.init),
    APIMigration(3)(ProviderDAO.init),
    APIMigration(4)(UserProviderDAO.init),
    APIMigration(5)(ChannelDAO.init),
    APIMigration(6)(ProviderChannelDAO.init),
    APIMigration(7)(SeriesDAO.init),
    APIMigration(8)(EpisodeDAO.init),
    APIMigration(9)(SportOrganizationDAO.init),
    APIMigration(10)(SportEventDAO.init),
    APIMigration(11)(ShowDAO.init),
    APIMigration(12)(AiringDAO.init),
    APIMigration(13)(ZipProviderDAO.init),
    APIMigration(14)(RoomActivityDAO.init),
    APIMigration(15)(UserMetaDAO.init),
    APIMigration(16)(UserActivityDAO.init),
    APIMigration(17)(UserExtDAO.init),
    APIMigration(18)(UserPrivateDAO.init),
    APIMigration(19)(ListingCacheDAO.init),
  )
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
