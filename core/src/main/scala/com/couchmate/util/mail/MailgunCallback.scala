package com.couchmate.util.mail

import net.sargue.mailgun
import net.sargue.mailgun.{MailRequestCallback, Response}

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object MailgunCallback {
  def apply(p: Promise[Boolean]): MailRequestCallback = new MailRequestCallback {
    override def completed(response: mailgun.Response): Unit = {
      if (response.isOk) {
        p.complete(Success(true))
      } else {
        p.complete(Failure(new RuntimeException(response.responseMessage)))
      }
    }

    override def failed(throwable: Throwable): Unit =
      p.complete(Failure(throwable))
  }
}
