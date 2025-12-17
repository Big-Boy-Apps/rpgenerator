# RPGenerator

A platform-agnostic Kotlin library for building AI-powered LitRPG experiences.

## Overview

RPGenerator is a **game engine library** that handles all the complex logic for creating dynamic, AI-driven LitRPG games. It's designed to be integrated into any platform - desktop apps, mobile apps, web applications, or even Discord bots.

The library manages:
- AI agent orchestration (System, Narrator, NPCs)
- Game state and progression
- Event streaming and persistence
- LitRPG mechanics (stats, quests, inventory)
- Automatic context management for LLM agents

**What RPGenerator is NOT:**
- Not a UI framework
- Not tied to any specific platform
- Not opinionated about audio, graphics, or input methods

You bring your own LLM provider (Claude, GPT-4, local models), your own I/O (voice, text, VR, whatever), and RPGenerator handles the game.

## Game Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        PLAYER INPUT                             │
│              (text, voice, button press, etc.)                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GAME ORCHESTRATOR                            │
│                                                                 │
│  First Input Only:                                              │
│  ┌──────────────────────────────────────────┐                  │
│  │ 1. Opening Narration (character → System)│                  │
│  │ 2. Initialize Story NPCs                 │                  │
│  │    └─► Spawn Administrator Aria          │                  │
│  │    └─► Trigger "First Words" Story Beat  │                  │
│  └──────────────────────────────────────────┘                  │
│                                                                 │
│  Process Action:                                                │
│  ┌──────────────────────────────────────────┐                  │
│  │ • Analyze Intent (explore/combat/talk)   │                  │
│  │ • Validate Action                        │                  │
│  │ • Route to appropriate handler           │                  │
│  └──────────────────────────────────────────┘                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                 ┌───────────┴───────────┐
                 │                       │
                 ▼                       ▼
    ┌─────────────────────┐   ┌──────────────────────┐
    │  EXPLORATION        │   │  NPC DIALOGUE        │
    │                     │   │                      │
    │  1. Narrator Agent  │   │  1. Find NPC         │
    │     generates       │   │  2. NPC Agent        │
    │     description     │   │     generates reply  │
    │                     │   │  3. Update           │
    │  2. Game Master     │   │     relationship     │
    │     checks:         │   │                      │
    │     • Spawn NPC?    │   └──────────────────────┘
    │     • Encounter?    │
    │                     │
    │  3. If NPC spawns:  │
    │     ┌─────────────┐ │
    │     │ GM creates  │ │
    │     │ NPC with AI │ │
    │     │      ↓      │ │
    │     │ Add to:     │ │
    │     │ • GameState │ │
    │     │ • NPCMgr    │ │
    │     └─────────────┘ │
    └─────────────────────┘
                 │
                 ▼
    ┌──────────────────────────────┐
    │     EMIT GAME EVENTS         │
    │                              │
    │  • NarratorText              │
    │  • NPCDialogue               │
    │  • SystemNotification        │
    │  • CombatLog                 │
    │  • StatChange                │
    │  • ItemGained                │
    │  • QuestUpdate               │
    └──────────────┬───────────────┘
                   │
                   ▼
    ┌──────────────────────────────┐
    │    YOUR APPLICATION          │
    │  (render however you want)   │
    │                              │
    │  • Display text              │
    │  • Speak with TTS            │
    │  • Show in VR                │
    │  • Update UI                 │
    └──────────────────────────────┘
```

### NPC System: Hardcoded + Dynamic

```
NPC Manager
├── Story NPCs (hardcoded, consistent)
│   ├── Administrator Aria (Tutorial Zone)
│   ├── Warden Kade (The Threshold)
│   └── ... (other major story characters)
│
└── Generated NPCs (AI-created, unique per playthrough)
    ├── Created by Game Master when:
    │   • Player explores new areas
    │   • Story needs supporting characters
    │   • Random encounters trigger
    │
    └── Registered in:
        • GameState (saved with game)
        • NPCManager (queryable)
```

**Both types work identically** - players can talk to them, get quests, build relationships, etc.

## Architecture

```
┌────────────────────────────────────────┐
│     Your Application (Any Platform)    │
│  - UI/UX (terminal, mobile, web, etc)  │
│  - Audio pipeline (TTS/STT) [optional] │
│  - LLM integration (Claude, GPT, etc)  │
└────────────┬───────────────────────────┘
             │ (implements LLMInterface)
┌────────────▼───────────────────────────┐
│      RPGenerator Core Library          │
│  - Game state management               │
│  - Agent orchestration (internal)      │
│  - Event system & logging              │
│  - Persistence (SQLDelight)            │
│  - Game mechanics                      │
└────────────────────────────────────────┘
```

### Sample Implementation: Desktop App

This repository includes a **desktop terminal app** as a reference implementation showing how to integrate the library. It demonstrates:
- Claude API integration (LLMInterface implementation)
- Text-to-speech for narration https://github.com/WhisperSpeech/WhisperSpeech
- Voice input via Whisper
- Terminal UI for stats/inventory

But you could just as easily build:
- A mobile RPG with native UI
- A web-based text adventure
- A VR experience
- A Discord bot that runs campaigns
- A voice-only game for smart speakers

## Public API

### Core Classes

```kotlin
// Main entry point
class RPGClient(databasePath: String) {
    fun getGames(): List<GameInfo>
    suspend fun startGame(config: GameConfig, llm: LLMInterface): Game
    suspend fun resumeGame(gameInfo: GameInfo, llm: LLMInterface): Game
}

// Active game instance
class Game {
    suspend fun processInput(input: String): Flow<GameEvent>
    fun getState(): GameStateSnapshot
    suspend fun save()
}
```

### Your Responsibilities

Implement the `LLMInterface`:

```kotlin
interface LLMInterface {
    val maxContextTokens: Int // Default: 200k
    fun startAgent(systemPrompt: String): AgentStream
}

interface AgentStream {
    suspend fun sendMessage(message: String): Flow<String>
}
```

The library handles everything else internally - you just:
1. Pass in your LLM implementation
2. Send player input (text)
3. Receive game events (also text)
4. Present those events however you want (audio, UI, etc.)

### Game Configuration

```kotlin
val config = GameConfig(
    systemType = SystemType.CULTIVATION,
    difficulty = Difficulty.NORMAL,
    playerPreferences = mapOf(
        "genre" to "xianxia",
        "tone" to "serious"
    )
)
```

**System Types:**
- ✅ `SYSTEM_INTEGRATION`: Standard System apocalypse with powers, levels, and skills (inspired by Defiance of the Fall, Primal Hunter)
- 🚧 `CULTIVATION_PATH`: Pure xianxia cultivation - spiritual realms, dao comprehension, enlightenment
- 🚧 `DEATH_LOOP`: Death makes you stronger - roguelike progression through failures
- 🚧 `DUNGEON_DELVE`: Classic dungeon crawling with permadeath stakes
- 🚧 `ARCANE_ACADEMY`: Magic academy progression - learn spells, advance through ranks
- 🚧 `TABLETOP_CLASSIC`: D&D style - classes, attributes, alignment, traditional tabletop RPG
- 🚧 `EPIC_JOURNEY`: Middle-earth inspired - hobbits to heroes, fellowship dynamics
- 🚧 `ELDRITCH_ASCENT`: Lovecraftian horror progression - sanity, forbidden knowledge
- 🚧 `HERO_AWAKENING`: Superhero origin story - discover and grow your powers

### Event Types

The library emits strongly-typed events:

```kotlin
sealed class GameEvent {
    data class NarratorText(val text: String)
    data class NPCDialogue(val npcId: String, val npcName: String, val text: String)
    data class SystemNotification(val text: String)
    data class CombatLog(val text: String)
    data class StatChange(val statName: String, val oldValue: Int, val newValue: Int)
    data class ItemGained(val itemId: String, val itemName: String, val quantity: Int)
    data class QuestUpdate(val questId: String, val questName: String, val status: QuestStatus)
}
```

## Quick Start - CLI

### 1. Build the CLI

```bash
./gradlew :cli:installDist
```

### 2. Run with Mock LLM (No API Key Required)

**Simple Mode:**
```bash
./cli/build/install/cli/bin/cli --mock
```

**Full-Screen Mode (ncurses-like TUI):**
```bash
./cli/build/install/cli/bin/cli --mock --fullscreen
```

**With AI Image Generation** (requires Python setup):
```bash
./run_with_images.sh --mock
```
See [Image Generation Setup](#ai-image-generation-optional) below.

### 3. Run with Real AI

#### Option A: Use Existing Subscriptions (No Extra Cost!)

**Claude Code CLI** (if you have Claude Pro)
```bash
# Install Claude Code CLI from https://claude.ai/download
claude login
./cli/build/install/cli/bin/cli --claude-code --fullscreen
```

**GitHub Copilot** (if you have Copilot subscription)
```bash
# Get your GitHub token from https://github.com/settings/tokens
export GITHUB_TOKEN="ghp_..."
./cli/build/install/cli/bin/cli --codex --fullscreen
```

#### Option B: Use Free Tier APIs

**Google Gemini** (FREE - Generous limits!)
```bash
export GOOGLE_API_KEY="AIza..."
./cli/build/install/cli/bin/cli --gemini --fullscreen
```
Get free key at: https://aistudio.google.com/app/apikey

#### Option C: Pay-As-You-Go APIs

**Claude API** ($5 free credits for new accounts)
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
./cli/build/install/cli/bin/cli --claude
```
Sign up: https://console.anthropic.com/settings/keys

**OpenAI GPT** ($5 free credits for new accounts)
```bash
export OPENAI_API_KEY="sk-proj-..."
./cli/build/install/cli/bin/cli --openai
```
Sign up: https://platform.openai.com/api-keys

**Grok (xAI)**
```bash
export XAI_API_KEY="xai-..."
./cli/build/install/cli/bin/cli --grok
```
Sign up: https://console.x.ai/

### 4. Model Selection

**View all available models:**
```bash
./cli/build/install/cli/bin/cli --list-models
```

**Interactive model selection:**
```bash
./cli/build/install/cli/bin/cli --claude --select-model
```

**Use specific model:**
```bash
./cli/build/install/cli/bin/cli --openai --model=gpt-5.1-instant
./cli/build/install/cli/bin/cli --claude --model=claude-sonnet-4-5-20250929
```

#### Recommended Models (November 2025):
- **Claude**: `claude-sonnet-4-5-20250929` - Best for complex narratives
- **OpenAI**: `gpt-5.1-instant` - Latest, warmer & more intelligent
- **Gemini**: `gemini-3.0-pro` - Most powerful Gemini
- **Grok**: `grok-4-1-fast-reasoning` - Latest xAI with reasoning

### Troubleshooting

**Database Error: "table Game already exists"**
```bash
rm ~/.rpgenerator/games.db
```

**API Key Not Working**
- Verify correct environment variable name (ANTHROPIC_API_KEY, OPENAI_API_KEY, etc.)
- Check API key format (sk-ant-, sk-proj-, AIza, xai- prefix)
- Ensure account has credits/quota

## AI Image Generation (Optional)

Generate fantasy scene images in real-time as you play! Uses Stable Diffusion 1.5 locally on your M1/M2/M3 Mac.

### Features
- **Fast**: 3-6 seconds per image on Apple Silicon
- **Local**: Runs entirely on your machine, no API costs
- **Automatic**: Images generated for major scene descriptions
- **Opens Automatically**: Generated images pop open as you play

### Setup

1. **Run the game with images enabled**:
   ```bash
   # Easy way - auto-starts service
./gradlew :cli:run --console=plain --args="--claude-code"
   ```

This for sure works, I haven't really tested the api keys yet but I think they should work. 

### How It Works

When the narrator describes a scene, the CLI:
1. Extracts the visual description from the narration
2. Sends it to the local Stable Diffusion service
3. Generates a 512x512 fantasy art image (~3-6 seconds)
4. Saves to `~/.rpgenerator/images/` and opens it automatically

### Examples

Input narration:
> "You stand at the edge of a dark forest. Ancient trees loom overhead, their gnarled branches twisting into the misty sky..."

Generated image: A detailed fantasy art scene of a dark, atmospheric forest with ancient twisted trees and mist.

### Requirements

- **Apple Silicon Mac** (M1/M2/M3) - uses Metal Performance Shaders
- **Python 3.9+**
- **~8GB disk space** for models
- **~4GB RAM** during generation

See [cli/image_gen/README.md](cli/image_gen/README.md) for detailed docs and troubleshooting.

## Library API

```kotlin
// 1. Implement LLM interface for your provider
class ClaudeLLM(apiKey: String) : LLMInterface {
    override fun startAgent(systemPrompt: String) = ClaudeAgentStream(systemPrompt, apiKey)
}

// 2. Create client and start a game
val client = RPGClient(databasePath = "~/.mygame/saves")
val game = client.startGame(
    config = GameConfig(SystemType.CULTIVATION, Difficulty.NORMAL),
    llm = ClaudeLLM(apiKey)
)

// 3. Game loop
game.processInput("I want to explore the forest").collect { event ->
    when (event) {
        is GameEvent.NarratorText -> println(event.text) // or speak it!
        is GameEvent.NPCDialogue -> println("${event.npcName}: ${event.text}")
        is GameEvent.SystemNotification -> showNotification(event.text)
        // ... handle other events
    }
}
```

## Tech Stack

**Core Library:**
- Kotlin 2.2.20 (JVM target, easily portable to multiplatform)
- Kotlinx Coroutines for async/streaming
- Kotlinx Serialization for state management
- SQLDelight for cross-platform persistence
- Turbine for Flow testing

**Sample Desktop App:**
- Kotlin/JVM
- Anthropic API (Claude)
- OpenAI Whisper (STT)
- ElevenLabs/OpenAI TTS

## Features

### Core Library
- ✅ Platform-agnostic game engine
- ✅ Streaming event system
- ✅ Multiple AI agent orchestration (internal)
- ✅ Automatic agent context management
- ✅ Persistent game state (SQLDelight)
- ✅ Multiple System types
- ✅ Extensible event system

### Sample Desktop App
- 🚧 Claude integration
- 🚧 Voice input/output
- 🚧 Terminal UI

### Roadmap
- 🚧 Combat system
- 🚧 Character progression
- 🚧 Multiplatform core (Native, JS, etc.)
- 🚧 Mobile sample app
- 🚧 Web sample app
- 🚧 Community System types

## Vision

Inspired by LitRPG novels like *Defiance of the Fall* and tabletop RPGs, RPGenerator aims to create fully generative RPG experiences where:

- An AI-powered "System" manages your progression, stats, and quests
- NPCs are intelligent agents with personalities and memory
- The story dynamically adapts to your choices and actions
- Games can be experienced through any medium (audio, text, VR, etc.)

## Contributing

This is an open-source project! We welcome:
- Core library improvements
- New System type implementations
- Sample applications for different platforms
- Bug reports and feature requests
- Documentation improvements

## License

MIT License - see LICENSE file for details

---

**Note:** This library is in early development. API is subject to change.
