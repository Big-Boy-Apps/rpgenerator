package org.bigboyapps.rngenerator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.db.SqlDriver
import com.rpgenerator.core.api.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.bigboyapps.rngenerator.llm.SimpleLLM
import org.bigboyapps.rngenerator.logging.AgentLogger
import org.bigboyapps.rngenerator.logging.LoggingLLM

data class TerminalLine(
    val text: String,
    val type: LineType
) {
    enum class LineType {
        NARRATIVE,
        NPC_DIALOGUE,
        COMBAT,
        SYSTEM,
        USER_INPUT,
        ERROR,
        ACTION_OPTION
    }
}

data class GameUiState(
    val isLoading: Boolean = false,
    val isGameActive: Boolean = false,
    val terminalLines: List<TerminalLine> = emptyList(),
    val currentInput: String = "",
    val actionOptions: List<String> = emptyList(),
    val playerName: String = "",
    val playerLevel: Int = 1
)

class GameViewModel(
    private val driverFactory: () -> SqlDriver
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    val agentLogs = AgentLogger.logs

    private var client: RPGClient? = null
    private var currentGame: Game? = null
    private val llm: LLMInterface = LoggingLLM(SimpleLLM())

    init {
        addSystemLine("Welcome to RPGenerator!")
        addSystemLine("Type 'start' to begin a new game or 'help' for commands.")
    }

    fun onInputChanged(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun onSubmitInput() {
        val input = _uiState.value.currentInput.trim()
        if (input.isEmpty()) return

        _uiState.update { it.copy(currentInput = "") }

        viewModelScope.launch {
            processInput(input)
        }
    }

    fun onActionSelected(index: Int) {
        val actions = _uiState.value.actionOptions
        if (index in actions.indices) {
            val action = actions[index]
            addUserLine(action)
            viewModelScope.launch {
                processGameInput(action)
            }
        }
    }

    private suspend fun processInput(input: String) {
        addUserLine(input)

        when {
            !_uiState.value.isGameActive -> {
                processMenuInput(input)
            }
            else -> {
                processGameInput(input)
            }
        }
    }

    private suspend fun processMenuInput(input: String) {
        when (input.lowercase()) {
            "start", "new", "1" -> startNewGame()
            "help", "?" -> showHelp()
            "clear" -> clearTerminal()
            else -> addSystemLine("Unknown command. Type 'help' for available commands.")
        }
    }

    private suspend fun processGameInput(input: String) {
        when (input.lowercase()) {
            "quit", "exit" -> {
                currentGame?.save()
                _uiState.update {
                    it.copy(
                        isGameActive = false,
                        actionOptions = emptyList()
                    )
                }
                addSystemLine("Game saved. Returning to menu...")
                addSystemLine("Type 'start' to begin a new game.")
            }
            "help", "?" -> showGameHelp()
            "status", "stats" -> showStatus()
            "logs", "debug" -> addSystemLine("Agent logs are visible in the debug panel.")
            else -> {
                // Process game action
                val game = currentGame ?: return

                _uiState.update { it.copy(isLoading = true, actionOptions = emptyList()) }

                try {
                    game.processInput(input).collect { event ->
                        handleGameEvent(event)
                    }
                } catch (e: Exception) {
                    addErrorLine("Error: ${e.message}")
                } finally {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private suspend fun startNewGame() {
        addSystemLine("Starting new game...")
        _uiState.update { it.copy(isLoading = true) }

        try {
            // Initialize client if needed
            if (client == null) {
                val driver = driverFactory()
                client = RPGClient(driver)
            }

            // Create game config
            val config = GameConfig(
                systemType = SystemType.SYSTEM_INTEGRATION,
                difficulty = Difficulty.NORMAL,
                characterCreation = CharacterCreationOptions(
                    name = "Adventurer",
                    backstory = null,
                    statAllocation = StatAllocation.BALANCED
                ),
                playerPreferences = mapOf(
                    "playstyle" to "balanced",
                    "playstyle_description" to "A balanced adventure experience."
                )
            )

            // Start the game
            val game = client!!.startGame(config, llm)
            currentGame = game

            _uiState.update {
                it.copy(
                    isGameActive = true,
                    playerName = "Adventurer",
                    playerLevel = 1
                )
            }

            addSystemLine("Game started! Your adventure begins...")
            addSystemLine("")

            // Get initial scene
            game.processInput("").collect { event ->
                handleGameEvent(event)
            }

        } catch (e: Exception) {
            addErrorLine("Failed to start game: ${e.message}")
            e.printStackTrace()
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun handleGameEvent(event: GameEvent) {
        when (event) {
            is GameEvent.NarratorText -> {
                val (narrative, actions) = parseActionOptions(event.text)
                addNarrativeLine(narrative)

                if (actions.isNotEmpty()) {
                    _uiState.update { it.copy(actionOptions = actions) }
                    actions.forEachIndexed { index, action ->
                        addActionLine("${index + 1}. $action")
                    }
                }
            }
            is GameEvent.NPCDialogue -> {
                addNpcLine("${event.npcName}: ${event.text}")
            }
            is GameEvent.CombatLog -> {
                addCombatLine("⚔ ${event.text}")
            }
            is GameEvent.StatChange -> {
                val symbol = if (event.newValue > event.oldValue) "↑" else "↓"
                addSystemLine("$symbol ${event.statName}: ${event.oldValue} → ${event.newValue}")
            }
            is GameEvent.ItemGained -> {
                addSystemLine("+ ${event.itemName} x${event.quantity}")
            }
            is GameEvent.QuestUpdate -> {
                when (event.status) {
                    QuestStatus.NEW -> addSystemLine("📜 New Quest: ${event.questName}")
                    QuestStatus.COMPLETED -> addSystemLine("✓ Quest Completed: ${event.questName}")
                    QuestStatus.FAILED -> addSystemLine("✗ Quest Failed: ${event.questName}")
                    QuestStatus.IN_PROGRESS -> addSystemLine("Quest updated: ${event.questName}")
                }
            }
            is GameEvent.SystemNotification -> {
                addSystemLine("ℹ ${event.text}")
            }
        }
    }

    private fun parseActionOptions(text: String): Pair<String, List<String>> {
        val lines = text.lines()
        val narrativeLines = mutableListOf<String>()
        val actionLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("> ") -> {
                    actionLines.add(trimmed.removePrefix("> ").trim())
                }
                trimmed.startsWith(">") && trimmed.length > 1 -> {
                    actionLines.add(trimmed.removePrefix(">").trim())
                }
                trimmed.isEmpty() && actionLines.isNotEmpty() -> {
                    // Skip empty lines after actions start
                }
                else -> {
                    narrativeLines.add(line)
                }
            }
        }

        while (narrativeLines.isNotEmpty() && narrativeLines.last().isBlank()) {
            narrativeLines.removeAt(narrativeLines.size - 1)
        }

        return Pair(narrativeLines.joinToString("\n"), actionLines)
    }

    private fun showHelp() {
        addSystemLine("=== Commands ===")
        addSystemLine("  start - Start a new game")
        addSystemLine("  help  - Show this help")
        addSystemLine("  clear - Clear the terminal")
    }

    private fun showGameHelp() {
        addSystemLine("=== Game Commands ===")
        addSystemLine("  status/stats - View character status")
        addSystemLine("  logs/debug   - Info about agent logs")
        addSystemLine("  quit/exit    - Save and exit to menu")
        addSystemLine("")
        addSystemLine("Or just type actions naturally!")
        addSystemLine("  Examples: 'attack', 'look around', 'talk to merchant'")
    }

    private suspend fun showStatus() {
        val game = currentGame ?: return
        try {
            val state = game.getState()
            addSystemLine("=== Character Status ===")
            addSystemLine("Name: ${state.playerStats.name}")
            addSystemLine("Level: ${state.playerStats.level}")
            addSystemLine("HP: ${state.playerStats.health}/${state.playerStats.maxHealth}")
            addSystemLine("XP: ${state.playerStats.experience}/${state.playerStats.experienceToNextLevel}")
            addSystemLine("")
            addSystemLine("Location: ${state.location}")
        } catch (e: Exception) {
            addErrorLine("Error getting status: ${e.message}")
        }
    }

    private fun clearTerminal() {
        _uiState.update { it.copy(terminalLines = emptyList()) }
    }

    private fun addLine(text: String, type: TerminalLine.LineType) {
        _uiState.update { state ->
            state.copy(
                terminalLines = state.terminalLines + TerminalLine(text, type)
            )
        }
    }

    private fun addNarrativeLine(text: String) = addLine(text, TerminalLine.LineType.NARRATIVE)
    private fun addNpcLine(text: String) = addLine(text, TerminalLine.LineType.NPC_DIALOGUE)
    private fun addCombatLine(text: String) = addLine(text, TerminalLine.LineType.COMBAT)
    private fun addSystemLine(text: String) = addLine(text, TerminalLine.LineType.SYSTEM)
    private fun addUserLine(text: String) = addLine("> $text", TerminalLine.LineType.USER_INPUT)
    private fun addErrorLine(text: String) = addLine(text, TerminalLine.LineType.ERROR)
    private fun addActionLine(text: String) = addLine(text, TerminalLine.LineType.ACTION_OPTION)

    override fun onCleared() {
        super.onCleared()
        client?.close()
    }
}
