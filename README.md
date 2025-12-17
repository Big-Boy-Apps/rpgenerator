# RPGenerator

A Kotlin Multiplatform library for building AI-powered LitRPG experiences.

## Overview

RPGenerator is a **game engine library** that handles the complex logic for creating dynamic, AI-driven LitRPG games. It can be integrated into desktop apps, mobile apps, web applications, or Discord bots.

The library manages:
- AI agent orchestration (Game Master, Narrator, NPCs)
- Game state and progression
- Event streaming and persistence
- LitRPG mechanics (stats, quests, inventory)
- Automatic context management for LLM agents

You bring your own LLM provider and your own I/O (voice, text, VR, whatever), and RPGenerator handles the game.

## Quick Start

### Build and Run

```bash
# Run with Gradle
./gradlew :cli:run --console=plain --args="--claude-code"

# Or build the distribution first
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli --claude-code
```

### LLM Options

Set one of these environment variables, or use the corresponding flag:

| Provider | Environment Variable | Flag |
|----------|---------------------|------|
| Claude | `ANTHROPIC_API_KEY` | `--claude` |
| OpenAI | `OPENAI_API_KEY` | `--openai` |
| Google Gemini | `GOOGLE_API_KEY` | `--gemini` |
| Grok (xAI) | `XAI_API_KEY` | `--grok` |
| Claude Code CLI | (requires Claude Pro) | `--claude-code` |
| Mock (no AI) | - | `--mock` |

If no flag is specified, the CLI auto-detects based on available environment variables.

### Other Flags

```
--select-model     Interactively choose which model to use
--model=MODEL_ID   Use a specific model
--list-models      Show all available models
--debug            Start web debug dashboard on http://localhost:8080
--help             Show help
```

## Architecture

```
┌────────────────────────────────────────┐
│     Your Application (Any Platform)    │
│  - UI/UX (terminal, mobile, web, etc)  │
│  - LLM integration (Claude, GPT, etc)  │
└────────────┬───────────────────────────┘
             │ (implements LLMInterface)
┌────────────▼───────────────────────────┐
│      RPGenerator Core Library          │
│  - Game state management               │
│  - Agent orchestration                 │
│  - Event system & persistence          │
│  - Game mechanics                      │
└────────────────────────────────────────┘
```

## Library API

```kotlin
// 1. Implement LLM interface for your provider
class MyLLM(apiKey: String) : LLMInterface {
    override fun startAgent(systemPrompt: String) = MyAgentStream(systemPrompt, apiKey)
}

// 2. Create client and start a game
val client = RPGClient(databasePath = "~/.mygame/saves")
val game = client.startGame(
    config = GameConfig(SystemType.SYSTEM_INTEGRATION, Difficulty.NORMAL),
    llm = MyLLM(apiKey)
)

// 3. Game loop
game.processInput("I want to explore the forest").collect { event ->
    when (event) {
        is GameEvent.NarratorText -> println(event.text)
        is GameEvent.NPCDialogue -> println("${event.npcName}: ${event.text}")
        is GameEvent.SystemNotification -> showNotification(event.text)
        // ... handle other events
    }
}
```

### LLMInterface

```kotlin
interface LLMInterface {
    fun startAgent(systemPrompt: String): AgentStream
}

interface AgentStream {
    suspend fun sendMessage(message: String): Flow<String>
}
```

## Tech Stack

- Kotlin Multiplatform (JVM, iOS, Android)
- Kotlinx Coroutines & Serialization
- SQLDelight for persistence
- Compose Multiplatform for mobile/desktop UI

## License

MIT License - see LICENSE file for details
