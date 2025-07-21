# Discord Bot with STTP4 and WebSocket Gateway

A feature-rich Scala Discord bot implementation using **code generation patterns** with sttp4, WebSocket Gateway connection, and real-time presence management. Creates interactive questions with Yes/No buttons and maintains proper online status.

## ✨ Features

### 🎯 Core Functionality
- **Interactive Slash Commands**: Use `/ask "your question"` to create polls with clickable buttons
- **One Response Per User**: Each user can only respond once to each question/message
- **Ephemeral Confirmations**: Button responses are private - only the responder sees their confirmation
- **Real-time Response Logging**: Terminal shows detailed user selections with IDs and choices

### 🌐 Discord Integration  
- **WebSocket Gateway Connection**: Real persistent connection to Discord for online presence
- **Bot Status Management**: Shows as "Playing /ask - Ask yes/no questions" when online
- **Discord Signature Verification**: Secure Ed25519 signature validation using BouncyCastle
- **Dynamic Channel Support**: Works in any channel without hardcoding IDs
- **Automatic Command Registration**: Slash commands registered automatically on startup

### 🏗️ Code Generation Architecture
- **Structured Event Handling**: Clean opcode management with `GatewayOpcodes` object
- **JSON Payload Generation**: Systematic creation of Discord protocol messages
- **Pattern Matching**: Type-safe event processing for different interaction types
- **Automated Scheduling**: Heartbeat generation using `ScheduledExecutorService`

## 🛠 Tech Stack

- **Scala 2.13** - Modern functional programming
- **sttp-client4** - HTTP client for Discord REST API calls  
- **Java-WebSocket** - WebSocket client for Discord Gateway connection
- **http4s** - HTTP server for receiving Discord interactions
- **circe** - JSON parsing and serialization
- **BouncyCastle** - Ed25519 signature verification for security
- **cats-effect** - Functional effects and IO management
- **dotenv-java** - Environment variable management

## 🚀 Setup

### Prerequisites

1. **Discord Application Setup**:
   - Create application at https://discord.com/developers/applications
   - Create bot user and copy the bot token
   - Copy your application's public key for signature verification
   - Enable "MESSAGE CONTENT INTENT" if needed

2. **Development Environment**:
   - Install Scala and SBT
   - Install ngrok for local development tunneling
   - Java 11+ for WebSocket support

### Environment Variables

Create a `.env` file in your project root:

```env
DISCORD_TOKEN=your_bot_token_here
PUBLIC_KEY=your_discord_public_key_here
DISCORD_APP_ID=your_application_id_here  # Optional - auto-detected if not provided
```

### Installation & Running

1. **Clone and compile**:
   ```bash
   git clone <your-repo>
   cd chatops4s-discord
   sbt compile
   ```

2. **Set up ngrok tunnel**:
   ```bash
   ngrok http 8082
   ```

3. **Configure Discord webhook**:
   - Copy your ngrok URL (e.g., `https://abc123.ngrok-free.app`)
   - Set as "Interactions Endpoint URL" in Discord Developer Portal
   - Add `/interactions` if required by your setup

4. **Run the bot**:
   ```bash
   sbt "runMain discord.bot.DiscordBotWithSttp"
   ```

## 📋 Expected Startup Sequence

```
🤖 Starting Discord Bot with STTP...
🔑 Using token: MTM4MTk3Nj...
🌐 Gateway URL: wss://gateway.discord.gg
🔗 Connecting to Discord Gateway...
💓 Starting heartbeat every 41250ms  
🔑 Sending IDENTIFY payload
🟢 Bot is now READY and ONLINE!
🟢 Setting presence: online - /ask - Ask yes/no questions
✅ Command registered successfully
🚀 Discord bot server started at /[0:0:0:0:0:0:0:0]:8082
🟢 Bot is now ONLINE and ready to receive interactions
💬 Use /ask command in Discord to test
```

## 🧪 Usage

### Basic Usage
```
/ask Do you like pizza?
```

Users will see Yes/No buttons and can click to respond. Each user can only respond once per question.

### Response Tracking
The terminal will show detailed logs:
```
📊 RESPONSE LOGGED: User 123456789 selected 'yes' for message 987654321
📊 RESPONSE LOGGED: User 111222333 selected 'no' for message 987654321  
```

## 🏗️ Architecture

### Project Structure
```
src/main/scala/
├── discord/
│   ├── api/
│   │   ├── model/Models.scala          # Discord API data models
│   │   └── client/DiscordApiClient.scala # REST API client
│   └── gateway/
│       └── DiscordGatewayClient.scala  # WebSocket Gateway client
├── DiscordBotWithSttp.scala           # Main application entry point
└── BotServer.scala                     # Legacy HTTP-only implementation
```

### Key Components

#### 1. **DiscordApiClient** (REST API)
- Type-safe HTTP calls using sttp4
- Command registration and interaction responses
- Proper error handling with `Try/Success/Failure`

#### 2. **DiscordGatewayClient** (WebSocket)  
- Real-time connection to Discord Gateway
- Presence management and heartbeat handling
- Event processing with pattern matching

#### 3. **Models** (Type Safety)
- Circe-based JSON encoding/decoding
- Discord API data structures
- Constants for interaction types and opcodes

#### 4. **Main Application**
- HTTP4s server for interaction endpoints
- Signature verification for security
- Response tracking and duplicate prevention

## 🔧 Configuration

### Bot Permissions
Your bot needs these permissions:
- `applications.commands` - For slash commands
- `bot` - Basic bot functionality

### Discord Developer Portal Setup
1. **OAuth2 > URL Generator**:
   - Scopes: `bot`, `applications.commands`
   - Permissions: Based on your needs

2. **General Information**:
   - Set "Interactions Endpoint URL" to your ngrok tunnel

## 🛡️ Security Features

- **Ed25519 Signature Verification**: Validates Discord requests
- **Environment Variable Protection**: Tokens stored securely
- **Input Validation**: Safe JSON parsing and error handling
- **Rate Limiting Awareness**: Built-in Discord API respect

## 🧪 Development

### Testing Locally
1. Start ngrok: `ngrok http 8082`
2. Update Discord endpoint URL
3. Run bot: `sbt "runMain discord.bot.DiscordBotWithSttp"`  
4. Test with `/ask` in Discord

### Debugging
- Check terminal logs for detailed interaction flow
- Verify ngrok tunnel is active and accessible
- Ensure Discord endpoint URL matches ngrok URL
- Validate environment variables are loaded

## 📦 Dependencies

Key dependencies in `build.sbt`:
```scala
"com.softwaremill.sttp.client4" %% "core" % "4.0.0-M7"
"com.softwaremill.sttp.client4" %% "circe" % "4.0.0-M7"  
"org.java-websocket" % "Java-WebSocket" % "1.5.4"
"org.http4s" %% "http4s-ember-server" % "0.23.26"
"org.bouncycastle" % "bcprov-jdk18on" % "1.77"
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🏷️ Tags

`scala` `discord-bot` `sttp4` `websocket` `http4s` `cats-effect` `circe` `functional-programming` `code-generation`
