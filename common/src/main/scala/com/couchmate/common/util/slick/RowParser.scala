package com.couchmate.common.util.slick

import slick.jdbc.{GetResult, PositionedResult}

// @see https://github.com/joprice/shapeless-getresult/blob/master/src/main/scala/GenGetResult.scala
final case class RowParser[T](value: GetResult[T])

object RowParser {
  import shapeless._

  def apply[A <: Product](implicit g: RowParser[A]): GetResult[A] = g.value

  implicit val hnilGetResult: RowParser[HNil] = RowParser((_: PositionedResult) => HNil)

  implicit def hlistConsGetResult[H, T <: HList](
    implicit
    h: GetResult[H],
    t: RowParser[T],
  ): RowParser[H :: T] = RowParser((r: PositionedResult) => (r << h) :: t.value(r))

  implicit def mkResult[A <: Product, AR <: HList](
    implicit
    gen: Generic.Aux[A, AR],
    g: RowParser[AR],
  ): RowParser[A] =
    RowParser((r: PositionedResult) => gen.from(g.value(r)))
}