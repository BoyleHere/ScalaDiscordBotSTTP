package discord.api.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

// Discord API Models based on our OpenAPI specification

case class User(
  id: String,
  username: String,
  discriminator: Option[String] = None,
  avatar: Option[String] = None
)

object User {
  implicit val decoder: Decoder[User] = deriveDecoder
  implicit val encoder: Encoder[User] = deriveEncoder
}

case class Emoji(
  id: Option[String] = None,
  name: Option[String] = None,
  animated: Option[Boolean] = None
)

object Emoji {
  implicit val decoder: Decoder[Emoji] = deriveDecoder
  implicit val encoder: Encoder[Emoji] = deriveEncoder
}

case class Component(
  `type`: Int,
  components: Option[List[Component]] = None,
  style: Option[Int] = None,
  label: Option[String] = None,
  emoji: Option[Emoji] = None,
  custom_id: Option[String] = None,
  url: Option[String] = None,
  disabled: Option[Boolean] = None
)

object Component {
  implicit val decoder: Decoder[Component] = deriveDecoder
  implicit val encoder: Encoder[Component] = deriveEncoder
}

case class Embed(
  title: Option[String] = None,
  description: Option[String] = None,
  color: Option[Int] = None
)

object Embed {
  implicit val decoder: Decoder[Embed] = deriveDecoder
  implicit val encoder: Encoder[Embed] = deriveEncoder
}

case class Message(
  id: String,
  channel_id: String,
  content: Option[String] = None,
  author: Option[User] = None,
  components: Option[List[Component]] = None
)

object Message {
  implicit val decoder: Decoder[Message] = deriveDecoder
  implicit val encoder: Encoder[Message] = deriveEncoder
}

case class InteractionCallbackData(
  content: Option[String] = None,
  embeds: Option[List[Embed]] = None,
  components: Option[List[Component]] = None,
  flags: Option[Int] = None
)

object InteractionCallbackData {
  implicit val decoder: Decoder[InteractionCallbackData] = deriveDecoder
  implicit val encoder: Encoder[InteractionCallbackData] = deriveEncoder
}

case class InteractionResponse(
  `type`: Int,
  data: Option[InteractionCallbackData] = None
)

object InteractionResponse {
  implicit val decoder: Decoder[InteractionResponse] = deriveDecoder
  implicit val encoder: Encoder[InteractionResponse] = deriveEncoder
}

case class WebhookMessage(
  content: Option[String] = None,
  embeds: Option[List[Embed]] = None,
  components: Option[List[Component]] = None,
  flags: Option[Int] = None
)

object WebhookMessage {
  implicit val decoder: Decoder[WebhookMessage] = deriveDecoder
  implicit val encoder: Encoder[WebhookMessage] = deriveEncoder
}

case class EditWebhookMessage(
  content: Option[String] = None,
  embeds: Option[List[Embed]] = None,
  components: Option[List[Component]] = None
)

object EditWebhookMessage {
  implicit val decoder: Decoder[EditWebhookMessage] = deriveDecoder
  implicit val encoder: Encoder[EditWebhookMessage] = deriveEncoder
}

// Interaction Types
object InteractionType {
  val PING = 1
  val APPLICATION_COMMAND = 2
  val MESSAGE_COMPONENT = 3
  val APPLICATION_COMMAND_AUTOCOMPLETE = 4
  val MODAL_SUBMIT = 5
}

// Interaction Response Types
object InteractionResponseType {
  val PONG = 1
  val CHANNEL_MESSAGE_WITH_SOURCE = 4
  val DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE = 5
  val DEFERRED_UPDATE_MESSAGE = 6
  val UPDATE_MESSAGE = 7
  val APPLICATION_COMMAND_AUTOCOMPLETE_RESULT = 8
  val MODAL = 9
  val PREMIUM_REQUIRED = 10
}

// Component Types
object ComponentType {
  val ACTION_ROW = 1
  val BUTTON = 2
  val STRING_SELECT = 3
  val TEXT_INPUT = 4
  val USER_SELECT = 5
  val ROLE_SELECT = 6
  val MENTIONABLE_SELECT = 7
  val CHANNEL_SELECT = 8
}

// Button Styles
object ButtonStyle {
  val PRIMARY = 1
  val SECONDARY = 2
  val SUCCESS = 3
  val DANGER = 4
  val LINK = 5
}

// Message Flags
object MessageFlags {
  val EPHEMERAL = 64
}
