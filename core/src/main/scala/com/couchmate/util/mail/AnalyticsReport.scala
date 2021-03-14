package com.couchmate.util.mail

import com.couchmate.common.dao.RoomActivityAnalyticsDAO.RoomActivityAnalyticContent
import com.couchmate.common.models.data.{RoomActivityAnalytics, UserActivityAnalytics}
import scalatags.Text.all._

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.{DateTimeFormatter, FormatStyle}

object AnalyticsReport {
  private[this] def aTable(mods: Modifier*): Modifier = table(
    width := "100%",
    border := "1px solid grey",
    borderCollapse := "collapse"
  )(mods)
  private[this] def innerTable(mods: Modifier*): Modifier = table(
    width := "100%",
  )(mods)
  private[this] def header(mods: Modifier*): Modifier = th(
    padding := "5px",
    border := "1px solid grey"
  )(mods)
  private[this] def outerCell(mods: Modifier*): Modifier = td(
    padding := "5px",
    border := "1px solid grey"
  )(mods)
  private[this] def innerCell(mods: Modifier*): Modifier = td(
    textAlign := "center"
  )(mods)

  private[this] def roundTo(value: Double, places: Int): Double =
    BigDecimal(value).setScale(places, BigDecimal.RoundingMode.HALF_UP).toDouble

  private[this] def outerCellPct(value: Double): Modifier = td(
    padding := "5px",
    border := "1px solid grey",
    color := {
      if (value < 0d) { "red" }
      else if (value > 0d) { "green" }
      else { "grey" }
    }
  )(s"${roundTo(value, 2)}%")

  private[this] def innerCellPct(value: Double): Modifier = td(
    textAlign := "center",
    color := {
      if (value < 0d) { "red" }
      else if (value > 0d) { "green" }
      else { "grey" }
    }
  )(s"${roundTo(value, 2)}%")

  def userCountTable(userActivityAnalytics: UserActivityAnalytics): Modifier = aTable(
    tr(
      border := "1px solid black"
    )(
      header(),
      header("Current"),
      header("Previous"),
      header(),
      header("Prev Week"),
      header()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("DAU"),
      outerCell(userActivityAnalytics.dau),
      outerCell(userActivityAnalytics.prevDau),
      outerCellPct(userActivityAnalytics.dauChange * 100),
      outerCell(userActivityAnalytics.prevWeekDau),
      outerCellPct(userActivityAnalytics.dauChangeLastWeek * 100)
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("WAU"),
      outerCell(userActivityAnalytics.wau),
      outerCell(userActivityAnalytics.prevWau),
      outerCellPct(userActivityAnalytics.wauChange * 100),
      outerCell(),
      outerCell()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("MAU"),
      outerCell(userActivityAnalytics.mau),
      outerCell(userActivityAnalytics.prevMau),
      outerCellPct(userActivityAnalytics.mauChange * 100),
      outerCell(),
      outerCell()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("DAU/MAU"),
      td(
        padding := "5px",
        border := "1px solid grey",
        colspan := "5",
      )(s"${roundTo(userActivityAnalytics.dauMauRatio * 100, 2)}%")
    )
  )

  def userSessionTable(userActivityAnalytics: UserActivityAnalytics): Modifier = aTable(
    tr(
      border := "1px solid black"
    )(
      header(),
      header("Current"),
      header("Previous"),
      header(),
      header("Prev Week"),
      header()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("DAPS"),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.dauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.dauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevDauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevDauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.dauPerSessionMMChange._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.dauPerSessionMMChange._2 * 100
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWeekDauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWeekDauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.dauPerSessionMMChangeLastWeek._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.dauPerSessionMMChangeLastWeek._2 * 100
          )
        )
      ))
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("DATS"),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.dauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.dauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevDauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevDauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.dauTotalSessionMMChange._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.dauTotalSessionMMChange._2 * 100
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWeekDauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWeekDauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.dauTotalSessionMMChangeLastWeek._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.dauTotalSessionMMChangeLastWeek._2 * 100
          )
        )
      ))
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("WAPS"),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.wauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.wauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.wauPerSessionMMChange._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.wauPerSessionMMChange._2 * 100
          )
        )
      )),
      outerCell(),
      outerCell()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("WATS"),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.wauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.wauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevWauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.wauTotalSessionMMChange._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.wauTotalSessionMMChange._2 * 100
          )
        )
      )),
      outerCell(),
      outerCell()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("MAPS"),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.mauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.mauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevMauPerSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevMauPerSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.mauPerSessionMMChange._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.mauPerSessionMMChange._2 * 100
          )
        )
      )),
      outerCell(),
      outerCell()
    ),
    tr(
      border := "1px solid grey"
    )(
      outerCell("MATS"),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.mauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.mauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCell(
            s"${roundTo(userActivityAnalytics.prevMauTotalSessionMM._1 / 60, 2)}m"
          ),
          innerCell(
            s"${roundTo(userActivityAnalytics.prevMauTotalSessionMM._2 / 60, 2)}m"
          )
        )
      )),
      outerCell(innerTable(
        tr(
          innerCell("Avg"),
          innerCell("Med")
        ),
        tr(
          innerCellPct(
            userActivityAnalytics.mauTotalSessionMMChange._1 * 100
          ),
          innerCellPct(
            userActivityAnalytics.mauTotalSessionMMChange._2 * 100
          )
        )
      )),
      outerCell(),
      outerCell()
    ),
  )

  def roomActivityTable(content: RoomActivityAnalyticContent): Modifier = aTable(
    tr(
      border := "1px solid black"
    )(
      header(),
      header("Name"),
      header("Secondary"),
      header("Users"),
      header("Average Minutes"),
      header("Total Minutes"),
      header("Aired (EST)")
    ),
    if (content.shows.nonEmpty) {
      Seq[Modifier](
        tr(
          td(
            colspan := "6"
          )("Shows")
        ),
        content.shows.map[Modifier](show => tr(
          outerCell(),
          outerCell(show.airing.show.title),
          outerCell(),
          outerCell(show.sessions.size),
          outerCell(s"${roundTo(show.sessions.map(_.duration).sum / 60d / show.sessions.size, 2)}m"),
          outerCell(s"${roundTo(show.sessions.map(_.duration).sum / 60d, 2)}m"),
          outerCell(
            show
              .airing
              .airing
              .startTime
              .atZone(ZoneId.of("UTC"))
              .withZoneSameInstant(ZoneId.of("America/New_York"))
              .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
          )
        ))
      )
    } else { Seq.empty[Modifier] },
    if (content.series.nonEmpty) {
      Seq[Modifier](
        tr(
          td(
            colspan := "6"
          )("Series")
        ),
        content.series.map[Modifier](episode => tr(
          outerCell(),
          outerCell(episode.airing.series.map(_.seriesTitle).getOrElse[String]("N/A")),
          outerCell(episode.airing.show.title),
          outerCell(episode.sessions.size),
          outerCell(s"${roundTo(episode.sessions.map(_.duration).sum / 60d / episode.sessions.size, 2)}m"),
          outerCell(s"${roundTo(episode.sessions.map(_.duration).sum / 60d, 2)}m"),
          outerCell(
            episode
              .airing
              .airing
              .startTime
              .atZone(ZoneId.of("UTC"))
              .withZoneSameInstant(ZoneId.of("America/New_York"))
              .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
          )
        )),
      )
    } else { Seq.empty[Modifier] },
    if (content.sports.nonEmpty) {
      Seq[Modifier](
        tr(
          td(
            colspan := "6"
          )("Sports")
        ),
        content.sports.map[Modifier](sport => tr(
          outerCell(),
          outerCell(sport.airing.sport.map(_.sportName).getOrElse[String]("N/A")),
          outerCell(sport.airing.show.title),
          outerCell(sport.sessions.size),
          outerCell(s"${roundTo(sport.sessions.map(_.duration).sum / 60d / sport.sessions.size, 2)}m"),
          outerCell(s"${roundTo(sport.sessions.map(_.duration).sum / 60d, 2)}m"),
          outerCell(
            sport
              .airing
              .airing
              .startTime
              .atZone(ZoneId.of("UTC"))
              .withZoneSameInstant(ZoneId.of("America/New_York"))
              .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
          )
        ))
      )
    } else { Seq.empty[Modifier] }
  )
}
