package net.andimiller.notifier

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.RequestLogger
import org.http4s.implicits._
import com.monovore.decline._

import scala.concurrent.ExecutionContext.global

object Main extends IOApp {
  implicit val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](getClass)

  case class Resources[F[_]](
      refreshTokens: List[String],
      tokens: TokenManager[F],
      character: Character[F],
      notifications: Notifications[F],
      affiliation: Affiliation[F],
      names: NameManager[F]
  )

  def makeResources(config: CLI.EnvConfig): Resource[IO, Resources[IO]] =
    (
      BlazeClientBuilder[IO](global).resource,
      Blocker[IO]
    ).tupled
      .map {
        case (client, blocker) =>
          RequestLogger[IO](true, true)(client) -> blocker
      }
      .flatMap {
        case (client, blocker) =>
          Resource.liftF(
            for {
              tokens <- TokenManager[IO](
                         new OAuth2[IO](
                           client,
                           config.eveLoginTokenUri,
                           config.eveLoginVerifyUri,
                           config.esiId,
                           config.esiSecret
                         ),
                         config.redisHost
                       )
              refreshTokens <- TokenLoader.readLines[IO](config.tokenFile, blocker).compile.toList
              character     = Character[IO](config.esiBaseUri, client)
              notifications = Notifications[IO](config.esiBaseUri, client)
              affiliation <- Affiliation.cached[IO](
                              Affiliation[IO](config.esiBaseUri, client),
                              config.redisHost
                            )
              names <- NameManager.cached[IO](
                        NameManager[IO](config.esiBaseUri, client),
                        config.redisHost
                      )
            } yield Resources(refreshTokens, tokens, character, notifications, affiliation, names)
          )
      }

  override def run(args: List[String]): IO[ExitCode] =
    CLI.main.parse(args, sys.env) match {
      case Left(value) =>
        IO {
          println(value.toString())
        }.as(ExitCode.Error)
      case Right(CLI.Audit(config)) =>
        makeResources(config).use { res => Audit[IO](res).as(ExitCode.Success) }
      case Right(CLI.Run(config)) =>
        IO(ExitCode.Success)
    }

}
