package com.rpgenerator.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.rpgenerator.core.api.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

class RPGTerminal(
    private val client: RPGClient,
    private val baseLlm: LLMInterface,
    private val debugMode: Boolean = false
) {

    private val terminal = Terminal()
    private var currentGame: Game? = null
    private var debugWebServer: DebugWebServer? = null

    // Wrap LLM with logging when in debug mode
    private val llm: LLMInterface = if (debugMode) LoggingLLMWrapper(baseLlm) { debugWebServer } else baseLlm

    /**
     * LLM wrapper that logs all agent conversations to the debug server.
     */
    private class LoggingLLMWrapper(
        private val delegate: LLMInterface,
        private val debugServerProvider: () -> DebugWebServer?
    ) : LLMInterface {
        private var agentCounter = 0

        override fun startAgent(systemPrompt: String): AgentStream {
            val agentId = "agent_${++agentCounter}_${System.currentTimeMillis()}"
            val agentType = inferAgentType(systemPrompt)

            debugServerProvider()?.logAgentStart(agentId, agentType, systemPrompt)

            return LoggingAgentStream(delegate.startAgent(systemPrompt), agentId, agentType, debugServerProvider)
        }

        private fun inferAgentType(systemPrompt: String): String {
            val prompt = systemPrompt.lowercase()
            return when {
                "narrator" in prompt -> "Narrator"
                "game master" in prompt || "dungeon master" in prompt -> "GameMaster"
                "npc" in prompt || "character" in prompt && "roleplay" in prompt -> "NPC"
                "combat" in prompt || "battle" in prompt -> "CombatManager"
                "quest" in prompt -> "QuestManager"
                "backstory" in prompt -> "BackstoryGenerator"
                "stat" in prompt && "assign" in prompt -> "StatsGenerator"
                "playstyle" in prompt || "goal" in prompt -> "PlaystyleExpander"
                "refine" in prompt -> "Refiner"
                else -> "Agent"
            }
        }
    }

    private class LoggingAgentStream(
        private val delegate: AgentStream,
        private val agentId: String,
        private val agentType: String,
        private val debugServerProvider: () -> DebugWebServer?
    ) : AgentStream {
        private var hasUpdatedName = false

        override suspend fun sendMessage(message: String): Flow<String> {
            debugServerProvider()?.logAgentMessage(agentId, "user", message)

            // Try to extract a more specific name from the message
            if (!hasUpdatedName) {
                val extractedName = extractAgentName(agentType, message)
                if (extractedName != null) {
                    debugServerProvider()?.updateAgentName(agentId, extractedName)
                    hasUpdatedName = true
                }
            }

            val chunks = mutableListOf<String>()
            return delegate.sendMessage(message)
                .onEach { chunk ->
                    chunks.add(chunk)
                }
                .onCompletion {
                    val fullResponse = chunks.joinToString("")
                    debugServerProvider()?.logAgentMessage(agentId, "assistant", fullResponse)
                }
        }

        private fun extractAgentName(type: String, message: String): String? {
            return when {
                // NPC archetype generator - extract "Pre-assigned name: X"
                type == "NPC" && message.contains("Pre-assigned name:") -> {
                    val match = Regex("Pre-assigned name:\\s*([^\\n]+)").find(message)
                    match?.groupValues?.get(1)?.trim()?.let { "NPC: $it" }
                }
                // NPC dialogue - extract NPC name from context
                type == "NPC" && message.contains("You are roleplaying as") -> {
                    val match = Regex("roleplaying as ([^,\\.]+)").find(message)
                    match?.groupValues?.get(1)?.trim()?.let { "NPC: $it" }
                }
                // Backstory generator - extract character name
                type == "BackstoryGenerator" && message.contains("character named") -> {
                    val match = Regex("character named \"([^\"]+)\"").find(message)
                    match?.groupValues?.get(1)?.trim()?.let { "Backstory: $it" }
                }
                else -> null
            }
        }
    }

    // Track current action options for numbered selection
    private var currentActionOptions: List<String> = emptyList()

    // I/O wrapper methods for debug mode routing
    private fun out(text: String = "", newline: Boolean = true) {
        val output = if (newline) "$text\n" else text
        if (debugMode && debugWebServer != null) {
            debugWebServer?.writeToTerminal(output)
        } else {
            if (newline) terminal.println(text) else terminal.print(text)
        }
    }

    private fun input(): String {
        return if (debugMode && debugWebServer?.isTerminalConnected() == true) {
            runBlocking { debugWebServer?.readFromTerminal() ?: "" }
        } else {
            readLine() ?: ""
        }
    }

    // Styled output helpers
    private fun outStyled(text: String, newline: Boolean = true) {
        // Strip ANSI codes for web terminal or keep for local
        out(text, newline)
    }

    // Debug-aware loading animation
    private var loadingMessageShown = false

    private fun showLoading(message: String): kotlinx.coroutines.Job {
        return if (debugMode && debugWebServer?.isTerminalConnected() == true) {
            // In debug mode, show static message in web terminal
            loadingMessageShown = true
            out(dim("⏳ $message").toString())
            // Return a no-op job
            kotlinx.coroutines.Job()
        } else {
            // Use normal loading animation for local terminal
            LoadingAnimation.showCustom(
                listOf(
                    "⠋ $message  ",
                    "⠙ $message. ",
                    "⠹ $message..",
                    "⠸ $message..."
                ),
                100
            )
        }
    }

    private fun clearLoading(job: kotlinx.coroutines.Job) {
        job.cancel()
        if (debugMode && debugWebServer?.isTerminalConnected() == true) {
            // In debug mode, nothing to clear (message already printed)
            loadingMessageShown = false
        } else {
            print("\r" + " ".repeat(80) + "\r")
        }
    }

    fun run() = runBlocking {
        // Start debug web server immediately if in debug mode
        if (debugMode) {
            startWebDebugServer(null)
            // Wait for browser connection before showing menu
            println("Debug dashboard started at http://localhost:8080")
            println("Waiting for browser connection...")

            // Auto-open browser
            openBrowser("http://localhost:8080")

            // Wait for WebSocket connection
            var attempts = 0
            while (debugWebServer?.isTerminalConnected() != true && attempts < 30) {
                Thread.sleep(500)
                attempts++
            }

            if (debugWebServer?.isTerminalConnected() == true) {
                println("Browser connected! Game running in web terminal.")
            } else {
                println("Warning: Browser not connected. Running in local terminal.")
            }
        }

        out((bold + cyan)("=".repeat(60)).toString())
        out((bold + cyan)("      RPGenerator - LitRPG Adventure Engine").toString())
        out((bold + cyan)("=".repeat(60)).toString())
        out()

        while (true) {
            showMainMenu()
        }
    }

    private fun openBrowser(url: String) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
                os.contains("win") -> Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
                os.contains("nix") || os.contains("nux") -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            }
        } catch (e: Exception) {
            // Silently fail - user can open manually
        }
    }

    private suspend fun showMainMenu() {
        out()
        out(yellow("Main Menu:").toString())
        out("  ${green("1.")} Start New Game")
        out("  ${green("2.")} Load Saved Game")
        out("  ${green("3.")} Exit")
        out()
        out(brightBlue("> ").toString(), newline = false)

        when (input().trim()) {
            "1" -> startNewGame()
            "2" -> loadSavedGame()
            "3" -> {
                out(magenta("Thanks for playing!").toString())
                kotlin.system.exitProcess(0)
            }
            else -> out(red("Invalid choice. Please enter 1, 2, or 3.").toString())
        }
    }

    private suspend fun startNewGame() {
        setupLoop@ while (true) {
            out()
            out((bold + yellow)("=== Game Setup ==="))
            out()

            // Step 1: Playstyle / Goal Selection
            var playstyleMode: Int? = null
            while (playstyleMode == null) {
                out(cyan("How would you like to define your playstyle?"))
                out("  ${green("1.")} Enter your own goal/playstyle")
                out("  ${green("2.")} Select from preset playstyles")
                out("  ${green("3.")} Unknown - Let the game adapt to you")
                out("  ${yellow("B.")} Back to main menu")
                out(brightBlue("> ").toString(), newline = false)

                when (val input = input().trim()?.uppercase()) {
                    "B", "BACK" -> return // Return to main menu
                    "1", "2", "3" -> playstyleMode = input.toInt()
                    else -> out(red("Invalid choice. Please enter 1, 2, 3, or B to go back."))
                }
            }

            // Step 2: Get playstyle details based on mode
            var playstyle = "unknown"
            var playstyleDescription = ""
            var playstyleConfirmed = false

            while (!playstyleConfirmed) {
                when (playstyleMode) {
                    1 -> {
                        // Custom playstyle
                        out()
                        out(yellow("Describe your goal or playstyle (e.g., 'I want to build a merchant empire' or 'Focus on stealth and assassinations'):").toString())
                        out("  ${yellow("B.")} Back")
                        out(brightBlue("> ").toString(), newline = false)
                        val userGoal = input().trim()
                        if (userGoal?.uppercase() == "B" || userGoal?.uppercase() == "BACK") {
                            playstyleMode = null // Go back to step 1
                            out()
                            continue@setupLoop
                        }
                        if (!userGoal.isNullOrEmpty()) {
                            playstyleDescription = expandPlaystyleGoal(userGoal)
                            playstyle = "custom"
                            playstyleConfirmed = true
                        } else {
                            playstyle = "unknown"
                            playstyleDescription = "Adapt to the player's choices and actions as they play."
                            playstyleConfirmed = true
                        }
                    }
                    2 -> {
                        // Preset playstyles
                        out()
                        out(cyan("Select a preset playstyle:"))
                        out("  ${green("1.")} Power Fantasy - Become overpowered, rapid progression")
                        out("  ${green("2.")} Story & Roleplay - Rich narrative, character development")
                        out("  ${green("3.")} Challenge & Strategy - Difficult tactical gameplay")
                        out("  ${green("4.")} Exploration & Discovery - Uncover secrets and lore")
                        out("  ${green("5.")} Balanced - Mix of all the above")
                        out("  ${yellow("B.")} Back")
                        out(brightBlue("> ").toString(), newline = false)

                        when (val input = input().trim()?.uppercase()) {
                            "B", "BACK" -> {
                                playstyleMode = null
                                out()
                                continue@setupLoop
                            }
                            "1" -> {
                                playstyle = "power_fantasy"
                                playstyleDescription = "The player wants to feel powerful and see rapid progression. Provide exciting power-ups, satisfying victories, and opportunities to dominate challenges."
                                playstyleConfirmed = true
                            }
                            "2" -> {
                                playstyle = "story_roleplay"
                                playstyleDescription = "The player values narrative depth and character development. Create compelling NPCs, moral dilemmas, and story-driven choices with consequences."
                                playstyleConfirmed = true
                            }
                            "3" -> {
                                playstyle = "challenge_strategy"
                                playstyleDescription = "The player seeks difficult tactical gameplay. Present challenging encounters that require planning, resource management, and strategic thinking."
                                playstyleConfirmed = true
                            }
                            "4" -> {
                                playstyle = "exploration_discovery"
                                playstyleDescription = "The player loves uncovering secrets and exploring. Include hidden areas, mysterious lore, and rewards for curiosity."
                                playstyleConfirmed = true
                            }
                            "5" -> {
                                playstyle = "balanced"
                                playstyleDescription = "The player wants a balanced experience with elements of power progression, story, challenge, and exploration."
                                playstyleConfirmed = true
                            }
                            else -> out(red("Invalid choice. Please enter 1-5 or B to go back."))
                        }
                    }
                    3 -> {
                        // Unknown - adapt dynamically
                        playstyle = "unknown"
                        playstyleDescription = "Adapt to the player's choices and actions as they play. Pay attention to what they engage with and adjust accordingly."
                        playstyleConfirmed = true
                    }
                }
            }

            // Log playstyle selection
            debugWebServer?.logSetupEvent(
                eventType = "PLAYSTYLE_SELECTED",
                category = "SETUP",
                text = "Playstyle: $playstyle - $playstyleDescription"
            )

            // Step 3: Character Name
            out()
            out((bold + yellow)("=== Character Creation ==="))
            out()

            var name: String? = null
            while (name == null) {
                out(cyan("Enter your character name:").toString())
                out("  ${yellow("B.")} Back")
                out(brightBlue("> ").toString(), newline = false)
                val input = input().trim()
                if (input?.uppercase() == "B" || input?.uppercase() == "BACK") {
                    playstyleMode = null
                    playstyleConfirmed = false
                    out()
                    continue@setupLoop
                }
                name = input?.takeIf { it.isNotEmpty() } ?: "Adventurer"
            }

            // Log character name
            debugWebServer?.logSetupEvent(
                eventType = "CHARACTER_NAMED",
                category = "SETUP",
                text = "Character name: $name"
            )

            // Step 4: Character Backstory
            var backstory: String? = null
            var backstoryMode: Int? = null

            while (backstoryMode == null) {
                out()
                out(cyan("Character Backstory:").toString())
                out("  ${green("1.")} Write your own backstory")
                out("  ${green("2.")} Auto-generate backstory")
                out("  ${yellow("B.")} Back")
                out(brightBlue("> ").toString(), newline = false)

                when (val input = input().trim()?.uppercase()) {
                    "B", "BACK" -> {
                        name = null
                        out()
                        continue@setupLoop
                    }
                    "1" -> {
                        backstoryMode = 1
                        out()
                        out(yellow("Enter your character's backstory (press Enter twice when done, or type 'BACK' to go back):"))
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = input() ?: break
                            if (line.trim().uppercase() == "BACK") {
                                backstoryMode = null
                                break
                            }
                            if (line.isEmpty() && lines.isNotEmpty()) break
                            if (line.isNotEmpty()) lines.add(line)
                        }
                        if (backstoryMode != null) {
                            backstory = lines.joinToString(" ").trim().takeIf { it.isNotEmpty() }
                        }
                    }
                    "2" -> {
                        backstoryMode = 2
                        backstory = generateBackstory(name!!)
                    }
                    else -> out(red("Invalid choice. Please enter 1, 2, or B to go back."))
                }
            }

            // Show generated backstory and allow refresh
            var backstoryConfirmed = false
            while (!backstoryConfirmed && backstory != null) {
                out()
                out(cyan("=== Backstory ==="))
                out(backstory!!)
                out()
                out("  ${green("1.")} Keep this backstory")
                out("  ${green("2.")} Generate new backstory")
                out("  ${green("3.")} Edit backstory")
                out("  ${yellow("B.")} Back")
                out(brightBlue("> ").toString(), newline = false)

                when (val input = input().trim()?.uppercase()) {
                    "1" -> backstoryConfirmed = true
                    "2" -> backstory = generateBackstory(name!!)
                    "3" -> {
                        out()
                        out(yellow("Enter details/changes you want (e.g., 'make them a teacher instead' or 'add more about hobbies'):").toString())
                        out("  ${yellow("B.")} Back")
                        out(brightBlue("> ").toString(), newline = false)
                        val userPrompt = input().trim()
                        if (userPrompt?.uppercase() == "B" || userPrompt?.uppercase() == "BACK") {
                            // Do nothing, loop continues
                        } else if (!userPrompt.isNullOrEmpty()) {
                            backstory = refineBackstory(name!!, backstory!!, userPrompt)
                        }
                    }
                    "B", "BACK" -> {
                        backstoryMode = null
                        out()
                        continue@setupLoop
                    }
                    else -> out(red("Invalid choice. Please enter 1, 2, 3, or B to go back."))
                }
            }

            // Generate stats from backstory
            val customStats = if (backstory != null) {
                generateStatsFromBackstory(name!!, backstory)
            } else {
                null
            }

            // Default to Normal difficulty
            val difficulty = Difficulty.NORMAL

            // Create game config - using SYSTEM_INTEGRATION as the only supported type
            val config = GameConfig(
                systemType = SystemType.SYSTEM_INTEGRATION,
                difficulty = difficulty,
                characterCreation = CharacterCreationOptions(
                    name = name!!,
                    backstory = backstory,
                    statAllocation = if (customStats != null) StatAllocation.CUSTOM else StatAllocation.BALANCED,
                    customStats = customStats
                ),
                playerPreferences = mapOf(
                    "playstyle" to playstyle,
                    "playstyle_description" to playstyleDescription
                )
            )

            out()

            // Show loading animation while creating the game and generating first scene
            val loadingJob = showLoading("Creating your adventure...")

            // Start the game
            currentGame = try {
                val game = client.startGame(config, llm)

                // Don't call processInput here - let gameLoop() handle the opening scene
                game
            } finally {
                clearLoading(loadingJob)
            }

            if (!debugMode) LoadingAnimation.beep()
            out(green("✓ Ready to begin your adventure!"))
            out()

            // Log game creation
            debugWebServer?.logSetupEvent(
                eventType = "GAME_CREATED",
                category = "SETUP",
                text = "Game created! Character: $name, Playstyle: $playstyle, Difficulty: ${difficulty.name}",
                importance = "HIGH"
            )

            // Auto-start web debug server if in debug mode
            if (debugMode && currentGame != null) {
                startWebDebugServer(currentGame!!)
            }

            // Exit the setup loop
            break
        }

        // Start game loop
        gameLoop()
    }

    private suspend fun loadSavedGame() {
        val games = client.getGames()

        if (games.isEmpty()) {
            out(red("No saved games found."))
            return
        }

        out()
        out(yellow("=== Saved Games ==="))
        games.forEachIndexed { index, game ->
            out()
            out("${green("${index + 1}.")} ${bold(game.playerName)} - Level ${game.level}")
            out("   System: ${game.systemType.name.replace("_", " ")}")
            out("   Difficulty: ${game.difficulty}")
            out("   Playtime: ${formatPlaytime(game.playtime)}")
            out("   Last Played: ${formatTimestamp(game.lastPlayedAt)}")
        }

        out()
        out(brightBlue("Select game (1-${games.size}) or 0 to cancel: ").toString(), newline = false)
        val choice = input().toIntOrNull() ?: 0

        if (choice in 1..games.size) {
            val gameInfo = games[choice - 1]

            // Show loading animation while loading the game
            val loadingJob = showLoading("Loading saved game...")

            currentGame = try {
                client.resumeGame(gameInfo, llm)
            } finally {
                clearLoading(loadingJob)
            }

            if (!debugMode) LoadingAnimation.beep()
            out(green("✓ Game loaded successfully!"))
            out()

            // Auto-start web debug server if in debug mode
            if (debugMode && currentGame != null) {
                startWebDebugServer(currentGame!!)
            }

            gameLoop()
        }
    }

    private suspend fun gameLoop() {
        val game = currentGame ?: return

        out()
        out((bold + cyan)("=".repeat(60)))
        out((bold + cyan)("      Adventure Begins"))
        out((bold + cyan)("=".repeat(60)))
        out()

        // Show initial scene with loading animation
        val loadingJob = showLoading("System is processing...")
        var loadingCleared = false

        try {
            game.processInput("").collect { event ->
                // Cancel loading on first event
                if (!loadingCleared) {
                    clearLoading(loadingJob)
                    loadingCleared = true
                    if (!debugMode) LoadingAnimation.beep()
                }

                handleGameEvent(event)
            }
        } catch (e: Exception) {
            if (!loadingCleared) clearLoading(loadingJob)
            out(red("Error loading initial scene: ${e.message}"))
        }

        while (true) {
            out(brightBlue("\n> ").toString(), newline = false)
            var input = input().trim() ?: continue

            if (input.isEmpty()) continue

            // Check if input is a numbered action selection (1-9)
            val actionNumber = input.toIntOrNull()
            if (actionNumber != null && actionNumber in 1..currentActionOptions.size) {
                input = currentActionOptions[actionNumber - 1]
                out(dim("→ $input"))
            }

            // Handle meta commands
            when (input.lowercase()) {
                "quit", "exit" -> {
                    out(magenta("Saving game..."))
                    game.save()
                    out(green("✓ Game saved!"))
                    debugWebServer?.stop()
                    currentGame = null
                    return
                }
                "help", "?" -> {
                    showHelp()
                    continue
                }
                "stats", "status" -> {
                    showStatus(game)
                    continue
                }
                "debug", "dev" -> {
                    if (debugMode) {
                        out(cyan("Debug web dashboard running at: ${bold("http://localhost:8080")}"))
                    } else {
                        showDebugView(game)
                    }
                    continue
                }
            }

            // Process game input
            out()

            // Show loading animation while AI is thinking
            val thinkingJob = showLoading("System is processing...")
            var thinkingCleared = false

            try {
                game.processInput(input).collect { event ->
                    // Cancel loading on first event
                    if (!thinkingCleared) {
                        clearLoading(thinkingJob)
                        thinkingCleared = true
                        if (!debugMode) LoadingAnimation.beep()
                    }

                    handleGameEvent(event)
                }
            } catch (e: Exception) {
                if (!thinkingCleared) clearLoading(thinkingJob)
                out(red("Error: ${e.message}"))
            }
        }
    }

    private suspend fun handleGameEvent(event: GameEvent) {
        when (event) {
            is GameEvent.NarratorText -> {
                // Parse action options from the text (lines starting with ">")
                val (narrativeText, actionOptions) = parseActionOptions(event.text)

                // Display the narrative
                out(narrativeText)

                // Display action options with numbers
                if (actionOptions.isNotEmpty()) {
                    currentActionOptions = actionOptions
                    out()
                    out(yellow("Actions:"))
                    actionOptions.forEachIndexed { index, action ->
                        out(green("  ${index + 1}. ") + action)
                    }
                    out(dim("  (or type your own action)"))
                }
            }
            is GameEvent.NPCDialogue -> {
                out()
                out(cyan("${bold(event.npcName)}: ") + event.text)
            }
            is GameEvent.CombatLog -> {
                out(yellow("⚔ ") + event.text)
            }
            is GameEvent.StatChange -> {
                val symbol = if (event.newValue > event.oldValue) "↑" else "↓"
                val color = if (event.newValue > event.oldValue) green else red
                out(color("  $symbol ${event.statName}: ${event.oldValue} → ${event.newValue}"))
            }
            is GameEvent.ItemGained -> {
                out(green("  + ${event.itemName} x${event.quantity}"))
            }
            is GameEvent.QuestUpdate -> {
                when (event.status) {
                    QuestStatus.NEW -> {
                        out()
                        out(magenta("📜 New Quest: ") + bold(event.questName))
                    }
                    QuestStatus.COMPLETED -> {
                        out()
                        out(green("✓ Quest Completed: ") + bold(event.questName))
                    }
                    QuestStatus.FAILED -> {
                        out()
                        out(red("✗ Quest Failed: ") + bold(event.questName))
                    }
                    QuestStatus.IN_PROGRESS -> {
                        out(yellow("  Quest updated: ") + event.questName)
                    }
                }
            }
            is GameEvent.SystemNotification -> {
                out(brightBlue("ℹ ") + event.text)
            }
        }
    }

    private fun showHelp() {
        out()
        out(yellow("=== Commands ==="))
        out("  ${green("stats/status")} - View character stats")
        if (debugMode) {
            out("  ${green("debug/dev")} - Show web debug dashboard URL")
        } else {
            out("  ${green("debug/dev")} - View debug information (agents, plot, world state)")
        }
        out("  ${green("quit/exit")} - Save and return to main menu")
        out("  ${green("help/?")} - Show this help")
        out()
        out(yellow("=== Gameplay ==="))
        out("  Just type naturally! Examples:")
        out("    - I attack the goblin")
        out("    - Talk to the merchant")
        out("    - Look around")
        out("    - Go north")
    }

    private suspend fun showStatus(game: Game) {
        val state = game.getState()
        out()
        out(cyan("=== Character Status ==="))
        out("${bold("Name:")} ${state.playerStats.name}")
        out("${bold("Level:")} ${state.playerStats.level}")
        out("${bold("XP:")} ${state.playerStats.experience} / ${state.playerStats.experienceToNextLevel}")
        out("${bold("HP:")} ${state.playerStats.health} / ${state.playerStats.maxHealth}")
        out()
        out(cyan("=== Stats ==="))
        state.playerStats.stats.forEach { (name, value) ->
            out("  ${name.replaceFirstChar { it.uppercase() }}: $value")
        }
        out()
        out(cyan("=== Location ==="))
        out("  ${state.location}")
    }

    private suspend fun showDebugView(game: Game) {
        out()
        out(yellow("Generating debug view..."))
        out()

        try {
            val debugText = client.getDebugView(game)
            out(debugText)
        } catch (e: Exception) {
            out(red("Error generating debug view: ${e.message}"))
            e.printStackTrace()
        }

        out()
        out(brightBlue("Press Enter to continue..."))
        input()
    }

    private fun startWebDebugServer(game: Game?) {
        if (debugWebServer == null) {
            out()
            out(yellow("Starting web debug dashboard..."))

            debugWebServer = DebugWebServer(client, port = 8080)
            debugWebServer?.start(game)

            // Give the server a moment to start
            Thread.sleep(500)

            out(green("✓ Web debug dashboard started!"))
            out(cyan("   Open your browser to: ${bold("http://localhost:8080")}"))
            out(gray("   (Auto-refreshes every 5 seconds)"))
            out()
        } else if (game != null) {
            // Update the game reference
            debugWebServer?.updateGame(game)
        }
    }

    private fun formatPlaytime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    private fun formatTimestamp(unixTimestamp: Long): String {
        val date = java.util.Date(unixTimestamp * 1000)
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm")
        return formatter.format(date)
    }

    private suspend fun generateStatsFromBackstory(name: String, backstory: String): CustomStats? {
        out()
        val loadingJob = showLoading("Analyzing character...")
        val agentId = "stats_gen_${++agentCounter}"

        return try {
            val systemPrompt = """
                You are a game designer analyzing character backstories to determine appropriate RPG stats.
                Interpret the character's background to assign D&D-style ability scores (3-18 range).
                Average person has 10 in each stat. Total should be around 60-70 points.
                Be realistic - not everyone is exceptional.
                """.trimIndent()

            // LoggingLLMInterface handles agent logging automatically
            val agentStream = llm.startAgent(systemPrompt)

            val userMessage = """
                Analyze this character and assign stats:
                Name: $name
                Backstory: $backstory

                Provide ONLY six numbers (3-18) separated by commas in this exact order:
                Strength,Dexterity,Constitution,Intelligence,Wisdom,Charisma

                Guidelines:
                - Physical job (construction, athlete) -> higher STR/CON
                - Desk job (programmer, writer) -> higher INT
                - Active lifestyle (hiking, sports) -> higher DEX/CON
                - Social job (teacher, salesperson) -> higher CHA
                - Experienced/older -> higher WIS
                - Average person should be around 10 in most stats
                - Total should be 60-70 points

                Output ONLY the six comma-separated numbers, nothing else.
                """.trimIndent()

            debugWebServer?.logAgentMessage(agentId, "user", userMessage)

            val response = agentStream.sendMessage(userMessage).toList().joinToString("")

            debugWebServer?.logAgentMessage(agentId, "assistant", response)

            // Parse the response
            val numbers = response.trim().split(",").mapNotNull { it.trim().toIntOrNull() }
            if (numbers.size == 6 && numbers.all { it in 3..18 }) {
                val stats = CustomStats(
                    strength = numbers[0],
                    dexterity = numbers[1],
                    constitution = numbers[2],
                    intelligence = numbers[3],
                    wisdom = numbers[4],
                    charisma = numbers[5]
                )

                // Log stats generation
                debugWebServer?.logSetupEvent(
                    eventType = "AI_STATS_GENERATED",
                    category = "AI_CALL",
                    text = "Generated stats for $name from backstory: STR=${stats.strength}, DEX=${stats.dexterity}, CON=${stats.constitution}, INT=${stats.intelligence}, WIS=${stats.wisdom}, CHA=${stats.charisma} (Total: ${stats.total()})",
                    importance = "HIGH"
                )

                stats
            } else {
                debugWebServer?.logSetupEvent(
                    eventType = "AI_STATS_FAILED",
                    category = "AI_CALL",
                    text = "Failed to parse stats from AI response: $response",
                    importance = "HIGH"
                )
                null
            }
        } finally {
            clearLoading(loadingJob)
            if (!debugMode) LoadingAnimation.beep()
        }
    }

    private var agentCounter = 0

    private suspend fun expandPlaystyleGoal(userGoal: String): String {
        out()
        val loadingJob = showLoading("Processing goal...")
        val agentId = "playstyle_${++agentCounter}"

        return try {
            val systemPrompt = """
                You are a game design assistant helping convert player goals into actionable DM instructions.
                Take a player's stated goal or playstyle and expand it into clear guidelines for the game master.
                """.trimIndent()

            val agentStream = llm.startAgent(systemPrompt)

            val userMessage = """
                Player's goal/playstyle: "$userGoal"

                Convert this into 2-3 sentences of clear DM instructions that explain:
                - What the player wants to achieve or experience
                - How to bias encounters, rewards, and narrative to support this goal
                - What to emphasize and what to de-emphasize

                Example:
                Input: "I want to build a merchant empire"
                Output: "The player wants to focus on economics and trade. Emphasize opportunities for buying/selling, negotiating deals, and building business relationships. Present challenges related to supply chains, competition, and market dynamics rather than pure combat."

                Write the DM instructions now:
                """.trimIndent()

            debugWebServer?.logAgentMessage(agentId, "user", userMessage)

            val expanded = agentStream.sendMessage(userMessage).toList().joinToString("")

            debugWebServer?.logAgentMessage(agentId, "assistant", expanded)

            // Log playstyle expansion
            debugWebServer?.logSetupEvent(
                eventType = "AI_PLAYSTYLE_EXPANDED",
                category = "AI_CALL",
                text = "User goal: \"$userGoal\" -> Expanded: ${expanded.trim().take(150)}...",
                importance = "HIGH"
            )

            expanded.trim()
        } finally {
            clearLoading(loadingJob)
            if (!debugMode) LoadingAnimation.beep()
        }
    }

    private suspend fun refineBackstory(name: String, currentBackstory: String, userPrompt: String): String {
        out()
        val loadingJob = showLoading("Refining backstory...")
        val agentId = "backstory_refine_${++agentCounter}"

        return try {
            val systemPrompt = """
                You are a creative writer helping refine character backstories for a LitRPG System Integration story.
                Take the user's feedback and recreate the backstory incorporating their requested changes.
                Keep the character interesting, confident, and memorable.
                """.trimIndent()

            val agentStream = llm.startAgent(systemPrompt)

            val userMessage = """
                Current backstory for $name:
                $currentBackstory

                User requested changes:
                $userPrompt

                Recreate the backstory incorporating these changes while maintaining:
                - Physical description in first sentence (age, one distinctive trait)
                - Modern day setting (2025)
                - 4-5 sentences total
                - Confident, engaging tone with personality
                - Something they're good at or proud of
                - No System, magic, or fantasy elements
                - Avoid melancholy or "quiet desperation" vibes

                Output the complete revised backstory.
                """.trimIndent()

            debugWebServer?.logAgentMessage(agentId, "user", userMessage)

            val backstory = agentStream.sendMessage(userMessage).toList().joinToString("")

            debugWebServer?.logAgentMessage(agentId, "assistant", backstory)

            // Log backstory refinement
            debugWebServer?.logSetupEvent(
                eventType = "AI_BACKSTORY_REFINED",
                category = "AI_CALL",
                text = "User requested: \"$userPrompt\" -> Refined backstory: ${backstory.trim().take(150)}...",
                importance = "HIGH"
            )

            backstory.trim()
        } finally {
            clearLoading(loadingJob)
            if (!debugMode) LoadingAnimation.beep()
        }
    }

    private suspend fun generateBackstory(name: String): String {
        out()
        val loadingJob = showLoading("Generating backstory...")
        val agentId = "backstory_gen_${++agentCounter}"

        return try {
            val systemPrompt = """
                You are a creative writer crafting rich character backstories for a LitRPG System Integration story.
                Write in FIRST PERSON from the character's perspective.
                Create real, layered people with history, relationships, and defining moments.
                These backstories should feel like the opening of a novel, giving us someone worth following.
                """.trimIndent()

            val agentStream = llm.startAgent(systemPrompt)

            val userMessage = """
                Generate a first-person backstory for a character named "$name".

                Include these elements across 2-3 paragraphs:

                IDENTITY & PHYSICALITY:
                - Age, distinctive physical traits, how they carry themselves
                - Athletic background or physical capabilities (sports, fitness, coordination)
                - How their body has shaped their life experiences

                FAMILY & RELATIONSHIPS:
                - Parents, siblings, or close family dynamics
                - One key relationship that shaped who they are
                - Current relationship status or important friendships

                LIFE HISTORY:
                - Where they grew up vs where they are now
                - Their career path and what they're good at
                - ONE formative event that changed them (could be positive or challenging)

                PERSONALITY:
                - What drives them, what they value
                - A skill or talent they're proud of
                - How others perceive them vs how they see themselves

                Requirements:
                - Written entirely in FIRST PERSON
                - Modern day setting (2025)
                - 2-3 substantial paragraphs
                - Tone: confident, engaging, with real depth
                - No mention of System, magic, or fantasy elements
                - NEVER use em-dashes or en-dashes. Use commas or periods instead.
                - Avoid melancholy or "quiet desperation" vibes, but real challenges are fine

                The backstory should make us invested in this person BEFORE anything supernatural happens.

                Example:
                "I'm Elena Vasquez, thirty-two, with dark curly hair I've finally stopped fighting and hands that still have calluses from my climbing days. I was a competitive rock climber through college, even made it to nationals twice, until I blew out my shoulder junior year. That injury taught me more about myself than any summit ever did. These days I work as a physical therapist in Denver, helping other athletes come back from the kind of setbacks I know too well.

                My parents immigrated from Guatemala before I was born, and my dad still runs the auto shop where I learned that anything can be fixed if you're stubborn enough and have the right tools. My younger brother Carlos is finishing his residency in Chicago, and we talk every Sunday without fail. Mom says I got dad's hands and her temper, which is probably accurate.

                I've been dating Angela for two years now, and she keeps saying I need to find hobbies that don't involve adrenaline. She's probably right. But there's something about pushing limits that I can't shake. My clients say I'm tough but fair. My friends say I'm the one they call when things go sideways, because I don't panic and I always show up. I like that reputation. I've earned it."
                """.trimIndent()

            debugWebServer?.logAgentMessage(agentId, "user", userMessage)

            val backstory = agentStream.sendMessage(userMessage).toList().joinToString("")

            debugWebServer?.logAgentMessage(agentId, "assistant", backstory)

            // Log backstory generation
            debugWebServer?.logSetupEvent(
                eventType = "AI_BACKSTORY_GENERATED",
                category = "AI_CALL",
                text = "Generated backstory for $name: ${backstory.trim().take(200)}...",
                importance = "HIGH"
            )

            backstory.trim()
        } finally {
            clearLoading(loadingJob)
            if (!debugMode) LoadingAnimation.beep()
        }
    }

    /**
     * Parse action options from narrator text.
     * Actions are lines starting with "> " (e.g., "> Attack the goblin")
     * Returns a pair of (narrative text without actions, list of action strings)
     */
    private fun parseActionOptions(text: String): Pair<String, List<String>> {
        val lines = text.lines()
        val narrativeLines = mutableListOf<String>()
        val actionLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                // Lines starting with "> " are action options
                trimmed.startsWith("> ") -> {
                    actionLines.add(trimmed.removePrefix("> ").trim())
                }
                // Also handle ">" without space
                trimmed.startsWith(">") && trimmed.length > 1 -> {
                    actionLines.add(trimmed.removePrefix(">").trim())
                }
                // Skip empty lines between actions if we've started collecting actions
                trimmed.isEmpty() && actionLines.isNotEmpty() -> {
                    // Skip
                }
                // Everything else is narrative
                else -> {
                    narrativeLines.add(line)
                }
            }
        }

        // Remove trailing empty lines from narrative
        while (narrativeLines.isNotEmpty() && narrativeLines.last().isBlank()) {
            narrativeLines.removeAt(narrativeLines.size - 1)
        }

        return Pair(narrativeLines.joinToString("\n"), actionLines)
    }
}
