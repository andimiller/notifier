package net.andimiller.notifier

import java.nio.file.{Path, Paths}

import cats.implicits._
import cats.effect.{Blocker, ContextShift, ExitCode, IO, IOApp, Sync}
import fs2.io.file.readAll
import fs2.Stream

object TokenLoader {
  def read[F[_]: Sync: ContextShift](path: Path, blocker: Blocker): Stream[F, String] =
    for {
      line  <- readAll[F](path, blocker, 1024).through(fs2.text.utf8Decode[F]).through(fs2.text.lines[F])
      json  <- Stream.eval(Sync[F].fromEither(io.circe.parser.parse(line).leftWiden[Throwable]))
      token <- Stream.eval(Sync[F].fromEither(json.hcursor.downField("refresh_token").as[String]))
    } yield token
  def readLines[F[_]: Sync: ContextShift](path: Path, blocker: Blocker): Stream[F, String] =
    for {
      line <- readAll[F](path, blocker, 1024).through(fs2.text.utf8Decode[F]).through(fs2.text.lines[F])
    } yield line
}

object TokenRun extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      TokenLoader.read[IO](Paths.get("tokens.json"), blocker).evalTap(t => IO { println(t) }).compile.drain.as(ExitCode.Success)
    }
}
