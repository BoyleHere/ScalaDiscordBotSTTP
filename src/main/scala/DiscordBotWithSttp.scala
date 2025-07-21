package discord.bot

import cats.effect._
import org.http4s.{HttpRoutes, Response => Http4sResponse}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe._
import org.http4s.server.middleware.CORS
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import sttp.client4._
import scala.collection.concurrent.TrieMap
import io.github.cdimascio.dotenv.Dotenv
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.util.HexFormat
import discord.api.client.{DiscordApiClient, DiscordComponents}
import discord.api.model._
import discord.gateway.DiscordGatewayClient
import scala.util.{Try, Success, Failure}
import com.comcast.ip4s._

object DiscordBotWithSttp extends IOApp.Simple {

  // Load environment variables
  val dotenv = Dotenv.load()
  val token = dotenv.get("DISCORD_TOKEN")
  val publicKey = dotenv.get("PUBLIC_KEY")
  val applicationId = Option(dotenv.get("DISCORD_APP_ID"))

  // HTTP backend for Discord API calls
  val backend = DefaultSyncBackend()
  val discordClient = new DiscordApiClient(token, backend)

  // Gateway client for presence management
  var gatewayClient: Option[DiscordGatewayClient] = None

  // In-memory storage for tracking user responses per message
  // messageId -> userId -> response ("yes" or "no")
  val userResponses = TrieMap.empty[String, TrieMap[String, String]]

  // Discord signature verification
  def verifyDiscordSignature(signature: String, timestamp: String, body: String): Boolean = {
    try {
      val signatureBytes = HexFormat.of().parseHex(signature)
      val publicKeyBytes = HexFormat.of().parseHex(publicKey)
      val message = (timestamp + body).getBytes("UTF-8")

      val ed25519PublicKey = new Ed25519PublicKeyParameters(publicKeyBytes)
      val verifier = new Ed25519Signer()
      verifier.init(false, ed25519PublicKey)
      verifier.update(message, 0, message.length)

      verifier.verifySignature(signatureBytes)
    } catch {
      case e: Exception =>
        println(s"Signature verification failed: ${e.getMessage}")
        false
    }
  }

  // Register the slash command
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

    // Get application ID if not provided
    val appId = applicationId.getOrElse {
      discordClient.getApplicationInfo() match {
        case Success(json) =>
          json.hcursor.get[String]("id").getOrElse {
            throw new RuntimeException("Failed to get application ID")
          }
        case Failure(ex) =>
          throw new RuntimeException(s"Failed to get bot info: ${ex.getMessage}")
      }
    }

    discordClient.registerGlobalCommand(appId, commandJson) match {
      case Success(response) =>
        println(s"Successfully registered command: $response")
      case Failure(ex) =>
        println(s"Failed to register command: ${ex.getMessage}")
    }
  }

  // Handle slash command interactions
  def handleSlashCommand(json: Json, interactionId: String, interactionToken: String): IO[Http4sResponse[IO]] = {
    val data = json.hcursor.downField("data")
    val question = data.downField("options").downArray.downField("value").as[String].getOrElse("(no question)")
    val channelId = json.hcursor.downField("channel_id").as[String].getOrElse("")

    println(s"Received slash command '$question' in channel: $channelId")

    // Create response with yes/no buttons
    val yesNoButtons = DiscordComponents.createYesNoButtons()
    val response = DiscordComponents.createInteractionResponse(question, List(yesNoButtons))

    // Send response to Discord
    discordClient.createInteractionResponse(interactionId, interactionToken, response) match {
      case Success(_) =>
        println("Successfully sent interaction response")
        Ok(Json.obj())
      case Failure(ex) =>
        println(s"Failed to send interaction response: ${ex.getMessage}")
        InternalServerError(s"Failed to respond: ${ex.getMessage}")
    }
  }

  // Handle button interactions
  def handleButtonInteraction(json: Json, interactionId: String, interactionToken: String): IO[Http4sResponse[IO]] = {
    val messageId = json.hcursor.downField("message").downField("id").as[String].getOrElse("")
    val userId = json.hcursor.downField("member").downField("user").downField("id")
      .as[String]
      .orElse(json.hcursor.downField("user").downField("id").as[String])
      .getOrElse("")
    val customId = json.hcursor.downField("data").downField("custom_id").as[String].getOrElse("")

    println(s"Button interaction: messageId=$messageId, userId=$userId, customId=$customId")

    // Check if user has already responded to this message
    val messageResponses = userResponses.getOrElseUpdate(messageId, TrieMap.empty)

    if (messageResponses.contains(userId)) {
      // User already responded - send ephemeral message
      val response = DiscordComponents.createInteractionResponse("⛔ You've already responded to this question.", ephemeral = true)

      discordClient.createInteractionResponse(interactionId, interactionToken, response) match {
        case Success(_) =>
          println(s"Sent 'already responded' message to user $userId")
          Ok(Json.obj())
        case Failure(ex) =>
          println(s"Failed to send response: ${ex.getMessage}")
          InternalServerError(s"Failed to respond: ${ex.getMessage}")
      }
    } else {
      // New response - record it and respond
      val userResponse = customId match {
        case "yes_btn" => "yes"
        case "no_btn" => "no"
        case _ => "unknown"
      }

      messageResponses(userId) = userResponse
      println(s"✅ User $userId selected: $userResponse for message $messageId")

      val responseMessage = customId match {
        case "yes_btn" => "✅ You selected: Yes"
        case "no_btn" => "❌ You selected: No"
        case _ => "⚠️ Unknown button!"
      }

      val response = DiscordComponents.createInteractionResponse(responseMessage, ephemeral = true)

      discordClient.createInteractionResponse(interactionId, interactionToken, response) match {
        case Success(_) =>
          println(s"Sent confirmation to user $userId")
          // Also log the response to terminal
          println(s"📊 RESPONSE LOGGED: User $userId selected '$userResponse' for message $messageId")
          Ok(Json.obj())
        case Failure(ex) =>
          println(s"Failed to send response: ${ex.getMessage}")
          InternalServerError(s"Failed to respond: ${ex.getMessage}")
      }
    }
  }

  // HTTP routes for Discord interactions
  val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root =>
      // Handle ngrok warning bypass
      val hasSkipWarning = req.headers.headers.exists(h =>
        h.name.toString.toLowerCase.contains("ngrok-skip-browser-warning") ||
        h.name.toString.toLowerCase.contains("user-agent")
      )

      // Extract Discord signature headers
      val signatureHeader = req.headers.headers.find(_.name.toString.equalsIgnoreCase("X-Signature-Ed25519")).map(_.value)
      val timestampHeader = req.headers.headers.find(_.name.toString.equalsIgnoreCase("X-Signature-Timestamp")).map(_.value)

      // Get request body as string
      req.bodyText.compile.string.flatMap { bodyString =>
        println(s"📥 Received interaction: ${bodyString.take(200)}...")

        // Verify signature (optional for development)
        val isSignatureValid = (signatureHeader, timestampHeader) match {
          case (Some(sig), Some(ts)) =>
            val valid = verifyDiscordSignature(sig, ts, bodyString)
            println(s"🔐 Signature verification: $valid")
            valid
          case _ =>
            println("⚠️ No signature headers found, allowing for development")
            true
        }

        if (!isSignatureValid) {
          println("❌ Invalid signature, rejecting request")
          Forbidden("Invalid signature")
        } else {
          // Parse and handle the interaction
          io.circe.parser.parse(bodyString) match {
            case Right(json) =>
              val interactionType = json.hcursor.downField("type").as[Int].getOrElse(0)
              val interactionId = json.hcursor.get[String]("id").getOrElse("")
              val interactionToken = json.hcursor.get[String]("token").getOrElse("")

              interactionType match {
                case InteractionType.PING =>
                  println("🏓 Responding to PING with PONG")
                  Ok(Json.obj("type" -> Json.fromInt(InteractionResponseType.PONG)))

                case InteractionType.APPLICATION_COMMAND =>
                  handleSlashCommand(json, interactionId, interactionToken)

                case InteractionType.MESSAGE_COMPONENT =>
                  handleButtonInteraction(json, interactionId, interactionToken)

                case _ =>
                  println(s"⚠️ Unknown interaction type: $interactionType")
                  BadRequest("Unknown interaction type")
              }

            case Left(parseError) =>
              println(s"❌ Failed to parse JSON: $parseError")
              BadRequest("Invalid JSON")
          }
        }
      }
  }

  // Get Gateway URL and establish real WebSocket connection for online presence
  def establishGatewayConnection(): IO[Unit] = {
    IO.async_ { callback =>
      try {
        // Get Gateway URL
        val gatewayRequest = basicRequest
          .get(uri"https://discord.com/api/v10/gateway/bot")
          .header("Authorization", s"Bot $token")
          .response(asString)

        val gatewayResponse = gatewayRequest.send(backend)
        gatewayResponse.body match {
          case Right(jsonString) =>
            io.circe.parser.parse(jsonString) match {
              case Right(json) =>
                val gatewayUrl = json.hcursor.get[String]("url").getOrElse("wss://gateway.discord.gg")
                println(s"🌐 Gateway URL: $gatewayUrl")

                // Create and connect the real WebSocket Gateway client
                val wsUrl = s"$gatewayUrl/?v=10&encoding=json"
                val gateway = new DiscordGatewayClient(token, wsUrl)
                gatewayClient = Some(gateway)

                println("🔗 Connecting to Discord Gateway...")
                gateway.connect()

                // Wait a moment for connection to establish
                Thread.sleep(2000)
                println("🟢 Gateway connection established - Bot should now appear ONLINE!")
                callback(Right(()))

              case Left(parseError) =>
                println(s"❌ Failed to parse gateway response: $parseError")
                callback(Right(())) // Continue even if gateway fails
            }

          case Left(error) =>
            println(s"❌ Failed to get gateway URL: $error")
            callback(Right(())) // Continue even if gateway fails
        }
      } catch {
        case ex: Exception =>
          println(s"⚠️ Gateway connection failed: ${ex.getMessage}")
          callback(Right(())) // Continue even if gateway fails
      }
    }
  }

  // Update bot presence/status using the Gateway connection
  def updateBotPresence(): Unit = {
    gatewayClient match {
      case Some(gateway) =>
        println("🟢 Updating bot presence to ONLINE with /ask activity...")
        gateway.setPresence(
          status = "online",
          activityName = "/ask - Ask yes/no questions",
          activityType = 0 // PLAYING
        )
      case None =>
        println("⚠️ Gateway client not available, cannot update presence")
    }
  }

  // Main application
  def run: IO[Unit] = {
    println("🤖 Starting Discord Bot with STTP...")
    println(s"🔑 Using token: ${token.take(10)}...")

    // Establish Gateway connection for online presence
    establishGatewayConnection().flatMap { _ =>

      // Update bot presence
      IO.delay(updateBotPresence())

    }.flatMap { _ =>

      // Register the slash command
      IO.delay {
        Try(registerCommand()) match {
          case Success(_) => println("✅ Command registered successfully")
          case Failure(ex) => println(s"⚠️ Command registration failed: ${ex.getMessage}")
        }
      }

    }.flatMap { _ =>

      // Start HTTP server
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8082")
        .withHttpApp(routes.orNotFound)
        .build
        .use { server =>
          println(s"🚀 Discord bot server started at ${server.address}")
          println("🟢 Bot is now ONLINE and ready to receive interactions")
          println("💬 Use /ask command in Discord to test")
          IO.never // Keep server running
        }
    }
  }
}
