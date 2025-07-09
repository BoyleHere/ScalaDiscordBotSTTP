# Discord Bot with sttp-client and Signature Verification

A feature-rich Scala Discord bot that creates interactive questions with Yes/No buttons using sttp-client for HTTP requests, http4s for handling Discord interactions, and BouncyCastle for Ed25519 signature verification.

## ✨ Features

- **Interactive Slash Commands**: Use `/ask "your question"` to create polls with clickable buttons
- **One Response Per User**: Each user can only respond once to each question/message
- **Ephemeral Confirmations**: Button responses are private - only the responder sees their confirmation
- **Discord Signature Verification**: Secure Ed25519 signature validation using BouncyCastle
- **Dynamic Channel Support**: Works in any channel without hardcoding IDs
- **Response Tracking**: Stores user responses in memory with duplicate prevention
- **Detailed Logging**: Terminal shows user selections with usernames and IDs
- **Static Domain Support**: Uses ngrok with permanent URL for consistent endpoint

## 🛠 Tech Stack

- **Scala 2.13** - Modern functional programming
- **sttp-client4** - HTTP client for Discord API calls
- **http4s** - HTTP server for receiving Discord interactions
- **circe** - JSON parsing and serialization
- **BouncyCastle** - Ed25519 signature verification for security
- **cats-effect** - Functional effects and IO
- **dotenv-java** - Environment variable management

## 🚀 Setup

### Prerequisites

1. Create a Discord application at https://discord.com/developers/applications
2. Create a bot user and get the bot token
3. Get your application's public key for signature verification
4. Set up an ngrok account for static domain (optional but recommended)

### Environment Variables

Create a `.env` file in your project root:

```env
DISCORD_TOKEN=your_bot_token_here
PUBLIC_KEY=your_discord_public_key_here
DISCORD_APP_ID=your_application_id_here  # Optional - will be auto-detected
```

### Installation

1. Clone the repository
2. Install dependencies:
   ```bash
   sbt compile
   ```

3. Set up ngrok with static domain (recommended):
   ```bash
   # Update ngrok.yml with your auth token and static domain
   ngrok start discord-bot --config ngrok.yml
   ```

4. Run the bot:
   ```bash
   sbt run
   ```

## 📋 Usage

### Basic Commands

1. **Create a poll**: `/ask question:"Do you like pizza?"`
2. **Users respond**: Click ��� Yes or ❌ No buttons
3. **View responses**: Check terminal for detailed logs

### Example Interaction

```
User: /ask question:"Should we order pizza for lunch?"
Bot: Should we order pizza for lunch?
     [✅ Yes] [❌ No]

User1 clicks "Yes" → Gets private message: "✅ Thank you for your response!"
Terminal shows: 🗳️ User john123 (1234567890) selected: Yes

User1 tries to click again → Gets warning: "⚠️ You have already responded to this question!"
```

## 🔒 Security Features

### Discord Signature Verification

The bot implements Discord's Ed25519 signature verification:

- Validates `X-Signature-Ed25519` and `X-Signature-Timestamp` headers
- Uses your Discord application's public key
- Rejects unauthorized requests with 403 Forbidden
- Includes testing mode for development

### Response Protection

- One response per user per message
- Prevents spam and duplicate voting
- Tracks responses by message ID, not globally

## 🏗 Architecture

```
Discord → ngrok → http4s Server → Signature Verification → Bot Logic → Discord API
                                        ↓
                                 Response Storage
```

1. **Discord sends interactions** to your ngrok endpoint
2. **http4s server** receives and validates signatures
3. **Bot logic** processes interactions and prevents duplicates
4. **sttp-client** sends responses back to Discord API
5. **Responses stored** in memory with user tracking

## 📁 Project Structure

```
chatops4s-discord/
├── build.sbt              # Dependencies and build configuration
├── ngrok.yml             # ngrok configuration with static domain
├── README.md             # This file
├── .env                  # Environment variables (create this)
└── src/main/scala/
    └── BotServer.scala   # Main bot application
```

## 🔧 Configuration

### ngrok Setup (Static Domain)

Update `ngrok.yml` with your configuration:

```yaml
version: "2"
authtoken: your_ngrok_auth_token
tunnels:
  discord-bot:
    addr: 8082
    proto: http
    domain: your-static-domain.ngrok-free.app
```

### Discord Application Setup

1. **Interactions Endpoint URL**: `https://your-static-domain.ngrok-free.app`
2. **Enable signature verification** in Discord Developer Portal
3. **Bot permissions**: Send Messages, Use Slash Commands
4. **Scopes**: `bot`, `applications.commands`

## 📊 Response Tracking

The bot maintains in-memory storage:

- `responses`: Maps user ID to their latest response
- `userResponses`: Maps message ID to user responses (prevents duplicates)

## 🐛 Troubleshooting

### Common Issues

1. **"Interactions endpoint could not be verified"**
   - Check that ngrok is running on port 8082
   - Verify your static domain is correctly configured
   - Ensure signature verification is properly set up

2. **"Address already in use"**
   - Change port in BotServer.scala if 8082 is occupied
   - Update ngrok.yml to match the new port

3. **Bot shows as offline**
   - This is normal for interaction-only bots
   - Bot will still respond to slash commands perfectly

### Debug Mode

The bot includes extensive logging:
- Signature verification results
- User interaction details
- Response tracking information
- Duplicate attempt warnings

## 🔄 Development Workflow

1. **Make changes** to BotServer.scala
2. **Compile**: `sbt compile`
3. **Run bot**: `sbt run`
4. **Test**: Use `/ask` command in Discord
5. **Check logs**: Monitor terminal for interaction details

## 📚 API References

- [Discord API Documentation](https://discord.com/developers/docs/intro)
- [Discord Slash Commands](https://discord.com/developers/docs/interactions/application-commands)
- [Discord Message Components](https://discord.com/developers/docs/interactions/message-components)
- [Discord Signature Verification](https://discord.com/developers/docs/interactions/receiving-and-responding#security-and-authorization)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly with Discord interactions
5. Submit a pull request

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

## 🎯 Future Enhancements

- [ ] Persistent storage (database integration)
- [ ] Multiple choice questions (beyond Yes/No)
- [ ] Response analytics and reporting
- [ ] Scheduled polls
- [ ] Role-based permissions
- [ ] Custom button labels
- [ ] Poll results visualization
