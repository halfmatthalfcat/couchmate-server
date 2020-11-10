package com.couchmate.services.room

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import akka.util.ByteString
import com.couchmate.common.models.api.room.message._
import com.couchmate.util.akka.extensions.RoomExtension
import io.lemonlabs.uri.{AbsoluteUrl, RelativeUrl, Url}
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object LinkScanner {
  sealed trait Command

  final case class ScanMessage(
    airingId: String,
    roomId: RoomId,
    message: TextMessage
  ) extends Command

  private final case class MessageScanned(
    airingId: String,
    roomId: RoomId,
    message: TextMessageWithLinks
  ) extends Command
  private final case class MessageScanFailed(ex: Throwable) extends Command

  private final case class MessageWithLinks(
    message: TextMessage,
    links: Seq[AbsoluteUrl]
  ) {
    def toTextMessageWithLinks(messageLinks: List[MessageLink]): TextMessageWithLinks = TextMessageWithLinks(
      messageId = message.messageId,
      message = message.message,
      author = message.author,
      reactions = message.reactions,
      isSelf = message.isSelf,
      isOnlyLink = this.isOnlyLink(message.message),
      links = messageLinks
    )

    private def isOnlyLink(message: String): Boolean =
      message
        .split(" ")
        .map(Url.parseTry)
        .collect { case Failure(exception) => exception }
        .isEmpty
  }

  private final case class ResponseWithLink(
    link: AbsoluteUrl,
    response: HttpResponse
  )

  def apply(): Behavior[Command] = Behaviors.setup { implicit ctx =>
    implicit val http: HttpExt = Http(ctx.system)
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = Materializer(ctx.system)

    val room: RoomExtension = RoomExtension(ctx.system)

    Behaviors.receiveMessage {
      case ScanMessage(airingId, roomId, message) => ctx.pipeToSelf(fetchLinks(MessageWithLinks(
          message = message,
          links = getLinks(message.message)
        ))) {
          case Success(value) => MessageScanned(airingId, roomId, value)
          case Failure(exception) => MessageScanFailed(exception)
        }
        Behaviors.same
      case MessageScanned(airingId, roomId, message) => room.linkMessage(
          airingId,
          roomId,
          message
        )
        Behaviors.same
      case MessageScanFailed(ex) =>
        ctx.log.error("Failed to get links for message", ex)
        Behaviors.same
    }
  }

  def getLinks(message: String): Seq[AbsoluteUrl] =
    message
      .split(" ")
      .map(Url.parseTry)
      .collect { case Success(url: AbsoluteUrl) => url }

  def hasLinks(message: String): Boolean =
    getLinks(message).nonEmpty

  private[this] def fetchLinks(message: MessageWithLinks)(
    implicit
    ctx: ActorContext[Command],
    http: HttpExt,
    ec: ExecutionContext,
    mat: Materializer
  ): Future[TextMessageWithLinks] =
    Future.sequence(message.links.map(link => http.singleRequest(HttpRequest(
      uri = Uri.from(
        scheme = link.scheme,
        host = link.host.value,
        path = link.path.toString,
        queryString = if (link.query.nonEmpty) Some(link.query.toString) else Option.empty
      )
    )).map(ResponseWithLink(link, _)))).flatMap(results => Future.sequence(results.collect {
      case ResponseWithLink(link, HttpResponse(StatusCodes.OK, headers, entity, _)) if (
          entity.contentType.mediaType.toString.equals("image/png") ||
          entity.contentType.mediaType.toString.equals("image/jpeg") ||
          entity.contentType.mediaType.toString.equals("image/bmp") ||
          entity.contentType.mediaType.toString.equals("image/gif") ||
          entity.contentType.mediaType.toString.equals("image/webp")
        ) => entity.discardBytes().future().flatMap(_ => Future.successful(List(ImageLink(link.toString))))
      case ResponseWithLink(link, HttpResponse(StatusCodes.OK, headers, entity, _)) if (
          entity.contentType.mediaType.toString.equals("text/html")
        ) => entity.toStrict(5 seconds)
                   .flatMap(_.dataBytes.runFold(ByteString.empty)(_ ++ _))
                   .map(byteString => List(getSiteLink(link, byteString.utf8String)))
      case ResponseWithLink(link, response) =>
        ctx.log.debug(s"Got response for ${link.toString}\n${response.headers.map(h => s"${h.name}:${h.value}").mkString("\n")}\nResponse Type: ${response.entity.contentType.mediaType.toString}")
        response.entity.discardBytes().future().map(_ => List.empty[MessageLink])
    }).map(_.foldLeft(List.empty[MessageLink])(_ ++ _)).map(links => message.toTextMessageWithLinks(links)))

  private[this] def getSiteLink(
    link: AbsoluteUrl,
    html: String
  ): SiteLink = {
    import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
    import net.ruippeixotog.scalascraper.dsl.DSL._

    val browser: Browser = JsoupBrowser()
    val doc = browser.parseString(html)
    SiteLink(
      url = link.toString,
      host = link.host.value,
      title = (doc.head >> elements("meta")).find(_.attrs.exists {
        case ("name" | "property", "og:title") => true
        case _ => false
      }).map(_.attr("content")).getOrElse(doc.head >> text("title")),
      siteName = (doc.head >> elements("meta")).find(_.attrs.exists {
        case ("name" | "property", "og:site_name") => true
        case _ => false
      }).map(_.attr("content")),
      description = (doc.head >> elements("meta")).find(_.attrs.exists {
        case ("name" | "property", "description") => true
        case ("name" | "property", "Description") => true
        case ("name" | "property", "og:description") => true
        case _ => false
      }).map(_.attr("content")),
      imageUrl = (doc.head >> elements("meta")).find(_.attrs.exists {
        case ("name" | "property", "og:image") => true
        case _ => false
      }).map(_.attr("content")),
      faviconUrl = (doc.head >> elements("link")).find(_.attrs.exists {
        case ("rel", "icon") => true
        case ("rel", "shortcut icon") => true
        case ("rel", "apple-touch-icon") => true
        case _ => false
      }).flatMap(el => Url.parseTry(el.attr("href")) match {
        case Success(url: AbsoluteUrl) => Some(url.toString)
        case Success(url: RelativeUrl) => Some(s"${link.scheme}://${link.host.value}${url.path}")
        case _ => Option.empty
      })
    )
  }
}
