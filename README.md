# Discord Bot with sttp-client

A Scala Discord bot that creates interactive questions with Yes/No buttons using sttp-client for HTTP requests and http4s for handling Discord interactions.

## Features

- **Slash Commands**: Use `/ask "your question"` to create interactive polls
- **Button Interactions**: Users can click Yes/No buttons to respond
- **Dynamic Channel Support**: Works in any channel without hardcoding IDs
- **Response Storage**: Stores user responses in memory
- **Public Responses**: All interactions are visible to everyone in the channel

## Tech Stack

- **Scala 2.13**
- **sttp-client4** - HTTP client for Discord API calls
- **http4s** - HTTP server for receiving Discord interactions
- **circe** - JSON parsing and serialization
- **cats-effect** - Functional effects

## Setup

### Prerequisites

1. Create a Discord application at https://discord.com/developers/applications
2. Create a bot user and get the bot token
3. Set up an interaction endpoint URL (see deployment section)

### Environment Variables

Create a `.env` file or set these environment variables:

```bash
DISCORD_TOKEN=your_bot_token_here
DISCORD_APP_ID=your_application_id_here  # Optional - will be auto-detected
```

### Installation

1. Clone the repository
2. Install dependencies:
   ```bash
   sbt compile
   ```

3. Run the bot:
   ```bash
   sbt run
   ```

## Usage

1. Invite the bot to your Discord server with the following permissions:
   - Use Slash Commands
   - Send Messages
   - Use External Emojis

2. Use the `/ask` command in any channel:
   ```
   /ask question:"Do you like pizza?"
   ```

3. Users can click the ✅ Yes or ❌ No buttons to respond

4. Responses are stored and displayed publicly in the channel

## Bot Permissions

When inviting the bot, ensure it has these permissions:
- `applications.commands` (Use Slash Commands)
- `bot` (Basic bot permissions)

## Deployment

### Local Development with ngrok

1. Install [ngrok](https://ngrok.com/)
2. Run your bot locally: `sbt run`
3. In another terminal, expose port 8080: `ngrok http 8080`
4. Copy the ngrok URL and set it as your Discord app's interaction endpoint:
   - Go to your Discord app in the Developer Portal
   - Navigate to General Information
   - Set "Interactions Endpoint URL" to `https://your-ngrok-url.ngrok.io`

### Production Deployment

For production, deploy to a cloud service that supports:
- HTTPS endpoints
- Scala/JVM applications
- Port 8080 (or configure as needed)

Popular options:
- Heroku
- Railway
- Google Cloud Run
- AWS Lambda (with custom runtime)

## Architecture

```
Discord → Interaction Endpoint → http4s Server → sttp-client → Discord API
```

1. **Discord sends interactions** to your HTTP endpoint
2. **http4s server** receives and parses the interaction
3. **Bot logic** processes the interaction and prepares a response
4. **sttp-client** sends responses back to Discord API
5. **Discord displays** the response in the channel

## Code Structure

- `BotServer.scala` - Main bot application
  - `registerCommand()` - Registers slash commands with Discord
  - `routes` - HTTP routes for handling Discord interactions
  - Response storage and button interaction handling

## API References

- [Discord API Documentation](https://discord.com/developers/docs/intro)
- [Discord Slash Commands](https://discord.com/developers/docs/interactions/application-commands)
- [Discord Message Components](https://discord.com/developers/docs/interactions/message-components)

## Troubleshooting

### Common Issues

1. **"Unknown interaction"** - Check that your interaction endpoint URL is correct
2. **Commands not appearing** - Ensure bot has proper permissions and commands are registered
3. **Button clicks not working** - Verify the interaction endpoint is responding correctly

### Debug Mode

The bot prints debug information including:
- Command registration responses
- Channel IDs for interactions
- User IDs for button clicks

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is open source and available under the [MIT License](LICENSE).
