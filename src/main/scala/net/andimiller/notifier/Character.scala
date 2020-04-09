package net.andimiller.notifier

import cats.effect.Sync
import enumeratum._
import io.circe.{Decoder, Json}
import org.http4s.client.Client
import org.http4s.{AuthScheme, Credentials, Headers, Method, Request, Uri}
import org.http4s.headers.Authorization
import org.http4s.circe.CirceEntityCodec._

sealed trait Role extends EnumEntry

case object Role extends Enum[Role] with CirceEnum[Role] {
  case object Account_Take_1            extends Role
  case object Account_Take_2            extends Role
  case object Account_Take_3            extends Role
  case object Account_Take_4            extends Role
  case object Account_Take_5            extends Role
  case object Account_Take_6            extends Role
  case object Account_Take_7            extends Role
  case object Accountant                extends Role
  case object Auditor                   extends Role
  case object Communications_Officer    extends Role
  case object Config_Equipment          extends Role
  case object Config_Starbase_Equipment extends Role
  case object Container_Take_1          extends Role
  case object Container_Take_2          extends Role
  case object Container_Take_3          extends Role
  case object Container_Take_4          extends Role
  case object Container_Take_5          extends Role
  case object Container_Take_6          extends Role
  case object Container_Take_7          extends Role
  case object Contract_Manager          extends Role
  case object Diplomat                  extends Role
  case object Director                  extends Role
  case object Factory_Manager           extends Role
  case object Fitting_Manager           extends Role
  case object Hangar_Query_1            extends Role
  case object Hangar_Query_2            extends Role
  case object Hangar_Query_3            extends Role
  case object Hangar_Query_4            extends Role
  case object Hangar_Query_5            extends Role
  case object Hangar_Query_6            extends Role
  case object Hangar_Query_7            extends Role
  case object Hangar_Take_1             extends Role
  case object Hangar_Take_2             extends Role
  case object Hangar_Take_3             extends Role
  case object Hangar_Take_4             extends Role
  case object Hangar_Take_5             extends Role
  case object Hangar_Take_6             extends Role
  case object Hangar_Take_7             extends Role
  case object Junior_Accountant         extends Role
  case object Personnel_Manager         extends Role
  case object Rent_Factory_Facility     extends Role
  case object Rent_Office               extends Role
  case object Rent_Research_Facility    extends Role
  case object Security_Officer          extends Role
  case object Starbase_Defense_Operator extends Role
  case object Starbase_Fuel_Technician  extends Role
  case object Station_Manager           extends Role
  case object Trader                    extends Role

  val values: IndexedSeq[Role] = findValues

  val canSeeNotifications: Set[Role] = Set(Starbase_Defense_Operator, Director, Config_Starbase_Equipment)
}

case class RolesResponse(roles: Set[Role])
object RolesResponse {
  import io.circe.generic.semiauto._
  implicit val dec: Decoder[RolesResponse] = deriveDecoder
}

trait Character[F[_]] {
  def getRoles(accessToken: AccessToken): F[RolesResponse]
}

object Character {
  def apply[F[_]: Sync](esiBaseUri: Uri, client: Client[F]): Character[F] =
    new Character[F] {
      def getRoles(accessToken: AccessToken): F[RolesResponse] =
        client.expect[RolesResponse](
          Request[F](
            Method.GET,
            esiBaseUri / "v2" / "characters" / accessToken.characterId.toString / "roles" / "",
            headers = Headers.of(
              Authorization(
                Credentials.Token(
                  AuthScheme.Bearer,
                  accessToken.accessToken
                )
              )
            )
          )
        )

    }
}
