package com.rpgenerator.core.debug

import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.persistence.GameDatabase
import com.rpgenerator.core.persistence.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simplified debug service that generates a basic debug view.
 * This version works with the current GameState structure.
 */
internal class SimpleGameDebugService(
    private val database: GameDatabase,
    private val agentRepository: AgentRepository
) {
    suspend fun generateDebugText(gameState: GameState): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()

        // Header
        sb.append("═".repeat(80)).append("\n")
        sb.append("  GAME DEBUG VIEW\n")
        sb.append("═".repeat(80)).append("\n")
        sb.append("Game ID: ${gameState.gameId}\n")
        sb.append("Player: ${gameState.playerName}\n")
        sb.append("\n")

        // Player Status
        sb.append("═".repeat(80)).append("\n")
        sb.append("  PLAYER STATUS\n")
        sb.append("═".repeat(80)).append("\n")
        val sheet = gameState.characterSheet
        val stats = sheet.effectiveStats()
        sb.append("Level ${sheet.level} (${sheet.currentGrade}-Grade) | ${sheet.playerClass}\n")
        sb.append("XP: ${sheet.xp}/${sheet.xpToNextLevel()}\n")
        sb.append("HP: ${sheet.resources.currentHP}/${sheet.resources.maxHP} | Mana: ${sheet.resources.currentMana}/${sheet.resources.maxMana} | Energy: ${sheet.resources.currentEnergy}/${sheet.resources.maxEnergy}\n")
        sb.append("\n")
        sb.append("Stats: STR ${stats.strength} | DEX ${stats.dexterity} | CON ${stats.constitution} | INT ${stats.intelligence} | WIS ${stats.wisdom} | CHA ${stats.charisma}\n")
        sb.append("Defense: ${stats.defense}\n")
        sb.append("Deaths: ${gameState.deathCount}\n")
        sb.append("\n")

        // Agent Memory
        sb.append("═".repeat(80)).append("\n")
        sb.append("  AGENT SYSTEM\n")
        sb.append("═".repeat(80)).append("\n")

        val memories = agentRepository.getAllAgentMemories(gameState.gameId)
        sb.append("Agent Memories: ${memories.size}\n")
        memories.forEach { memory ->
            sb.append("  - ${memory.agentId}: ${memory.messages.size} messages, ~${memory.estimateTokens()} tokens\n")
        }
        sb.append("\n")

        val allActions = agentRepository.getAllActionsForGame(gameState.gameId, limit = 100)
        sb.append("Total Actions: ${allActions.size}\n")

        val actionsByType = allActions.groupBy { it.actionType.name }.mapValues { it.value.size }
        if (actionsByType.isNotEmpty()) {
            sb.append("Actions by Type:\n")
            actionsByType.entries.sortedByDescending { it.value }.take(5).forEach { (type, count) ->
                sb.append("  - $type: $count\n")
            }
        }
        sb.append("\n")

        // World State
        sb.append("═".repeat(80)).append("\n")
        sb.append("  WORLD STATE\n")
        sb.append("═".repeat(80)).append("\n")
        sb.append("Current Location: ${gameState.currentLocation.name}\n")
        sb.append("Discovered Locations: ${gameState.discoveredTemplateLocations.size}\n")
        sb.append("Custom Locations: ${gameState.customLocations.size}\n")
        val totalNPCs = gameState.npcsByLocation.values.sumOf { it.size }
        sb.append("Total NPCs: $totalNPCs\n")
        sb.append("Active Quests: ${gameState.activeQuests.size}\n")
        sb.append("Completed Quests: ${gameState.completedQuests.size}\n")
        sb.append("\n")

        // Database Stats
        sb.append("═".repeat(80)).append("\n")
        sb.append("  DATABASE STATS\n")
        sb.append("═".repeat(80)).append("\n")

        // Count plot nodes
        val plotNodes = database.gameQueries.selectPlotNodesByGame(gameState.gameId).executeAsList()
        sb.append("Plot Nodes: ${plotNodes.size}\n")
        if (plotNodes.isNotEmpty()) {
            val triggered = plotNodes.count { it.triggered == 1L }
            val completed = plotNodes.count { it.completed == 1L }
            sb.append("  - Triggered: $triggered\n")
            sb.append("  - Completed: $completed\n")
        }
        sb.append("\n")

        // Count plot threads
        val plotThreads = database.gameQueries.selectPlotThreadsByGame(gameState.gameId).executeAsList()
        sb.append("Plot Threads: ${plotThreads.size}\n")
        if (plotThreads.isNotEmpty()) {
            val active = plotThreads.count { it.status == "ACTIVE" }
            sb.append("  - Active: $active\n")
        }
        sb.append("\n")

        // Planning sessions
        val planningSessions = database.gameQueries.selectPlanningSessionsByGame(gameState.gameId).executeAsList()
        sb.append("Planning Sessions: ${planningSessions.size}\n")
        sb.append("\n")

        // Recent events
        val recentEvents = database.gameQueries.selectRecentEvents(gameState.gameId, 10).executeAsList()
        sb.append("Recent Events (Last 10):\n")
        recentEvents.forEach { event ->
            sb.append("  - [${event.category}] ${event.eventType}\n")
        }
        sb.append("\n")

        sb.append("═".repeat(80)).append("\n")

        return@withContext sb.toString()
    }
}
