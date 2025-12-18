# RPGenerator - Claude Agent Context

## Project Overview

RPGenerator is a Kotlin Multiplatform library for building AI-powered LitRPG games. It orchestrates multiple AI agents (Game Master, Narrator, NPCs) to create dynamic, story-driven gameplay.

## Build & Run

```bash
# Run CLI with debug dashboard
./gradlew :cli:run --console=plain

# Compile only
./gradlew :core:compileKotlinJvm
./gradlew :cli:compileKotlin
```

## Web Debug Dashboard

When running with `--debug` (default), a web dashboard opens at **http://localhost:8080**.

### Dashboard Tabs

| Tab | Description | Data Source |
|-----|-------------|-------------|
| **Terminal** | Game runs in browser via WebSocket | Live game I/O |
| **Logs** | Setup events, AI calls, game events | Event stream |
| **Agents** | All AI agent conversations with system prompts | In-memory agent tracking |
| **Character** | Player stats, inventory, quests, NPCs at location | `Game.getState()` API (in-memory) |
| **Database** | Browse SQLite tables, run queries | Persisted data (after `save()`) |
| **Plan** | Plot graph visualization | Story planning system |

### Key Files for Debug System

- `cli/src/main/kotlin/com/rpgenerator/cli/DebugWebServer.kt` - Web server, WebSocket, all dashboard HTML/JS/CSS
- `cli/src/main/kotlin/com/rpgenerator/cli/RPGTerminal.kt` - Game loop, agent logging integration
- `core/src/commonMain/kotlin/com/rpgenerator/core/api/GameStateSnapshot.kt` - Data exposed to Character tab

### How Data Flows

1. **Character Tab**: Calls `/api/character` → `getCharacterData()` → `game.getState()` → returns `CharacterSheetData` with player stats, inventory, quests, NPCs
2. **Agents Tab**: Real-time tracking via `LoggingLLMInterface` wrapper that intercepts all LLM calls
3. **Database Tab**: Direct SQLite queries via SQLDelight

## Project Structure

```
rpgenerator/
├── core/                          # Multiplatform library (JVM, iOS)
│   └── src/commonMain/kotlin/com/rpgenerator/core/
│       ├── GameImpl.kt            # Game session implementation
│       ├── RPGClientImpl.kt       # Main entry point, game creation/loading
│       ├── agents/                # AI agents
│       │   ├── GameMasterAgent.kt
│       │   ├── NarratorAgent.kt
│       │   ├── NPCAgent.kt
│       │   ├── QuestGeneratorAgent.kt
│       │   └── ConsensusEngine.kt
│       ├── api/                   # Public interfaces
│       │   ├── Game.kt            # Main game interface
│       │   ├── GameEvent.kt       # Event types emitted during gameplay
│       │   ├── GameStateSnapshot.kt # UI-facing state (Character tab data)
│       │   └── LLMInterface.kt    # LLM provider abstraction
│       ├── domain/                # Core game entities
│       │   ├── GameState.kt       # Full internal game state
│       │   ├── CharacterSheet.kt  # Player stats, inventory, skills
│       │   ├── NPC.kt             # NPC data model
│       │   ├── Quest.kt           # Quest system
│       │   └── TierSystem.kt      # Class/tier progression (PlayerClass enum)
│       ├── orchestration/
│       │   └── GameOrchestrator.kt # Main game loop, intent routing
│       ├── story/
│       │   └── StoryPlanningService.kt # Plot graph generation
│       └── persistence/
│           ├── GameRepository.kt  # Save/load game state
│           └── PlotGraphRepository.kt
├── cli/                           # CLI application
│   └── src/main/kotlin/com/rpgenerator/cli/
│       ├── Main.kt                # Entry point
│       ├── RPGTerminal.kt         # Terminal game loop
│       └── DebugWebServer.kt      # Web debug dashboard (2000+ lines)
└── composeApp/                    # Mobile/desktop UI (Compose Multiplatform)
```

## Key Architecture Patterns

### Agent System
- Agents are lazy-initialized (`by lazy`) to only appear in debug UI when used
- Each agent has a system prompt and maintains conversation history
- `LoggingLLMInterface` wraps the actual LLM to intercept and log all calls

### Game State
- `GameState` (internal) - Full mutable state with all game data
- `GameStateSnapshot` (API) - Immutable snapshot for UI display
- State includes: player stats, location, NPCs by location, quests, inventory

### Event System
- `GameOrchestrator.processInput()` returns `Flow<GameEvent>`
- Events: `NarratorText`, `NPCDialogue`, `SystemNotification`, `QuestStarted`, etc.

## Common Tasks

### Adding data to Character tab
1. Add field to `GameStateSnapshot` in `api/GameStateSnapshot.kt`
2. Map it in `GameImpl.getState()`
3. Add to `CharacterSheetData` in `DebugWebServer.kt`
4. Update `getCharacterData()` to populate it
5. Add HTML element and JS rendering in `loadCharacter()`

### Adding a new agent
1. Create agent class in `core/agents/`
2. Add lazy property in `GameOrchestrator`
3. Agent will auto-appear in debug UI when first used

### NPC System
- NPCs stored in `GameState.npcsByLocation: Map<String, List<NPC>>`
- `GameState.getNPCsAtCurrentLocation()` returns NPCs at player's location
- NPC resolution uses fuzzy matching + LLM fallback for ambiguous references

## Debug Session Files

The debug system saves session data to `cli/.rpgenerator-debug/`:

| File | Description |
|------|-------------|
| `agents.md` | Full agent conversations - system prompts + all messages (auto-updated) |
| `terminal.log` | Terminal I/O log with ANSI color codes |

These files persist between sessions and are useful for:
- Reviewing what agents said in previous runs
- Understanding agent behavior patterns
- Debugging issues that occurred in past sessions

## Files to Read First

For understanding the system:
1. `core/.../orchestration/GameOrchestrator.kt` - Main game loop, agent coordination
2. `core/.../api/GameEvent.kt` - All event types
3. `core/.../domain/GameState.kt` - Full state model
4. `cli/.../DebugWebServer.kt` - Web dashboard implementation

For debugging issues:
1. `cli/.rpgenerator-debug/agents.md` - Full agent conversations from last session
2. `cli/.rpgenerator-debug/terminal.log` - Terminal I/O history
3. Agents tab in web dashboard for live system prompts
4. Database tab for persisted state

## LLM Integration

```kotlin
interface LLMInterface {
    fun startAgent(systemPrompt: String): AgentStream
}

interface AgentStream {
    suspend fun sendMessage(message: String): Flow<String>
}
```

Supported providers: Claude, OpenAI, Gemini, Grok, Claude Code CLI, Mock
