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
import io.github.cdimascio.dotenv.Dotenv
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.HexFormat

object BotServer extends IOApp.Simple {
  // Load .env file
  val dotenv = Dotenv.load()

  // Load token and public key from .env file
  val token = dotenv.get("DISCORD_TOKEN")
  val publicKey = dotenv.get("PUBLIC_KEY")
  val applicationId = Option(dotenv.get("DISCORD_APP_ID")) // Optional, can be extracted from token

  val backend = DefaultSyncBackend()

  // In-memory storage for responses and user tracking
  val responses = TrieMap.empty[String, String]
  // Track which users have responded to which messages: messageId -> Set[userId]
  val userResponses = TrieMap.empty[String, TrieMap[String, String]]

  // Discord signature verification function
  def verifyDiscordSignature(signature: String, timestamp: String, body: String): Boolean = {
    try {
      // Remove "0x" prefix if present and decode hex strings
      val signatureBytes = HexFormat.of().parseHex(signature)
      val publicKeyBytes = HexFormat.of().parseHex(publicKey)

      // Create the message that was signed (timestamp + body)
      val message = (timestamp + body).getBytes("UTF-8")

      // Create Ed25519 public key and verifier
      val ed25519PublicKey = new Ed25519PublicKeyParameters(publicKeyBytes)
      val verifier = new Ed25519Signer()
      verifier.init(false, ed25519PublicKey)
      verifier.update(message, 0, message.length)

      // Verify the signature
      verifier.verifySignature(signatureBytes)
    } catch {
      case e: Exception =>
        println(s"Signature verification failed: ${e.getMessage}")
        false
    }
  }

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
      // Extract Discord signature headers
      val signatureHeader = req.headers.headers.find(_.name.toString.equalsIgnoreCase("X-Signature-Ed25519")).map(_.value)
      val timestampHeader = req.headers.headers.find(_.name.toString.equalsIgnoreCase("X-Signature-Timestamp")).map(_.value)

      println(s"Received POST request: ${req.headers}")
      println(s"Signature: $signatureHeader")
      println(s"Timestamp: $timestampHeader")

      // Get request body as string for signature verification
      req.bodyText.compile.string.flatMap { bodyString =>
        println(s"Request body: $bodyString")

        // Verify Discord signature if headers are present
        val isSignatureValid = (signatureHeader, timestampHeader) match {
          case (Some(sig), Some(ts)) =>
            val valid = verifyDiscordSignature(sig, ts, bodyString)
            println(s"Signature verification result: $valid")
            valid
          case _ =>
            println("No signature headers found, allowing request for testing")
            true // Allow requests without signatures for testing
        }

        if (!isSignatureValid) {
          println("Invalid signature, rejecting request")
          Forbidden("Invalid signature")
        } else {
          // Parse JSON body and process interaction
          io.circe.parser.parse(bodyString) match {
            case Right(json) =>
              val tpe = json.hcursor.downField("type").as[Int].getOrElse(0)
              // 1 = PING, 2 = APPLICATION_COMMAND, 3 = MESSAGE_COMPONENT
              tpe match {
                case 1 =>
                  println("Responding to PING with PONG")
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
                  val messageId = json.hcursor.downField("message").get[String]("id").getOrElse("")
                  val username = json.hcursor.downField("member").downField("user").get[String]("username")
                    .orElse(json.hcursor.downField("user").get[String]("username")).getOrElse("Unknown User")

                  println(s"Received button interaction in channel: $channelId from user: $userId ($username)")

                  // Check if user has already responded to this specific message
                  val messageResponses = userResponses.getOrElseUpdate(messageId, TrieMap.empty)

                  if (messageResponses.contains(userId)) {
                    println(s"⚠️ Duplicate response detected from user $username ($userId) to message $messageId")
                    val respJson = Json.obj(
                      "type" -> Json.fromInt(4), // CHANNEL_MESSAGE_WITH_SOURCE
                      "data" -> Json.obj(
                        "content" -> Json.fromString("⚠️ You have already responded to this question!"),
                        "flags" -> Json.fromInt(64) // Ephemeral - only visible to the user
                      )
                    )
                    Ok(respJson)
                  } else {
                    val (responseText, optionSelected) = customId match {
                      case "yes_btn" => ("✅ Thank you for your response!", "Yes")
                      case "no_btn" => ("❌ Thank you for your response!", "No")
                      case _ => ("❓ Unknown response.", "Unknown")
                    }

                    // Log the selection to terminal
                    println(s"🗳️ User $username ($userId) selected: $optionSelected")

                    // Store the response
                    responses.put(userId, optionSelected)
                    messageResponses.put(userId, optionSelected)

                    // Send ephemeral confirmation message (only visible to the responder)
                    val respJson = Json.obj(
                      "type" -> Json.fromInt(4), // CHANNEL_MESSAGE_WITH_SOURCE
                      "data" -> Json.obj(
                        "content" -> Json.fromString(responseText),
                        "flags" -> Json.fromInt(64) // Ephemeral - only visible to the user who clicked
                      )
                    )
                    Ok(respJson)
                  }
                case _ =>
                  println(s"Unknown interaction type: $tpe")
                  Ok(Json.obj("type" -> Json.fromInt(4), "data" -> Json.obj("content" -> Json.fromString("Unknown interaction."))))
              }
            case Left(parseError) =>
              println(s"Failed to parse JSON: $parseError")
              BadRequest("Invalid JSON")
          }
        }
      }
    case req @ GET -> Root =>
      // Handle ngrok browser warning bypass
      println(s"Received GET request: ${req.headers}")
      Ok("ngrok tunnel active - Discord bot is running")
  }

  val httpApp = routes.orNotFound

  override def run: IO[Unit] =
    IO(registerCommand()) *>
      EmberServerBuilder.default[IO]
        .withHost(com.comcast.ip4s.Ipv4Address.fromString("127.0.0.1").get)
        .withPort(com.comcast.ip4s.Port.fromInt(8082).get)
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)
}
