# RPGenerator CLI

A simple text-based terminal interface for the RPGenerator LitRPG engine.

## Features

- 🎮 **Interactive Terminal UI** - Colored text output with intuitive menu system
- 🎲 **Character Creation** - Choose your system type, name, difficulty, and stat allocation
- 💾 **Save/Load Games** - Automatically saves game progress and resume anytime
- 📊 **Real-time Stats** - View character stats, inventory, and quests
- 🎨 **Beautiful Output** - Color-coded events (combat, dialogue, quests, loot)
- 🤖 **Multi-LLM Support** - Works with Claude, GPT-4, Gemini, Grok, or mock LLM

## Running the CLI

### Quick Start (Auto-detect LLM)

```bash
# Set your API key (optional - uses mock LLM if not set)
export ANTHROPIC_API_KEY="sk-ant-..."

# Run the CLI
./gradlew :cli:run
./gradlew :cli:run --console=plain --args="--claude-code"
./gradlew :cli:run --console=plain --args="--openai"
```

### Choose Specific LLM

```bash
# Use Claude
./gradlew :cli:run --args="--claude"

# Use OpenAI
./gradlew :cli:run --args="--openai"

# Use Gemini
./gradlew :cli:run --args="--gemini"

# Use Grok
./gradlew :cli:run --args="--grok"

# Use Mock (no API key needed)
./gradlew :cli:run --args="--mock"
```

### See All Options

```bash
./gradlew :cli:run --args="--help"
```

### Using the Distribution

```bash
# Build the distribution
./gradlew :cli:installDist

# Run with your preferred LLM
./cli/build/install/cli/bin/cli --claude
```

## Usage

### Main Menu

When you start the CLI, you'll see the main menu:

```
1. Start New Game
2. Load Saved Game
3. Exit
```

### Character Creation

Choose from:
- **System Types**: System Integration, Cultivation Path, Death Loop, Dungeon Delve, etc.
- **Difficulties**: Easy, Normal, Hard, Nightmare
- **Stat Allocations**: Balanced, Warrior, Mage, Rogue, Tank, or Random

### Gameplay Commands

- **Natural Language Input**: Just type what you want to do!
  - "I attack the goblin"
  - "Talk to the merchant"
  - "Look around"
  - "Go north"

- **Meta Commands**:
  - `stats` or `status` - View character information
  - `help` or `?` - Show help
  - `quit` or `exit` - Save and return to main menu

### Event Types

The CLI displays different event types with distinct colors:

- 📝 **Narration** (white) - Story descriptions and atmosphere
- 💬 **NPC Dialogue** (cyan) - Conversations with NPCs
- ⚔️ **Combat Log** (yellow) - Battle actions and results
- 📊 **Stat Changes** (green/red) - Level ups, damage, healing
- 🎁 **Items** (green) - Loot and rewards
- 📜 **Quests** (magenta) - Quest updates and completions
- ℹ️ **Notifications** (blue) - System messages

## Game Saves

Games are automatically saved to `~/.rpgenerator/games.db` and persist between sessions.

## LLM Support

The CLI supports multiple LLM providers:

### Supported LLMs

1. **Claude** (Anthropic) - `ANTHROPIC_API_KEY`
2. **GPT-4** (OpenAI) - `OPENAI_API_KEY`
3. **Gemini** (Google) - `GOOGLE_API_KEY`
4. **Grok** (xAI) - `XAI_API_KEY`
5. **SimpleLLM** (Mock) - No API key required

### Auto-Detection

The CLI automatically detects which LLM to use based on available environment variables. Priority: Claude > OpenAI > Gemini > Grok > Mock.

### API Keys

Get your API keys from:
- Claude: https://console.anthropic.com/
- OpenAI: https://platform.openai.com/api-keys
- Gemini: https://makersuite.google.com/app/apikey
- Grok: https://x.ai/api

For more details, see [MULTI_LLM_SUPPORT.md](../MULTI_LLM_SUPPORT.md).

## Architecture

```
cli/
├── src/main/kotlin/com/rpgenerator/cli/
│   ├── Main.kt           # Entry point
│   ├── RPGTerminal.kt    # Main UI logic
│   └── SimpleLLM.kt      # Mock LLM implementation
└── build.gradle.kts      # Build configuration
```

## Dependencies

- **rpgenerator-core** - The core game engine
- **kotlinx-coroutines** - Async/Flow support
- **mordant** - Terminal UI with colors and formatting
