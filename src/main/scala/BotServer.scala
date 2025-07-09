import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import sttp.client4._
import sttp.client4.circe._
import scala.collection.concurrent.TrieMap

object BotServer extends IOApp.Simple {
  // Only require the bot token
  val token = sys.env.getOrElse("DISCORD_TOKEN", "YOUR_BOT_TOKEN")
  val applicationId = sys.env.get("DISCORD_APP_ID") // Optional, can be extracted from token

  val backend = DefaultSyncBackend()

  // In-memory storage for responses
  val responses = TrieMap.empty[String, String]

  // Register the slash command globally (no guild ID needed)
  def registerCommand(): Unit = {
    val commandJson = Json.obj(
      "name" -> Json.fromString("ask"),
      "description" -> Json.fromString("Ask a yes/no question"),
      "options" -> Json.arr(
        Json.obj(
          "type" -> Json.fromInt(3), // STRING
          "name" -> Json.fromString("question"),
          "description" -> Json.fromString("The question to ask"),
          "required" -> Json.fromBoolean(true)
        )
      )
    )

    // Get application ID from token if not provided
    val appId = applicationId.getOrElse {
      // Extract from bot token or get from Discord API
      val botInfoReq = basicRequest
        .get(uri"https://discord.com/api/v10/applications/@me")
        .header("Authorization", s"Bot $token")
        .response(asJson[Json])
      val botInfo = botInfoReq.send(backend)
      botInfo.body.fold(
        _ => throw new RuntimeException("Failed to get bot info"),
        json => json.hcursor.get[String]("id").getOrElse(throw new RuntimeException("No app ID found"))
      )
    }

    // Register command globally (works in all guilds)
    val req = basicRequest
      .post(uri"https://discord.com/api/v10/applications/$appId/commands")
      .header("Authorization", s"Bot $token")
      .body(commandJson)
      .response(asJson[Json])
    val resp = req.send(backend)
    println(s"Command registration response: ${resp.body}")
  }

  // HTTP4s route to handle Discord interactions
  val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root =>
      req.as[Json].flatMap { json =>
        val tpe = json.hcursor.downField("type").as[Int].getOrElse(0)
        // 1 = PING, 2 = APPLICATION_COMMAND, 3 = MESSAGE_COMPONENT
        tpe match {
          case 1 =>
            Ok(Json.obj("type" -> Json.fromInt(1))) // Pong
          case 2 => // Slash command
            val data = json.hcursor.downField("data")
            val question = data.downField("options").downArray.downField("value").as[String].getOrElse("(no question)")
            val channelId = json.hcursor.downField("channel_id").as[String].getOrElse("")
            val interactionId = json.hcursor.get[String]("id").getOrElse("")
            val interactionToken = json.hcursor.get[String]("token").getOrElse("")

            println(s"Received slash command in channel: $channelId")

            // Respond with a message with buttons
            val respJson = Json.obj(
              "type" -> Json.fromInt(4), // CHANNEL_MESSAGE_WITH_SOURCE
              "data" -> Json.obj(
                "content" -> Json.fromString(question),
                "components" -> Json.arr(
                  Json.obj(
                    "type" -> Json.fromInt(1), // Action row
                    "components" -> Json.arr(
                      Json.obj(
                        "type" -> Json.fromInt(2), // Button
                        "style" -> Json.fromInt(3), // Success
                        "custom_id" -> Json.fromString("yes_btn"),
                        "label" -> Json.fromString("✅ Yes")
                      ),
                      Json.obj(
                        "type" -> Json.fromInt(2),
                        "style" -> Json.fromInt(4), // Danger
                        "custom_id" -> Json.fromString("no_btn"),
                        "label" -> Json.fromString("❌ No")
                      )
                    )
                  )
                )
              )
            )
            Ok(respJson)
          case 3 => // Button interaction
            val data = json.hcursor.downField("data")
            val customId = data.get[String]("custom_id").getOrElse("")
            val userId = json.hcursor.downField("member").downField("user").get[String]("id")
              .orElse(json.hcursor.downField("user").get[String]("id")).getOrElse("")
            val channelId = json.hcursor.downField("channel_id").as[String].getOrElse("")

            println(s"Received button interaction in channel: $channelId from user: $userId")

            val responseText = customId match {
              case "yes_btn" => "✅ You selected: Yes"
              case "no_btn" => "❌ You selected: No"
              case _ => "Unknown response."
            }
            responses.put(userId, responseText)
            val respJson = Json.obj(
              "type" -> Json.fromInt(4), // CHANNEL_MESSAGE_WITH_SOURCE
              "data" -> Json.obj(
                "content" -> Json.fromString(responseText)
                // Removed "flags" to make the response public
              )
            )
            Ok(respJson)
          case _ =>
            Ok(Json.obj("type" -> Json.fromInt(4), "data" -> Json.obj("content" -> Json.fromString("Unknown interaction."))))
        }
      }
  }

  val httpApp = routes.orNotFound

  override def run: IO[Unit] =
    IO(registerCommand()) *>
      EmberServerBuilder.default[IO]
        .withHost(com.comcast.ip4s.ipv4"0.0.0.0")
        .withPort(com.comcast.ip4s.port"8080")
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)
}
