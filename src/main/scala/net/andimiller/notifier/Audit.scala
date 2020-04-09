package net.andimiller.notifier

import cats.data.OptionT
import cats.implicits._
import cats.effect.{Sync, Timer}
import io.chrisdavenport.log4cats.Logger
import scala.concurrent.duration._

object Audit {

  def apply[F[_]: Sync: Logger: Timer](resources: Main.Resources[F]) =
    resources.refreshTokens
      .traverse { token =>
        resources.tokens.getToken(token).attemptT.leftSemiflatMap(t => Logger[F].warn(t)("can't refresh").as("bad")).value
      }
      .flatMap { r =>
        val (bad, good) = r.separate

        good.traverseFilter { at =>
          resources.character
            .getRoles(at)
            .map(_.roles.intersect(Role.canSeeNotifications))
            .map { goodroles =>
              if (goodroles.nonEmpty)
                (at -> goodroles).some
              else none
            }
            .flatTap { r => r.traverse { stuff => Sync[F].delay { println(stuff) } } }
        }
      }
      .flatMap { peopleWithRoles =>
        for {
          _            <- Sync[F].delay { println(s"${peopleWithRoles.length} keys with good roles") }
          affiliations <- resources.affiliation.getAffiliations(peopleWithRoles.map(_._1.characterId))
          affils       = peopleWithRoles.mapFilter { case (at, _) => affiliations.get(at.characterId) }
          bycorp       = affils.groupMap(k => (k.corporation_id, k.alliance_id))(_.character_id)
          names        <- resources.names.getName(bycorp.keys.toList.map(_._1) ++ bycorp.keys.toList.flatMap(_._2) ++ bycorp.values.toList.flatten)
          res = bycorp.toList.sortBy(_._1._1).map {
            case ((corp, alli), chars) =>
              (names(corp), alli.map(names(_)), chars.map(names))
          }
        } yield res
      }
      .flatMap { res =>
        Sync[F].delay {
          println(
            res
              .sortBy(0 - _._3.length)
              .map {
                case (corp, alli, members) =>
                  s"${alli.getOrElse("none")} - $corp - ${members.length} keys"
              }
              .mkString("\n")
          )
        }
      }
}
