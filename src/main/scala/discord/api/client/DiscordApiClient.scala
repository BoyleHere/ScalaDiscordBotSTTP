package discord.api.client

import sttp.client4._
import sttp.client4.circe._
import sttp.model.Uri
import discord.api.model._
import io.circe.Json
import scala.util.{Try, Success, Failure}

class DiscordApiClient(token: String, backend: Backend[Identity]) {
  private val baseUrl = "https://discord.com/api/v10"

  private def authHeader = Map("Authorization" -> s"Bot $token")

  // Create Interaction Response
  def createInteractionResponse(
    interactionId: String,
    interactionToken: String,
    response: InteractionResponse
  ): Try[Unit] = {
    val request = basicRequest
      .post(uri"$baseUrl/interactions/$interactionId/$interactionToken/callback")
      .headers(authHeader)
      .body(response)
      .response(ignore)

    Try(request.send(backend)) match {
      case Success(resp) if resp.code.isSuccess => Success(())
      case Success(resp) => Failure(new RuntimeException(s"Discord API error: ${resp.code} - ${resp.body}"))
      case Failure(ex) => Failure(ex)
    }
  }

  // Get Application Info
  def getApplicationInfo(): Try[Json] = {
    val request = basicRequest
      .get(uri"$baseUrl/applications/@me")
      .headers(authHeader)
      .response(asJson[Json])

    Try(request.send(backend)) match {
      case Success(resp) if resp.code.isSuccess =>
        resp.body match {
          case Right(json) => Success(json)
          case Left(error) => Failure(new RuntimeException(s"Failed to parse response: $error"))
        }
      case Success(resp) => Failure(new RuntimeException(s"Discord API error: ${resp.code} - ${resp.body}"))
      case Failure(ex) => Failure(ex)
    }
  }

  // Register Global Command
  def registerGlobalCommand(applicationId: String, commandData: Json): Try[Json] = {
    val request = basicRequest
      .post(uri"$baseUrl/applications/$applicationId/commands")
      .headers(authHeader)
      .body(commandData)
      .response(asJson[Json])

    Try(request.send(backend)) match {
      case Success(resp) if resp.code.isSuccess =>
        resp.body match {
          case Right(json) => Success(json)
          case Left(error) => Failure(new RuntimeException(s"Failed to parse response: $error"))
        }
      case Success(resp) => Failure(new RuntimeException(s"Discord API error: ${resp.code} - ${resp.body}"))
      case Failure(ex) => Failure(ex)
    }
  }
}

// Helper object for creating common Discord components
object DiscordComponents {

  def createButton(
    customId: String,
    label: String,
    style: Int = ButtonStyle.PRIMARY,
    emoji: Option[Emoji] = None,
    disabled: Boolean = false
  ): Component = {
    Component(
      `type` = ComponentType.BUTTON,
      custom_id = Some(customId),
      label = Some(label),
      style = Some(style),
      emoji = emoji,
      disabled = Some(disabled)
    )
  }

  def createActionRow(components: Component*): Component = {
    Component(
      `type` = ComponentType.ACTION_ROW,
      components = Some(components.toList)
    )
  }

  def createYesNoButtons(): Component = {
    createActionRow(
      createButton("yes_btn", "✅ Yes", ButtonStyle.SUCCESS),
      createButton("no_btn", "❌ No", ButtonStyle.DANGER)
    )
  }

  def createInteractionResponse(
    content: String,
    components: List[Component] = List.empty,
    ephemeral: Boolean = false
  ): InteractionResponse = {
    InteractionResponse(
      `type` = InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
      data = Some(InteractionCallbackData(
        content = Some(content),
        components = if (components.nonEmpty) Some(components) else None,
        flags = if (ephemeral) Some(MessageFlags.EPHEMERAL) else None
      ))
    )
  }
}
