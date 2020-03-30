package net.andimiller.notifier

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.global

object Main extends IOApp {
  implicit val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](getClass)
  override def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource
      .flatMap { client =>
        Resource
          .liftF(
            TokenManager.apply[IO](
              new OAuth2[IO](
                client,
                Uri.unsafeFromString("https://login.eveonline.com/v2/oauth/token"),
                Uri.unsafeFromString("https://esi.evetech.net/verify"),
                "",
                ""
              )
            )
          )
          .tupleLeft(Notifications[IO](Uri.unsafeFromString("https://esi.evetech.net"), client))
      }
      .use {
        case (notifs, tokens) =>
          Runner[IO](
            List(""),
            tokens,
            notifs
          ).compile.drain.as(ExitCode.Success)
      }
}
