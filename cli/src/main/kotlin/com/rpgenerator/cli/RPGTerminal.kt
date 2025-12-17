package com.rpgenerator.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.rpgenerator.core.api.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class RPGTerminal(
    private val client: RPGClient,
    private val llm: LLMInterface,
    private val imageGenerator: ImageGenerator = NoOpImageGenerator(),
    private val debugMode: Boolean = false
) {

    private val terminal = Terminal()
    private var currentGame: Game? = null
    private var debugWebServer: DebugWebServer? = null

    // Track current action options for numbered selection
    private var currentActionOptions: List<String> = emptyList()

    fun run() = runBlocking {
        terminal.println((bold + cyan)("=".repeat(60)))
        terminal.println((bold + cyan)("      RPGenerator - LitRPG Adventure Engine"))
        terminal.println((bold + cyan)("=".repeat(60)))
        terminal.println()

        while (true) {
            showMainMenu()
        }
    }

    private suspend fun showMainMenu() {
        terminal.println()
        terminal.println(yellow("Main Menu:"))
        terminal.println("  ${green("1.")} Start New Game")
        terminal.println("  ${green("2.")} Load Saved Game")
        terminal.println("  ${green("3.")} Exit")
        terminal.println()
        terminal.print(brightBlue("> "))

        when (readLine()?.trim()) {
            "1" -> startNewGame()
            "2" -> loadSavedGame()
            "3" -> {
                terminal.println(magenta("Thanks for playing!"))
                kotlin.system.exitProcess(0)
            }
            else -> terminal.println(red("Invalid choice. Please enter 1, 2, or 3."))
        }
    }

    private suspend fun startNewGame() {
        setupLoop@ while (true) {
            terminal.println()
            terminal.println((bold + yellow)("=== Game Setup ==="))
            terminal.println()

            // Step 1: Playstyle / Goal Selection
            var playstyleMode: Int? = null
            while (playstyleMode == null) {
                terminal.println(cyan("How would you like to define your playstyle?"))
                terminal.println("  ${green("1.")} Enter your own goal/playstyle")
                terminal.println("  ${green("2.")} Select from preset playstyles")
                terminal.println("  ${green("3.")} Unknown - Let the game adapt to you")
                terminal.println("  ${yellow("B.")} Back to main menu")
                terminal.print(brightBlue("> "))

                when (val input = readLine()?.trim()?.uppercase()) {
                    "B", "BACK" -> return // Return to main menu
                    "1", "2", "3" -> playstyleMode = input.toInt()
                    else -> terminal.println(red("Invalid choice. Please enter 1, 2, 3, or B to go back."))
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
                        terminal.println()
                        terminal.println(yellow("Describe your goal or playstyle (e.g., 'I want to build a merchant empire' or 'Focus on stealth and assassinations'):"))
                        terminal.println("  ${yellow("B.")} Back")
                        terminal.print(brightBlue("> "))
                        val userGoal = readLine()?.trim()
                        if (userGoal?.uppercase() == "B" || userGoal?.uppercase() == "BACK") {
                            playstyleMode = null // Go back to step 1
                            terminal.println()
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
                        terminal.println()
                        terminal.println(cyan("Select a preset playstyle:"))
                        terminal.println("  ${green("1.")} Power Fantasy - Become overpowered, rapid progression")
                        terminal.println("  ${green("2.")} Story & Roleplay - Rich narrative, character development")
                        terminal.println("  ${green("3.")} Challenge & Strategy - Difficult tactical gameplay")
                        terminal.println("  ${green("4.")} Exploration & Discovery - Uncover secrets and lore")
                        terminal.println("  ${green("5.")} Balanced - Mix of all the above")
                        terminal.println("  ${yellow("B.")} Back")
                        terminal.print(brightBlue("> "))

                        when (val input = readLine()?.trim()?.uppercase()) {
                            "B", "BACK" -> {
                                playstyleMode = null
                                terminal.println()
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
                            else -> terminal.println(red("Invalid choice. Please enter 1-5 or B to go back."))
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

            // Step 3: Character Name
            terminal.println()
            terminal.println((bold + yellow)("=== Character Creation ==="))
            terminal.println()

            var name: String? = null
            while (name == null) {
                terminal.println(cyan("Enter your character name:"))
                terminal.println("  ${yellow("B.")} Back")
                terminal.print(brightBlue("> "))
                val input = readLine()?.trim()
                if (input?.uppercase() == "B" || input?.uppercase() == "BACK") {
                    playstyleMode = null
                    playstyleConfirmed = false
                    terminal.println()
                    continue@setupLoop
                }
                name = input?.takeIf { it.isNotEmpty() } ?: "Adventurer"
            }

            // Step 4: Character Backstory
            var backstory: String? = null
            var backstoryMode: Int? = null

            while (backstoryMode == null) {
                terminal.println()
                terminal.println(cyan("Character Backstory:"))
                terminal.println("  ${green("1.")} Write your own backstory")
                terminal.println("  ${green("2.")} Auto-generate backstory")
                terminal.println("  ${yellow("B.")} Back")
                terminal.print(brightBlue("> "))

                when (val input = readLine()?.trim()?.uppercase()) {
                    "B", "BACK" -> {
                        name = null
                        terminal.println()
                        continue@setupLoop
                    }
                    "1" -> {
                        backstoryMode = 1
                        terminal.println()
                        terminal.println(yellow("Enter your character's backstory (press Enter twice when done, or type 'BACK' to go back):"))
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = readLine() ?: break
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
                    else -> terminal.println(red("Invalid choice. Please enter 1, 2, or B to go back."))
                }
            }

            // Show generated backstory and allow refresh
            var backstoryConfirmed = false
            while (!backstoryConfirmed && backstory != null) {
                terminal.println()
                terminal.println(cyan("=== Backstory ==="))
                terminal.println(backstory!!)
                terminal.println()
                terminal.println("  ${green("1.")} Keep this backstory")
                terminal.println("  ${green("2.")} Generate new backstory")
                terminal.println("  ${green("3.")} Edit backstory")
                terminal.println("  ${yellow("B.")} Back")
                terminal.print(brightBlue("> "))

                when (val input = readLine()?.trim()?.uppercase()) {
                    "1" -> backstoryConfirmed = true
                    "2" -> backstory = generateBackstory(name!!)
                    "3" -> {
                        terminal.println()
                        terminal.println(yellow("Enter details/changes you want (e.g., 'make them a teacher instead' or 'add more about hobbies'):"))
                        terminal.println("  ${yellow("B.")} Back")
                        terminal.print(brightBlue("> "))
                        val userPrompt = readLine()?.trim()
                        if (userPrompt?.uppercase() == "B" || userPrompt?.uppercase() == "BACK") {
                            // Do nothing, loop continues
                        } else if (!userPrompt.isNullOrEmpty()) {
                            backstory = refineBackstory(name!!, backstory!!, userPrompt)
                        }
                    }
                    "B", "BACK" -> {
                        backstoryMode = null
                        terminal.println()
                        continue@setupLoop
                    }
                    else -> terminal.println(red("Invalid choice. Please enter 1, 2, 3, or B to go back."))
                }
            }

            // Generate stats from backstory
            val customStats = if (backstory != null) {
                generateStatsFromBackstory(name!!, backstory)
            } else {
                null
            }

            // Display interpreted stats
            if (customStats != null) {
                terminal.println()
                terminal.println(cyan("=== Interpreted Stats ==="))
                terminal.println("Based on the backstory, your starting stats are:")
                terminal.println("  Strength: ${customStats.strength}")
                terminal.println("  Dexterity: ${customStats.dexterity}")
                terminal.println("  Constitution: ${customStats.constitution}")
                terminal.println("  Intelligence: ${customStats.intelligence}")
                terminal.println("  Wisdom: ${customStats.wisdom}")
                terminal.println("  Charisma: ${customStats.charisma}")
                terminal.println("  ${yellow("Total:")} ${customStats.total()}")
            }

            // Step 5: Difficulty
            var difficulty: Difficulty? = null
            while (difficulty == null) {
                terminal.println()
                terminal.println(cyan("Select difficulty:"))
                terminal.println("  ${green("1.")} Easy")
                terminal.println("  ${green("2.")} Normal")
                terminal.println("  ${green("3.")} Hard")
                terminal.println("  ${green("4.")} Nightmare")
                terminal.println("  ${yellow("B.")} Back")
                terminal.print(brightBlue("> "))

                when (val input = readLine()?.trim()?.uppercase()) {
                    "B", "BACK" -> {
                        backstoryMode = null
                        backstoryConfirmed = false
                        terminal.println()
                        continue@setupLoop
                    }
                    "1" -> difficulty = Difficulty.EASY
                    "2" -> difficulty = Difficulty.NORMAL
                    "3" -> difficulty = Difficulty.HARD
                    "4" -> difficulty = Difficulty.NIGHTMARE
                    else -> terminal.println(red("Invalid choice. Please enter 1-4 or B to go back."))
                }
            }

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

            terminal.println()

            // Show loading animation while creating the game and generating first scene
            val loadingJob = LoadingAnimation.showCreating()

            // Start the game
            currentGame = try {
                val game = client.startGame(config, llm)

                // Generate the first scene before showing ready message
                game.processInput("").collect { }

                game
            } finally {
                loadingJob.cancel()
                print("\r" + " ".repeat(80) + "\r")
            }

            LoadingAnimation.beep()
            terminal.println(green("✓ Ready to begin your adventure!"))
            terminal.println()

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
            terminal.println(red("No saved games found."))
            return
        }

        terminal.println()
        terminal.println(yellow("=== Saved Games ==="))
        games.forEachIndexed { index, game ->
            terminal.println()
            terminal.println("${green("${index + 1}.")} ${bold(game.playerName)} - Level ${game.level}")
            terminal.println("   System: ${game.systemType.name.replace("_", " ")}")
            terminal.println("   Difficulty: ${game.difficulty}")
            terminal.println("   Playtime: ${formatPlaytime(game.playtime)}")
            terminal.println("   Last Played: ${formatTimestamp(game.lastPlayedAt)}")
        }

        terminal.println()
        terminal.print(brightBlue("Select game (1-${games.size}) or 0 to cancel: "))
        val choice = readLine()?.toIntOrNull() ?: 0

        if (choice in 1..games.size) {
            val gameInfo = games[choice - 1]

            // Show loading animation while loading the game
            val loadingJob = LoadingAnimation.showCustom(
                listOf(
                    "⠋ Loading saved game.  ",
                    "⠙ Loading saved game.. ",
                    "⠹ Loading saved game...",
                    "⠸ Loading saved game.. "
                ),
                100
            )

            currentGame = try {
                client.resumeGame(gameInfo, llm)
            } finally {
                loadingJob.cancel()
                print("\r" + " ".repeat(80) + "\r")
            }

            LoadingAnimation.beep()
            terminal.println(green("✓ Game loaded successfully!"))
            terminal.println()

            // Auto-start web debug server if in debug mode
            if (debugMode && currentGame != null) {
                startWebDebugServer(currentGame!!)
            }

            gameLoop()
        }
    }

    private suspend fun gameLoop() {
        val game = currentGame ?: return

        terminal.println()
        terminal.println((bold + cyan)("=".repeat(60)))
        terminal.println((bold + cyan)("      Adventure Begins"))
        terminal.println((bold + cyan)("=".repeat(60)))
        terminal.println()

        // Show initial scene with loading animation
        val loadingJob = LoadingAnimation.showThinking()

        try {
            game.processInput("").collect { event ->
                // Cancel loading on first event
                loadingJob.cancel()
                print("\r" + " ".repeat(80) + "\r")

                // Play beep on first response
                LoadingAnimation.beep()

                handleGameEvent(event)
            }
        } catch (e: Exception) {
            loadingJob.cancel()
            print("\r" + " ".repeat(80) + "\r")
            terminal.println(red("Error loading initial scene: ${e.message}"))
        }

        while (true) {
            terminal.print(brightBlue("\n> "))
            var input = readLine()?.trim() ?: continue

            if (input.isEmpty()) continue

            // Check if input is a numbered action selection (1-9)
            val actionNumber = input.toIntOrNull()
            if (actionNumber != null && actionNumber in 1..currentActionOptions.size) {
                input = currentActionOptions[actionNumber - 1]
                terminal.println(dim("→ $input"))
            }

            // Handle meta commands
            when (input.lowercase()) {
                "quit", "exit" -> {
                    terminal.println(magenta("Saving game..."))
                    game.save()
                    terminal.println(green("✓ Game saved!"))
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
                        terminal.println(cyan("Debug web dashboard running at: ${bold("http://localhost:8080")}"))
                    } else {
                        showDebugView(game)
                    }
                    continue
                }
            }

            // Process game input
            terminal.println()

            // Show loading animation while AI is thinking
            val loadingJob = LoadingAnimation.showThinking()

            try {
                game.processInput(input).collect { event ->
                    // Cancel loading on first event
                    loadingJob.cancel()
                    print("\r" + " ".repeat(80) + "\r")

                    // Play beep on first response
                    LoadingAnimation.beep()

                    handleGameEvent(event)
                }
            } catch (e: Exception) {
                loadingJob.cancel()
                print("\r" + " ".repeat(80) + "\r")
                terminal.println(red("Error: ${e.message}"))
            }
        }
    }

    private suspend fun handleGameEvent(event: GameEvent) {
        when (event) {
            is GameEvent.NarratorText -> {
                // Parse action options from the text (lines starting with ">")
                val (narrativeText, actionOptions) = parseActionOptions(event.text)

                // Display the narrative
                terminal.println(narrativeText)

                // Display action options with numbers
                if (actionOptions.isNotEmpty()) {
                    currentActionOptions = actionOptions
                    terminal.println()
                    terminal.println(yellow("Actions:"))
                    actionOptions.forEachIndexed { index, action ->
                        terminal.println(green("  ${index + 1}. ") + action)
                    }
                    terminal.println(dim("  (or type your own action)"))
                }

                // Generate image for major scene descriptions
                if (imageGenerator.isAvailable() && shouldGenerateImage(narrativeText)) {
                    terminal.println(brightBlue("🎨 Generating scene image..."))
                    generateSceneImage(narrativeText)
                }
            }
            is GameEvent.NPCDialogue -> {
                terminal.println()
                terminal.println(cyan("${bold(event.npcName)}: ") + event.text)
            }
            is GameEvent.CombatLog -> {
                terminal.println(yellow("⚔ ") + event.text)
            }
            is GameEvent.StatChange -> {
                val symbol = if (event.newValue > event.oldValue) "↑" else "↓"
                val color = if (event.newValue > event.oldValue) green else red
                terminal.println(color("  $symbol ${event.statName}: ${event.oldValue} → ${event.newValue}"))
            }
            is GameEvent.ItemGained -> {
                terminal.println(green("  + ${event.itemName} x${event.quantity}"))
            }
            is GameEvent.QuestUpdate -> {
                when (event.status) {
                    QuestStatus.NEW -> {
                        terminal.println()
                        terminal.println(magenta("📜 New Quest: ") + bold(event.questName))
                    }
                    QuestStatus.COMPLETED -> {
                        terminal.println()
                        terminal.println(green("✓ Quest Completed: ") + bold(event.questName))
                    }
                    QuestStatus.FAILED -> {
                        terminal.println()
                        terminal.println(red("✗ Quest Failed: ") + bold(event.questName))
                    }
                    QuestStatus.IN_PROGRESS -> {
                        terminal.println(yellow("  Quest updated: ") + event.questName)
                    }
                }
            }
            is GameEvent.SystemNotification -> {
                terminal.println(brightBlue("ℹ ") + event.text)
            }
        }
    }

    private fun showHelp() {
        terminal.println()
        terminal.println(yellow("=== Commands ==="))
        terminal.println("  ${green("stats/status")} - View character stats")
        if (debugMode) {
            terminal.println("  ${green("debug/dev")} - Show web debug dashboard URL")
        } else {
            terminal.println("  ${green("debug/dev")} - View debug information (agents, plot, world state)")
        }
        terminal.println("  ${green("quit/exit")} - Save and return to main menu")
        terminal.println("  ${green("help/?")} - Show this help")
        terminal.println()
        terminal.println(yellow("=== Gameplay ==="))
        terminal.println("  Just type naturally! Examples:")
        terminal.println("    - I attack the goblin")
        terminal.println("    - Talk to the merchant")
        terminal.println("    - Look around")
        terminal.println("    - Go north")
    }

    private suspend fun showStatus(game: Game) {
        val state = game.getState()
        terminal.println()
        terminal.println(cyan("=== Character Status ==="))
        terminal.println("${bold("Name:")} ${state.playerStats.name}")
        terminal.println("${bold("Level:")} ${state.playerStats.level}")
        terminal.println("${bold("XP:")} ${state.playerStats.experience} / ${state.playerStats.experienceToNextLevel}")
        terminal.println("${bold("HP:")} ${state.playerStats.health} / ${state.playerStats.maxHealth}")
        terminal.println()
        terminal.println(cyan("=== Stats ==="))
        state.playerStats.stats.forEach { (name, value) ->
            terminal.println("  ${name.replaceFirstChar { it.uppercase() }}: $value")
        }
        terminal.println()
        terminal.println(cyan("=== Location ==="))
        terminal.println("  ${state.location}")
    }

    private suspend fun showDebugView(game: Game) {
        terminal.println()
        terminal.println(yellow("Generating debug view..."))
        terminal.println()

        try {
            val debugText = client.getDebugView(game)
            terminal.println(debugText)
        } catch (e: Exception) {
            terminal.println(red("Error generating debug view: ${e.message}"))
            e.printStackTrace()
        }

        terminal.println()
        terminal.println(brightBlue("Press Enter to continue..."))
        readLine()
    }

    private fun startWebDebugServer(game: Game) {
        if (debugWebServer == null) {
            terminal.println()
            terminal.println(yellow("Starting web debug dashboard..."))

            debugWebServer = DebugWebServer(client, port = 8080)
            debugWebServer?.start(game)

            // Give the server a moment to start
            Thread.sleep(500)

            terminal.println(green("✓ Web debug dashboard started!"))
            terminal.println(cyan("   Open your browser to: ${bold("http://localhost:8080")}"))
            terminal.println(gray("   (Auto-refreshes every 5 seconds)"))
            terminal.println()
        } else {
            // Update the game reference
            debugWebServer?.updateGame(game)
            terminal.println()
            terminal.println(cyan("Web debug dashboard is already running at: ${bold("http://localhost:8080")}"))
            terminal.println()
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
        terminal.println()
        val loadingJob = LoadingAnimation.showCustom(
            listOf(
                "⠋ Analyzing character.  ",
                "⠙ Analyzing character.. ",
                "⠹ Analyzing character...",
                "⠸ Analyzing character.. "
            ),
            100
        )

        return try {
            val agentStream = llm.startAgent(
                """
                You are a game designer analyzing character backstories to determine appropriate RPG stats.
                Interpret the character's background to assign D&D-style ability scores (3-18 range).
                Average person has 10 in each stat. Total should be around 60-70 points.
                Be realistic - not everyone is exceptional.
                """.trimIndent()
            )

            val response = agentStream.sendMessage(
                """
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
            ).toList().joinToString("")

            // Parse the response
            val numbers = response.trim().split(",").mapNotNull { it.trim().toIntOrNull() }
            if (numbers.size == 6 && numbers.all { it in 3..18 }) {
                CustomStats(
                    strength = numbers[0],
                    dexterity = numbers[1],
                    constitution = numbers[2],
                    intelligence = numbers[3],
                    wisdom = numbers[4],
                    charisma = numbers[5]
                )
            } else {
                null
            }
        } finally {
            loadingJob.cancel()
            print("\r" + " ".repeat(80) + "\r")
            LoadingAnimation.beep()
        }
    }

    private suspend fun expandPlaystyleGoal(userGoal: String): String {
        terminal.println()
        val loadingJob = LoadingAnimation.showCustom(
            listOf(
                "⠋ Processing goal.  ",
                "⠙ Processing goal.. ",
                "⠹ Processing goal...",
                "⠸ Processing goal.. "
            ),
            100
        )

        return try {
            val agentStream = llm.startAgent(
                """
                You are a game design assistant helping convert player goals into actionable DM instructions.
                Take a player's stated goal or playstyle and expand it into clear guidelines for the game master.
                """.trimIndent()
            )

            val expanded = agentStream.sendMessage(
                """
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
            ).toList().joinToString("")

            expanded.trim()
        } finally {
            loadingJob.cancel()
            print("\r" + " ".repeat(80) + "\r")
            LoadingAnimation.beep()
        }
    }

    private suspend fun refineBackstory(name: String, currentBackstory: String, userPrompt: String): String {
        terminal.println()
        val loadingJob = LoadingAnimation.showCustom(
            listOf(
                "⠋ Refining backstory.  ",
                "⠙ Refining backstory.. ",
                "⠹ Refining backstory...",
                "⠸ Refining backstory.. "
            ),
            100
        )

        return try {
            val agentStream = llm.startAgent(
                """
                You are a creative writer helping refine character backstories for a LitRPG System Integration story.
                Take the user's feedback and recreate the backstory incorporating their requested changes.
                Maintain the same format and level of detail as the original.
                """.trimIndent()
            )

            val backstory = agentStream.sendMessage(
                """
                Current backstory for $name:
                $currentBackstory

                User requested changes:
                $userPrompt

                Recreate the backstory incorporating these changes while maintaining:
                - Physical description in first sentence (age, build, notable features)
                - Modern day setting (2025)
                - 3-5 sentences total
                - Realistic, grounded tone
                - No System, magic, or fantasy elements

                Output the complete revised backstory.
                """.trimIndent()
            ).toList().joinToString("")

            backstory.trim()
        } finally {
            loadingJob.cancel()
            print("\r" + " ".repeat(80) + "\r")
            LoadingAnimation.beep()
        }
    }

    private suspend fun generateBackstory(name: String): String {
        terminal.println()
        val loadingJob = LoadingAnimation.showCustom(
            listOf(
                "⠋ Generating backstory.  ",
                "⠙ Generating backstory.. ",
                "⠹ Generating backstory...",
                "⠸ Generating backstory.. "
            ),
            100
        )

        return try {
            val agentStream = llm.startAgent(
                """
                You are a creative writer crafting character backstories for a LitRPG System Integration story.
                Write in FIRST PERSON from the character's perspective - personal, emotional, and grounded.
                These are ordinary people with real personalities, small habits, and relatable feelings.
                Capture their inner voice - their hopes, insecurities, and the things that make them human.
                """.trimIndent()
            )

            val backstory = agentStream.sendMessage(
                """
                Generate a first-person backstory for a character named "$name".

                Requirements:
                - Written entirely in FIRST PERSON ("I am...", "I feel...", "I've always...")
                - Start with a brief self-description (age, appearance, how they see themselves)
                - Modern day setting (2025)
                - Ordinary person with a normal job and life
                - Include ONE personal detail that reveals character (a hobby, habit, or small dream)
                - Include ONE emotional anchor (a relationship, memory, or feeling that matters to them)
                - Total of 4-5 sentences
                - Tone: genuine and personal, not overly quirky or comedic
                - No mention of System, magic, or fantasy elements
                - NEVER use em-dashes or en-dashes. Use commas or periods instead.

                Example: "I'm Sarah Chen, twenty-eight, with shoulder-length black hair and glasses I'm always pushing up my nose. I work as a librarian in downtown Seattle, which suits me fine because I've always been more comfortable around books than people. On weekends I hike the Cascades with my dog, Mochi. My mom calls every Sunday to ask when I'm going to settle down, and I never know what to tell her. I've been practicing calligraphy in the evenings, and I'm getting pretty good at it."
                """.trimIndent()
            ).toList().joinToString("")

            backstory.trim()
        } finally {
            loadingJob.cancel()
            print("\r" + " ".repeat(80) + "\r")
            LoadingAnimation.beep()
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

    /**
     * Determine if we should generate an image for this text.
     * Only generate for significant scene descriptions, not minor updates.
     */
    private fun shouldGenerateImage(text: String): Boolean {
        val textLength = text.length
        val lowerText = text.lowercase()

        // Only generate for substantial narration (50+ chars)
        if (textLength < 50) return false

        // Skip if it's just combat/loot text
        if (lowerText.contains("you deal") ||
            lowerText.contains("you receive") ||
            lowerText.contains("level up")) {
            return false
        }

        // Generate for scene descriptions
        return true
    }

    /**
     * Generate and display a scene image.
     */
    private suspend fun generateSceneImage(narratorText: String) {
        val dataDir = java.io.File(System.getProperty("user.home"), ".rpgenerator/images")
        dataDir.mkdirs()

        val timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        )
        val outputFile = java.io.File(dataDir, "scene_$timestamp.png")

        // Create a concise prompt from the narrator text
        val prompt = createImagePrompt(narratorText)

        val success = imageGenerator.generateImage(prompt, outputFile)

        if (success && outputFile.exists()) {
            terminal.println(green("✓ Image saved: ${outputFile.absolutePath}"))

            // Try to open the image automatically (macOS)
            try {
                ProcessBuilder("open", outputFile.absolutePath).start()
            } catch (e: Exception) {
                terminal.println(yellow("  View image: ${outputFile.absolutePath}"))
            }
        } else {
            terminal.println(red("✗ Failed to generate image"))
        }
    }

    /**
     * Convert narrator text into a good Stable Diffusion prompt.
     */
    private fun createImagePrompt(narratorText: String): String {
        // Extract key visual elements from the narrative
        // Keep it concise (SD 1.5 works best with 50-75 word prompts)

        // Take first 200 chars and clean it up
        var prompt = narratorText.take(200)
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Remove second-person pronouns to make it more like an art description
        prompt = prompt
            .replace(Regex("\\byou\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\byour\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return prompt
    }
}
