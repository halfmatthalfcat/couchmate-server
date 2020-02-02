package com.couchmate.data.db

import io.getquill.{PostgresJdbcContext, SnakeCase}

class CMContext(context: String)
  extends PostgresJdbcContext[SnakeCase.type](SnakeCase, context) {

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
