package com.rpgenerator.core

import com.rpgenerator.core.api.*
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.orchestration.GameOrchestrator
import com.rpgenerator.core.persistence.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Implementation of the Game interface.
 * Manages an active game session.
 */
internal class GameImpl(
    private val gameId: String,
    private val llm: LLMInterface,
    private val repository: GameRepository,
    initialState: GameState
) : Game(gameId, llm) {

    private val orchestrator = GameOrchestrator(llm, initialState)
    private var sessionStartTime = currentTimeMillis()
    private var sessionPlaytime = 0L

    override suspend fun processInput(input: String): Flow<GameEvent> {
        return orchestrator.processInput(input).onEach { event ->
            // Log each event to the database
            repository.logEvent(gameId, event)
        }
    }

    override fun getState(): GameStateSnapshot {
        val state = orchestrator.getState()
        val recentEvents = emptyList<GameEvent>() // We'll load these on demand if needed

        return GameStateSnapshot(
            playerStats = PlayerStats(
                name = "Player", // TODO: Get from config/state
                level = state.playerLevel,
                experience = state.playerXP,
                experienceToNextLevel = state.characterSheet.xpToNextLevel(),
                stats = mapOf(
                    "strength" to state.characterSheet.effectiveStats().strength,
                    "dexterity" to state.characterSheet.effectiveStats().dexterity,
                    "constitution" to state.characterSheet.effectiveStats().constitution,
                    "intelligence" to state.characterSheet.effectiveStats().intelligence,
                    "wisdom" to state.characterSheet.effectiveStats().wisdom,
                    "charisma" to state.characterSheet.effectiveStats().charisma,
                    "defense" to state.characterSheet.effectiveStats().defense
                ),
                health = state.characterSheet.resources.currentHP,
                maxHealth = state.characterSheet.resources.maxHP,
                energy = state.characterSheet.resources.currentEnergy,
                maxEnergy = state.characterSheet.resources.maxEnergy
            ),
            location = state.currentLocation.name,
            currentScene = state.currentLocation.description,
            inventory = state.characterSheet.inventory.items.values.map { item ->
                Item(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    quantity = item.quantity,
                    rarity = ItemRarity.COMMON // TODO: Add rarity to InventoryItem
                )
            },
            activeQuests = emptyList(), // TODO: Implement quest system
            recentEvents = recentEvents
        )
    }

    override suspend fun save() {
        // Calculate total playtime for this session
        val currentTime = currentTimeMillis()
        val sessionDuration = (currentTime - sessionStartTime) / 1000
        sessionPlaytime += sessionDuration
        sessionStartTime = currentTime

        // Save to database
        repository.saveGame(
            gameId = gameId,
            state = orchestrator.getState(),
            playtime = sessionPlaytime
        )
    }

    /**
     * Update the session playtime tracker.
     * Should be called periodically during gameplay.
     */
    internal fun updatePlaytime() {
        val currentTime = currentTimeMillis()
        val sessionDuration = (currentTime - sessionStartTime) / 1000
        sessionPlaytime += sessionDuration
        sessionStartTime = currentTime
    }

    /**
     * Set the initial playtime (used when resuming a game).
     */
    internal fun setInitialPlaytime(playtime: Long) {
        sessionPlaytime = playtime
    }

    /**
     * Get the current game state for debugging.
     * Internal use only.
     */
    internal fun getCurrentState(): GameState {
        return orchestrator.getState()
    }
}
