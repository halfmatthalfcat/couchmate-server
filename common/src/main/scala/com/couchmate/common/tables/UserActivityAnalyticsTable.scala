package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserActivityAnalytics
import com.couchmate.common.util.slick.WithTableQuery

import java.time.LocalDateTime

class UserActivityAnalyticsTable(tag: Tag) extends Table[UserActivityAnalytics](tag, "user_activity_analytics") {
  import slick.collection.heterogeneous._

  def reportDate = column[LocalDateTime]("report_date", O.PrimaryKey)
  def dau = column[Long]("dau")
  def prevDau = column[Long]("prev_dau")
  def prevWeekDau = column[Long]("prev_week_dau")
  def dauChange = column[Double]("dau_change")
  def dauChangeLastWeek = column[Double]("dau_change_last_week")
  def dauPerSessionMM = column[(Double, Double)]("dau_per_session_mm")
  def dauTotalSessionMM = column[(Double, Double)]("dau_total_session_mm")
  def prevDauPerSessionMM = column[(Double, Double)]("prev_dau_per_session_mm")
  def prevDauTotalSessionMM = column[(Double, Double)]("prev_dau_total_session_mm")
  def prevWeekDauPerSessionMM = column[(Double, Double)]("prev_week_dau_per_session_mm")
  def prevWeekDauTotalSessionMM = column[(Double, Double)]("prev_week_dau_total_session_mm")
  def dauPerSessionMMChange = column[(Double, Double)]("dau_per_session_mm_change")
  def dauTotalSessionMMChange = column[(Double, Double)]("dau_total_session_mm_change")
  def dauPerSessionMMChangeLastWeek = column[(Double, Double)]("dau_per_session_mm_change_last_week")
  def dauTotalSessionMMChangeLastWeek = column[(Double, Double)]("dau_total_session_mm_change_last_week")
  def wau = column[Long]("wau")
  def prevWau = column[Long]("prev_wau")
  def wauChange = column[Double]("wau_change")
  def wauPerSessionMM = column[(Double, Double)]("wau_per_session_mm")
  def wauTotalSessionMM = column[(Double, Double)]("wau_total_session_mm")
  def prevWauPerSessionMM = column[(Double, Double)]("prev_wau_per_session_mm")
  def prevWauTotalSessionMM = column[(Double, Double)]("prev_wau_total_session_mm")
  def wauPerSessionMMChange = column[(Double, Double)]("wau_per_session_mm_change")
  def wauTotalSessionMMChange = column[(Double, Double)]("wau_total_session_mm_change")
  def mau = column[Long]("mau")
  def prevMau = column[Long]("prev_mau")
  def mauChange = column[Double]("mau_change")
  def mauPerSessionMM = column[(Double, Double)]("mau_per_session_mm")
  def mauTotalSessionMM = column[(Double, Double)]("mau_total_session_mm")
  def prevMauPerSessionMM = column[(Double, Double)]("prev_mau_per_session_mm")
  def prevMauTotalSessionMM = column[(Double, Double)]("prev_mau_total_session_mm")
  def mauPerSessionMMChange = column[(Double, Double)]("mau_per_session_mm_change")
  def mauTotalSessionMMChange = column[(Double, Double)]("mau_total_session_mm_change")
  def dauMauRatio = column[Double]("dau_mau_ratio")

  def * = (
    reportDate ::
    dau ::
    prevDau ::
    prevWeekDau ::
    dauChange ::
    dauChangeLastWeek ::
    dauPerSessionMM ::
    dauTotalSessionMM ::
    prevDauPerSessionMM ::
    prevDauTotalSessionMM ::
    prevWeekDauPerSessionMM ::
    prevWeekDauTotalSessionMM ::
    dauPerSessionMMChange ::
    dauTotalSessionMMChange ::
    dauPerSessionMMChangeLastWeek ::
    dauTotalSessionMMChangeLastWeek ::
    wau ::
    prevWau ::
    wauChange ::
    wauPerSessionMM ::
    wauTotalSessionMM ::
    prevWauPerSessionMM ::
    prevWauTotalSessionMM ::
    wauPerSessionMMChange ::
    wauTotalSessionMMChange ::
    mau ::
    prevMau ::
    mauChange ::
    mauPerSessionMM ::
    mauTotalSessionMM ::
    prevMauPerSessionMM ::
    prevMauTotalSessionMM ::
    mauPerSessionMMChange ::
    mauTotalSessionMMChange ::
    dauMauRatio :: HNil
  ).mapTo[UserActivityAnalytics]
}

object UserActivityAnalyticsTable extends WithTableQuery[UserActivityAnalyticsTable] {
  private[couchmate] val table = TableQuery[UserActivityAnalyticsTable]
}