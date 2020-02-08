package com.couchmate.data.db

import io.getquill.{ idiom => _, _ }

class CMContext(context: String)
  extends PostgresAsyncContext[SnakeCase.type](SnakeCase, context) {

  implicit class ComparisonInfix[T: Encoder](left: T) {
    def >(right: T) =
      quote(infix"$right > $left".as[Boolean])
    def <(right: T) =
      quote(infix"$right < $left".as[Boolean])
    def >=(right: T) =
      quote(infix"$right >= $left".as[Boolean])
    def <=(right: T) =
      quote(infix"$right <= $left".as[Boolean])
    def <>(right: (T, T)) =
      quote(infix"$left BETWEEN ${right._1} AND ${right._2}".as[Boolean])
  }
}
