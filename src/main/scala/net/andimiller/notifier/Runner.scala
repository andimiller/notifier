package net.andimiller.notifier

import cats.implicits._
import cats.effect._
import io.chrisdavenport.log4cats.Logger
import fs2._

import scala.concurrent.duration._

object Runner {
  val notificationTtl: FiniteDuration = 10.minutes

  def apply[F[_]: Timer: Sync: Logger](
      tokens: List[String],
      tokenManager: TokenManager[F],
      notifications: Notifications[F]
  ): Stream[F, Unit] = {
    val tick: FiniteDuration = (notificationTtl / tokens.length) + 1.second
    for {
      _ <- Stream.eval(
            Logger[F].info(s"Scheduling jobs, got ${tokens.length} tokens across ${notificationTtl} window, using ${tick} tick rate")
          )
      (tick, token) <- (Stream.emit[F, FiniteDuration](0.seconds) ++ Stream.awakeEvery[F](tick))
                        .zip(Stream.emits(tokens).repeat)
      _ <- Stream.eval(Logger[F].info(s"tick: $tick"))
      (at, notifs) <- Stream.eval(
                       tokenManager.getToken(token).flatMap { at => notifications.getNotifications(at).tupleLeft(at) }
                     )
      _ <- Stream.eval(Logger[F].info(s"notifs for ${at.characterName}: ${notifs}"))
    } yield ()
  }

}
