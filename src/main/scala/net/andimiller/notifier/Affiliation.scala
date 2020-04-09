package net.andimiller.notifier

import cats.implicits._
import cats.data.EitherT
import cats.effect.{Async, Sync}
import enumeratum._
import io.circe.{Decoder, Json}
import org.http4s.client.Client
import org.http4s.{AuthScheme, Credentials, Headers, Method, Request, Uri}
import org.http4s.headers.Authorization
import org.http4s.circe.CirceEntityCodec._
import io.circe.generic.auto._
import scalacache.redis.RedisCache
import scalacache.serialization.circe._
import scalacache.CatsEffect.modes._
import scala.concurrent.duration._

case class AffiliationResponse(alliance_id: Option[Long], character_id: Long, corporation_id: Long)

trait Affiliation[F[_]] {
  def getAffiliations(ids: List[Long]): F[Map[Long, AffiliationResponse]]
}

object Affiliation {
  def apply[F[_]: Sync](esiBaseUri: Uri, client: Client[F]): Affiliation[F] =
    new Affiliation[F] {
      def getAffiliations(ids: List[Long]): F[Map[Long, AffiliationResponse]] =
        client
          .expect[List[AffiliationResponse]](
            Request[F](
              Method.POST,
              esiBaseUri / "v1" / "characters" / "affiliation" / ""
            ).withEntity(ids.distinct)
          )
          .map(_.groupBy(_.character_id).transform { case (_, as) => as.head })
    }

  def cached[F[_]: Async](underlying: Affiliation[F], redisHost: String): F[Affiliation[F]] = {
    Async[F]
      .delay {
        RedisCache[AffiliationResponse](redisHost, 6379)
      }
      .map { cache => (ids: List[Long]) =>
        ids
          .traverse { id =>
            EitherT
              .fromOptionF(
                cache.get[F]("affiliation", id).map(_.tupleLeft(id)),
                id
              )
              .value
          }
          .flatMap { rs =>
            val (miss, hit) = rs.separate
            miss.distinct
              .grouped(10)
              .toList
              .traverse { missIds => underlying.getAffiliations(missIds) }
              .map(_.toList.flatten.toMap)
              .flatTap { results =>
                results.toList.traverse {
                  case (id, aff) =>
                    cache.put[F]("affiliation", id)(aff, 1.day.some)
                }
              }
              .map { results =>
                val hitMap = hit.toMap
                ids.map { i => i -> hitMap.get(i).orElse(results.get(i)).get }.toMap
              }
          }
      }
  }
}
