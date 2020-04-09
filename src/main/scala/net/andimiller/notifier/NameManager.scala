package net.andimiller.notifier

import cats.data.{EitherT, OptionT}
import cats.effect._
import org.http4s.{Method, Request, Uri}
import scalacache.CacheConfig
import scalacache.caffeine.CaffeineCache
import scalacache.redis._
import scalacache.serialization.circe._
import cats.implicits._
import org.http4s.client.Client
import scalacache.CatsEffect.modes._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import scala.concurrent.duration._

trait NameManager[F[_]] {
  def getName(ids: List[Long]): F[Map[Long, String]]
}

case class Mapping(id: Long, name: String)

object NameManager {
  def apply[F[_]: Sync](esiBase: Uri, client: Client[F]) = new NameManager[F] {
    override def getName(ids: List[Long]): F[Map[Long, String]] =
      client
        .expect[List[Mapping]](
          Request[F](
            Method.POST,
            esiBase / "v3" / "universe" / "names"
          ).withEntity(ids.distinct)
        )
        .map { ms => ms.groupBy(_.id).transform { case (_, v) => v.head.name } }
  }

  def cached[F[_]: Async](underlying: NameManager[F], redisHost: String): F[NameManager[F]] = {
    Async[F]
      .delay {
        RedisCache[Option[String]](redisHost, 6379)
      }
      .map { cache => (ids: List[Long]) =>
        ids
          .traverse { id =>
            EitherT
              .fromOptionF(
                cache.get[F]("name", id).map(_.tupleLeft(id)),
                id
              )
              .value
          }
          .flatMap { rs =>
            val (miss, hit) = rs.separate
            miss
              .grouped(10)
              .toList
              .traverse { missIds => underlying.getName(missIds) }
              .map(_.toList.flatten.toMap)
              .flatTap { results => miss.traverse { missId => cache.put[F]("name", missId)(results.get(missId), 1.day.some) } }
              .map { results =>
                val hitMap = hit.toMap
                ids.map { i => i -> hitMap.get(i).orElse(results.get(i).some).flatten.getOrElse("unknown") }.toMap
              }
          }
      }
  }
}
