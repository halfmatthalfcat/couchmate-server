package com.couchmate.data.schema

import slick.jdbc.{GetResult, PositionedResult}

final case class GenGetResult[T](value: GetResult[T])

/**
 * Thanks to @joprice, the OG
 * @see https://github.com/joprice/shapeless-getresult/blob/master/src/main/scala/GenGetResult.scala
 */
object GenGetResult {
  import shapeless._

  def apply[A](implicit g: GenGetResult[A]): GetResult[A] = g.value

  implicit val hnilGetResult: GenGetResult[HNil] =
    GenGetResult((_: PositionedResult) => HNil)

  implicit def hlistConsGetResult[H, T <: HList](
    implicit
    h: GetResult[H],
    t: GenGetResult[T],
  ): GenGetResult[H :: T] = GenGetResult((r: PositionedResult) =>
    (r << h) :: t.value(r)
  )

  implicit def mkResult[A <: Product, AR <: HList](
    implicit
    gen: Generic.Aux[A, AR],
    g: GenGetResult[AR],
  ): GenGetResult[A] = GenGetResult((r: PositionedResult) => gen.from(g.value(r)))

}
