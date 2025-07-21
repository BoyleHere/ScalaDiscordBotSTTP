package discord.gateway

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import scala.concurrent.duration._
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

class DiscordGatewayClient(token: String, gatewayUrl: String) extends WebSocketClient(URI.create(gatewayUrl)) {

  private var sessionId: Option[String] = None
  private var sequenceNumber: Option[Int] = None
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
  private var heartbeatInterval: Option[Long] = None

  // Gateway opcodes
  object GatewayOpcodes {
    val DISPATCH = 0
    val HEARTBEAT = 1
    val IDENTIFY = 2
    val PRESENCE_UPDATE = 3
    val VOICE_STATE_UPDATE = 4
    val RESUME = 6
    val RECONNECT = 7
    val REQUEST_GUILD_MEMBERS = 8
    val INVALID_SESSION = 9
    val HELLO = 10
    val HEARTBEAT_ACK = 11
  }

  override def onOpen(handshake: ServerHandshake): Unit = {
    println("🔗 Connected to Discord Gateway")
  }

  override def onMessage(message: String): Unit = {
    parse(message) match {
      case Right(json) =>
        val op = json.hcursor.get[Int]("op").getOrElse(-1)
        val sequence = json.hcursor.get[Int]("s").toOption
        val eventType = json.hcursor.get[String]("t").toOption
        val data = json.hcursor.downField("d")

        // Update sequence number if present
        sequence.foreach(seq => sequenceNumber = Some(seq))

        op match {
          case GatewayOpcodes.HELLO =>
            val interval = data.get[Long]("heartbeat_interval").getOrElse(41250L)
            heartbeatInterval = Some(interval)
            println(s"💓 Starting heartbeat every ${interval}ms")
            startHeartbeat(interval)
            identify()

          case GatewayOpcodes.DISPATCH =>
            eventType match {
              case Some("READY") =>
                sessionId = data.get[String]("session_id").toOption
                println("🟢 Bot is now READY and ONLINE!")
                setPresence()

              case Some("RESUMED") =>
                println("🔄 Session resumed successfully")

              case _ =>
                // Handle other dispatch events if needed
            }

          case GatewayOpcodes.HEARTBEAT_ACK =>
            // Heartbeat acknowledged

          case GatewayOpcodes.INVALID_SESSION =>
            println("❌ Invalid session, reconnecting...")
            // Handle invalid session

          case GatewayOpcodes.RECONNECT =>
            println("🔄 Gateway requested reconnection")
            // Handle reconnect request

          case _ =>
            // Handle other opcodes
        }

      case Left(error) =>
        println(s"❌ Failed to parse Gateway message: $error")
    }
  }

  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    println(s"🔌 Gateway connection closed: $code - $reason")
    scheduler.shutdown()
  }

  override def onError(ex: Exception): Unit = {
    println(s"❌ Gateway error: ${ex.getMessage}")
  }

  private def startHeartbeat(interval: Long): Unit = {
    scheduler.scheduleAtFixedRate(
      () => {
        val heartbeat = Json.obj(
          "op" -> Json.fromInt(GatewayOpcodes.HEARTBEAT),
          "d" -> sequenceNumber.map(Json.fromInt).getOrElse(Json.Null)
        )
        send(heartbeat.noSpaces)
      },
      interval,
      interval,
      TimeUnit.MILLISECONDS
    )
  }

  private def identify(): Unit = {
    val identifyPayload = Json.obj(
      "op" -> Json.fromInt(GatewayOpcodes.IDENTIFY),
      "d" -> Json.obj(
        "token" -> Json.fromString(token),
        "intents" -> Json.fromInt(0), // No intents needed for interactions-only bot
        "properties" -> Json.obj(
          "os" -> Json.fromString("linux"),
          "browser" -> Json.fromString("discord-bot-sttp"),
          "device" -> Json.fromString("discord-bot-sttp")
        ),
        "presence" -> Json.obj(
          "status" -> Json.fromString("online"),
          "since" -> Json.Null,
          "activities" -> Json.arr(
            Json.obj(
              "name" -> Json.fromString("/ask"),
              "type" -> Json.fromInt(0) // PLAYING
            )
          ),
          "afk" -> Json.fromBoolean(false)
        )
      )
    )

    println("🔑 Sending IDENTIFY payload")
    send(identifyPayload.noSpaces)
  }

  def setPresence(
    status: String = "online",
    activityName: String = "/ask",
    activityType: Int = 0 // 0=PLAYING, 1=STREAMING, 2=LISTENING, 3=WATCHING, 5=COMPETING
  ): Unit = {
    val presencePayload = Json.obj(
      "op" -> Json.fromInt(GatewayOpcodes.PRESENCE_UPDATE),
      "d" -> Json.obj(
        "status" -> Json.fromString(status),
        "since" -> Json.Null,
        "activities" -> Json.arr(
          Json.obj(
            "name" -> Json.fromString(activityName),
            "type" -> Json.fromInt(activityType)
          )
        ),
        "afk" -> Json.fromBoolean(false)
      )
    )

    println(s"🟢 Setting presence: $status - $activityName")
    send(presencePayload.noSpaces)
  }

  def disconnect(): Unit = {
    scheduler.shutdown()
    close()
  }
}
