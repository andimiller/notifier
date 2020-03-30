package net.andimiller.notifier

import cats.implicits._
import cats.effect.Sync
import io.circe.Json
import org.http4s.circe.{CirceEntityCodec, CirceEntityDecoder}
import org.http4s.{headers, AuthScheme, BasicCredentials, Credentials, MediaType, Method, Request, Uri, UrlForm}
import org.http4s.client.Client
import org.http4s.headers.{`Content-Type`, Authorization, Host}
import io.circe.generic.auto._

case class Refreshed(
    access_token: String,
    expires_in: Int,
    token_type: String,
    refresh_token: String
)

case class Verified(
    CharacterID: Long,
    CharacterName: String
)

class OAuth2[F[_]: Sync](
    client: Client[F],
    tokenUri: Uri,
    verifyUri: Uri,
    clientId: String,
    clientSecret: String
) extends CirceEntityDecoder {
  def refresh(refreshToken: String): F[(Verified, Refreshed)] =
    client
      .expect[Refreshed](
        Request[F](
          Method.POST,
          tokenUri
        ).withEntity(
            UrlForm(
              "grant_type"    -> "refresh_token",
              "refresh_token" -> refreshToken
            )
          )
          .putHeaders(
            Authorization(
              BasicCredentials(
                clientId,
                clientSecret
              )
            )
          )
      )
      .flatMap { r =>
        client
          .expect[Verified](
            Request[F](
              Method.GET,
              verifyUri
            ).putHeaders(
              Authorization(Credentials.Token(AuthScheme.Bearer, r.access_token))
            )
          )
          .tupleRight(r)
      }
}
