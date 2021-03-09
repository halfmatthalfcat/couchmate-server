package com.couchmate.migration.migrations

import com.couchmate.common.tables.UserActivityAnalyticsTable
import com.couchmate.migration.db.MigrationItem

object UserActivityAnalyticsMigrations {
  val init = MigrationItem(54L, UserActivityAnalyticsTable.table)(
    _.create.addColumns(
      _.reportDate,
      _.dau,
      _.prevDau,
      _.prevWeekDau,
      _.dauChange,
      _.dauChangeLastWeek,
      _.dauPerSessionMM,
      _.dauTotalSessionMM,
      _.prevDauPerSessionMM,
      _.prevDauTotalSessionMM,
      _.prevWeekDauPerSessionMM,
      _.prevWeekDauTotalSessionMM,
      _.dauPerSessionMMChange,
      _.dauTotalSessionMMChange,
      _.dauPerSessionMMChangeLastWeek,
      _.dauTotalSessionMMChangeLastWeek,
      _.wau,
      _.prevWau,
      _.wauChange,
      _.wauPerSessionMM,
      _.wauTotalSessionMM,
      _.prevWauPerSessionMM,
      _.prevWauTotalSessionMM,
      _.wauPerSessionMMChange,
      _.wauTotalSessionMMChange,
      _.mau,
      _.prevMau,
      _.mauChange,
      _.mauPerSessionMM,
      _.mauTotalSessionMM,
      _.prevMauPerSessionMM,
      _.prevMauTotalSessionMM,
      _.mauPerSessionMMChange,
      _.mauTotalSessionMMChange,
      _.dauMauRatio
    )
  )()
}
