package net.andimiller.notifier

import java.time.ZonedDateTime

import cats.{Applicative, Eval, Functor, Traverse}
import cats.implicits._
import cats.effect.Sync
import io.circe.{Decoder, Json}
import io.circe.generic.semiauto._
import org.http4s.{AuthScheme, Credentials, Headers, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.circe.CirceEntityCodec._
import org.http4s.headers.Authorization

case class Notification[T](
                       notification_id: Long,
                       sender_id: Long,
                       sender_type: String,
                       value: T,
                       timestamp: ZonedDateTime,
                       `type`: String
                       )
object Notification {
  implicit val func: Functor[Notification] = new Functor[Notification] {
    override def map[A, B](fa: Notification[A])(f: A => B): Notification[B] =
      fa.copy(value = f(fa.value))
  }
  implicit val trav: Traverse[Notification] = new Traverse[Notification] {
    override def traverse[G[_], A, B](fa: Notification[A])(f: A => G[B])(implicit evidence$1: Applicative[G]): G[Notification[B]] =
      f(fa.value).map { b =>
        fa.copy(value = b)
      }
    override def foldLeft[A, B](fa: Notification[A], b: B)(f: (B, A) => B): B =
      f(b, fa.value)
    override def foldRight[A, B](fa: Notification[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      f(fa.value, lb)
  }

  implicit def dec[T: Decoder]: Decoder[Notification[T]] = Decoder { c =>
    (
      c.downField("notification_id").as[Long],
      c.downField("sender_id").as[Long],
      c.downField("sender_type").as[String],
      Decoder[String]
        .emap(io.circe.yaml.parser.parse(_).leftMap(_.message))
        .emap(j => Decoder[T].tryDecode(j.hcursor).leftMap(_.message))
        .tryDecode(c.downField("text")),
      c.downField("timestamp").as[ZonedDateTime],
      c.downField("type").as[String]
    ).mapN(Notification.apply)
  }
}

trait Notifications[F[_]] {
  def getNotifications(accessToken: AccessToken): F[List[Notification[Json]]]
}

object Notifications {
  def apply[F[_]: Sync](esiBaseUri: Uri, client: Client[F]): Notifications[F] = {
    new Notifications[F] {
      override def getNotifications(accessToken: AccessToken): F[List[Notification[Json]]] =
        client.expect[List[Notification[Json]]](
          Request[F](
            Method.GET,
            esiBaseUri / "v5" / "characters" / accessToken.characterId.toString / "notifications" / "",
            headers = Headers.of(
              Authorization(
                Credentials.Token(
                  AuthScheme.Bearer, accessToken.accessToken
                )
              )
            )
          )
        )
    }

  }
}