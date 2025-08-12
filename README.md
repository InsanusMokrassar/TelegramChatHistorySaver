# Telegram Chat History Saver Bot

A comprehensive Telegram bot for automatically saving chat history and managing reminders. Built for efficient message archiving and content management.

## Features

### 📝 Chat History Saving

- **Automatic message archiving**: Saves all messages from tracked chats to local storage
- **Media support**: Handles text, images, videos, documents, and other media types
- **Thread support**: Properly organizes forum topics and thread messages
- **Reaction feedback**: Visual feedback through message reactions (saving, saved, error states)
- **Selective tracking**: Enable/disable tracking for specific chats

### ⏰ Reminder System

- **Scheduled reminders**: Set reminders for specific messages using cron-like syntax
- **Reply-based reminders**: Create reminders by replying to messages
- **Flexible scheduling**: Use krontab expressions for complex scheduling patterns
- **Reminder management**: Remove reminders for specific messages

### 🔧 Management Commands

- `/enable_chat_tracking` - Enable message tracking for current chat (owner only)
- `/disable_chat_tracking` - Disable message tracking for current chat (owner only)
- `/force_resave` - Force resave of replied message
- `/force_full_resave` - Force full resave of all messages in chat
- `/remind <schedule>` - Set reminder for replied message (use `krontab` syntax)
- `/remove_reminders` - Remove all reminders for replied message

## Technology Stack

* [KTgBotAPI Kotlin Library](https://docs.inmo.dev/tgbotapi/index.html) + [PlaguBot](https://docs.inmo.dev/plagubot/index.html) - Telegram Bot API framework
* [Kotlinx Exposed](https://github.com/JetBrains/Exposed) - Database ORM
* [SQLite](https://github.com/xerial/sqlite-jdbc) - Database engine
* [Kotlin Koin](https://insert-koin.io) - Dependency injection
* [Krontab](https://github.com/InsanusMokrassar/KrontabPredictor) - Scheduling expressions

## Project Structure

```
├── common/          # Core functionality (message saving, tracking)
├── replier/         # Simple reply functionality
├── reminder/        # Reminder system with scheduling
└── runner/          # Application entry point and configuration
```

## Configuration

Create a `local.json` file based on the provided `sample.json`:

```json
{
  "database": {
    "url": "jdbc:sqlite:file:local.sqlite?cache=shared"
  },
  "botToken": "YOUR_BOT_TOKEN_HERE",
  "plugins": [
    "dev.inmo.plagubot.plugins.commands.CommandsPlugin",
    "dev.inmo.tgchat_history_saver.common.CommonPlugin",
    "dev.inmo.tgchat_history_saver.replier.ReplierPlugin",
    "dev.inmo.tgchat_history_saver.reminder.ReminderPlugin"
  ],
  "common": {
    "ownerChatId": YOUR_CHAT_ID,
    "cachingChatId": YOUR_CHAT_ID,
    "savingFolder": "./content_data.local/"
  },
  "replier": {
    "answer": "Your default reply message"
  },
  "reminder": {}
}
```

### Configuration Options

- **botToken**: Your Telegram bot token from @BotFather
- **ownerChatId**: Your personal chat ID for admin commands
- **cachingChatId**: Chat ID for caching operations
- **savingFolder**: Local folder path for storing saved content
- **answer**: Default reply message for the replier plugin

## Running the Bot

### Prerequisites

- Java 17 or higher
- Gradle

### Launch Command

```bash
./gradlew run --args="/path/to/local.json"
```

### Docker Deployment

The project includes Docker support for easy deployment:

```bash
# Build and run with Docker Compose
docker-compose up -d

# Or build manually
docker build -t tgchat-history-saver .
docker run -v $(pwd)/content_data.local:/app/content_data.local tgchat-history-saver
```

## Usage

1. **Setup**: Configure your bot token and chat IDs in `local.json`
2. **Enable tracking**: Use `/enable_chat_tracking` in chats you want to monitor
3. **Monitor**: The bot will automatically save all messages with visual feedback
4. **Set reminders**: Reply to any message with `/remind "0 9 * * 1"` for weekly Monday reminders
5. **Manage**: Use admin commands to control tracking and force resaves

## Scheduling Syntax

The reminder system uses krontab expressions. Examples:
- `"0 9 * * 1"` - Every Monday at 9 AM
- `"0 12 * * *"` - Every day at 12 PM
- `"0 0 1 * *"` - First day of every month at midnight

Use the [Krontab Predictor](https://insanusmokrassar.github.io/KrontabPredictor/) for help with scheduling expressions.
