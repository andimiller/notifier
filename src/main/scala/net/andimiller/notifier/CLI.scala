package net.andimiller.notifier

import java.nio.file.Path

import cats.data.ValidatedNel
import com.monovore.decline._
import org.http4s.implicits._
import org.http4s.Uri
import cats.implicits._
import com.monovore.decline._

object CLI {
  implicit val uriArg: Argument[Uri] = new Argument[Uri] {
    override def read(string: String): ValidatedNel[String, Uri] = Uri.fromString(string).leftMap(_.message).toValidatedNel
    override def defaultMetavar: String                          = "https://foo/"
  }

  case class EnvConfig(
      tokenFile: Path,
      esiId: String,
      esiSecret: String,
      esiBaseUri: Uri,
      eveLoginTokenUri: Uri,
      eveLoginVerifyUri: Uri,
      redisHost: String,
      redisPort: Int
  )

  val config: Opts[EnvConfig] =
    (
      Opts.argument[Path]("tokenfile.json"),
      Opts.env[String]("ESI_ID", "Id for the ESI application"),
      Opts.env[String]("ESI_SECRET", "Secret for the ESI application"),
      Opts.env[Uri]("ESI_BASE_URI", "Base URI for ESI").withDefault(uri"https://esi.evetech.net"),
      Opts.env[Uri]("EVE_LOGIN_TOKEN_URI", "Token URI for EVE Login").withDefault(uri"https://login.eveonline.com/v2/oauth/token"),
      Opts.env[Uri]("EVE_LOGIN_VERIFY_URI", "Verify URI for EVE Login").withDefault(uri"https://esi.evetech.net/verify"),
      Opts.env[String]("REDIS_HOST", "Redis hostname").withDefault("localhost"),
      Opts.env[Int]("REDIS_PORT", "Redis port").withDefault(6379)
    ).mapN(EnvConfig)

  sealed trait Mode
  case class Audit(config: EnvConfig) extends Mode
  case class Run(config: EnvConfig)   extends Mode

  val audit: Opts[Audit] = Opts.subcommand(Command("audit", "audit the keys", true)(config.map(Audit)))
  val run: Opts[Run]     = Opts.subcommand(Command("run", "run the notifier service", true)(config.map(Run)))

  val main: Command[Mode] = Command("notifier", "eve online notifier service")(audit orElse run)

}
