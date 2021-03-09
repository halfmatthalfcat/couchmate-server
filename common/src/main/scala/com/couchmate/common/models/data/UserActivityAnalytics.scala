package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}
import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx

import java.time.LocalDateTime

case class UserActivityAnalytics(
  reportDate: LocalDateTime,
  // Daily Active Users (DAU)
  dau: Long,
  // Previous Day's DAU
  prevDau: Long,
  // Previous Week's DAU
  prevWeekDau: Long,
  // Change in DAU
  dauChange: Double,
  // Change in DAU from last week
  dauChangeLastWeek: Double,
  // DAU per session length mean/median
  dauPerSessionMM: (Double, Double),
  // DAI total session length mean/median
  dauTotalSessionMM: (Double, Double),
  // Previous Day's DAU per session length mean/median
  prevDauPerSessionMM: (Double, Double),
  // Previous Day's DAU total session length mean/median
  prevDauTotalSessionMM: (Double, Double),
  // Previous Week's DAU per session length mean/median
  prevWeekDauPerSessionMM: (Double, Double),
  // Previous Week's DAU total session length mean/median
  prevWeekDauTotalSessionMM: (Double, Double),
  // Change in DAU per session length mean/median
  dauPerSessionMMChange: (Double, Double),
  // Change in DAU total session length mean/median
  dauTotalSessionMMChange: (Double, Double),
  // Change in DAU per session last week length mean/median
  dauPerSessionMMChangeLastWeek: (Double, Double),
  // Change in DAU total session last week length mean/median
  dauTotalSessionMMChangeLastWeek: (Double, Double),
  // Weekly Active Users (WAU)
  wau: Long,
  // Previous Week's WAU
  prevWau: Long,
  // Change in WAU
  wauChange: Double,
  // WAU per session length mean/median
  wauPerSessionMM: (Double, Double),
  // WAU total session length mean/median
  wauTotalSessionMM: (Double, Double),
  // Previous Week's WAU per session length mean/median
  prevWauPerSessionMM: (Double, Double),
  // Previous Week's WAU total session length mean/median
  prevWauTotalSessionMM: (Double, Double),
  // Change in WAU per session length mean/median
  wauPerSessionMMChange: (Double, Double),
  // Change in WAU total session length mean/median
  wauTotalSessionMMChange: (Double, Double),
  // Monthly Active Users (MAU)
  mau: Long,
  // Previous Month's MAU
  prevMau: Long,
  // Change in MAU
  mauChange: Double,
  // MAU per session length mean/median
  mauPerSessionMM: (Double, Double),
  // MAU total session length mean/median
  mauTotalSessionMM: (Double, Double),
  // Previous Month's MAU per session length mean/median
  prevMauPerSessionMM: (Double, Double),
  // Previous Month's MAU total session length mean/median
  prevMauTotalSessionMM: (Double, Double),
  // Change in MAU per session length mean/median
  mauPerSessionMMChange: (Double, Double),
  // Change in MAU total session length mean/median
  mauTotalSessionMMChange: (Double, Double),
  // DAU/MAU ratio
  dauMauRatio: Double
)

object UserActivityAnalytics {
  implicit val format: Format[UserActivityAnalytics] = Jsonx.formatCaseClass[UserActivityAnalytics]
}