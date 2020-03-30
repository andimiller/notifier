package net.andimiller.notifier

import cats.data.OptionT
import cats.implicits._
import cats.effect.Sync
import scalacache._
import scalacache.CatsEffect.modes._
import scalacache.caffeine.CaffeineCache
import scalacache.serialization.circe._
import scalacache.memoization._
import cats.effect.Async
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration._

case class AccessToken(
    characterId: Long,
    characterName: String,
    accessToken: String
)

trait TokenManager[F[_]] {
  def getToken(refreshToken: String): F[AccessToken]
}

object TokenManager {
  def apply[F[_]: Async](oauth: OAuth2[F]): F[TokenManager[F]] =
    Async[F].delay { CaffeineCache[AccessToken](CacheConfig()) }.map { cache => (refreshToken: String) =>
      OptionT(cache.get[F](refreshToken)).getOrElseF(
        oauth.refresh(refreshToken).flatMap {
          case (v, r) =>
            val token = AccessToken(v.CharacterID, v.CharacterName, r.access_token)
            cache.put[F](refreshToken)(token, Some(r.expires_in.seconds)).as(token)
        }
      )
    }
}
