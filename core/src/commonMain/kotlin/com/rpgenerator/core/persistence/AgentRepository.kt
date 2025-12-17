package com.rpgenerator.core.persistence

import com.rpgenerator.core.domain.AgentMemory
import com.rpgenerator.core.domain.AgentAction
import com.rpgenerator.core.domain.AgentActionType
import com.rpgenerator.core.domain.AgentActionContext
import com.rpgenerator.core.domain.ConsolidationSnapshot
import com.rpgenerator.core.domain.ConsolidationType
import com.rpgenerator.core.domain.ConsolidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Repository for persisting and loading agent memory and actions.
 *
 * This repository handles:
 * - Agent conversation history (memory)
 * - Agent action logging (decisions with reasoning)
 * - Memory consolidation (summarization)
 *
 * Usage Pattern:
 * 1. Agent loads memory on initialization: loadAgentMemory()
 * 2. Agent performs actions and logs them: saveAgentAction()
 * 3. Agent updates memory after interactions: saveAgentMemory()
 * 4. Agent consolidates when needed: consolidateAgentMemory()
 */
internal class AgentRepository(
    private val database: GameDatabase
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // ========================================
    // AGENT MEMORY OPERATIONS
    // ========================================

    /**
     * Save agent conversation memory to database.
     * This persists the full message history and metadata.
     *
     * @param memory The agent memory to save
     */
    suspend fun saveAgentMemory(memory: AgentMemory) = withContext(Dispatchers.Default) {
        val memoryJson = json.encodeToString(memory)
        val timestamp = currentTimeMillis() / 1000

        database.gameQueries.insertAgentMemory(
            agentId = memory.agentId,
            gameId = memory.gameId,
            memoryJson = memoryJson,
            messageCount = memory.messages.size.toLong(),
            tokenEstimate = memory.estimateTokens().toLong(),
            lastConsolidated = memory.lastConsolidated,
            lastUpdated = timestamp
        )
    }

    /**
     * Load agent memory from database.
     * Returns null if no saved memory exists for this agent/game combination.
     *
     * @param agentId The agent identifier (e.g., "game_master", "planner")
     * @param gameId The game session ID
     * @return AgentMemory if found, null otherwise
     */
    suspend fun loadAgentMemory(agentId: String, gameId: String): AgentMemory? =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectAgentMemory(agentId, gameId)
                .executeAsOneOrNull()
                ?.let { row ->
                    try {
                        json.decodeFromString<AgentMemory>(row.memoryJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Get all agent memories for a game.
     * Useful for debugging or analyzing agent state.
     *
     * @param gameId The game session ID
     * @return List of all agent memories in this game
     */
    suspend fun getAllAgentMemories(gameId: String): List<AgentMemory> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectAllAgentMemoriesForGame(gameId)
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        json.decodeFromString<AgentMemory>(row.memoryJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Delete agent memory.
     *
     * @param agentId The agent identifier
     * @param gameId The game session ID
     */
    suspend fun deleteAgentMemory(agentId: String, gameId: String) = withContext(Dispatchers.Default) {
        database.gameQueries.deleteAgentMemory(agentId, gameId)
    }

    // ========================================
    // AGENT ACTION LOGGING
    // ========================================

    /**
     * Log an agent action with reasoning.
     * This creates a permanent record of important decisions.
     *
     * @param action The action to log
     */
    suspend fun saveAgentAction(action: AgentAction) = withContext(Dispatchers.Default) {
        val timestamp = currentTimeMillis() / 1000

        database.gameQueries.insertAgentAction(
            agentId = action.agentId,
            gameId = action.gameId,
            actionType = action.actionType.name,
            actionJson = action.actionData,
            reasoning = action.reasoning,
            playerLevel = action.context.playerLevel.toLong(),
            timestamp = timestamp,
            npcId = action.context.npcId,
            questId = action.context.questId,
            plotThreadId = action.context.plotThreadId,
            locationId = action.context.locationId
        )
    }

    /**
     * Get recent actions for a specific agent.
     *
     * @param agentId The agent identifier
     * @param gameId The game session ID
     * @param limit Maximum number of actions to return
     * @return List of recent actions
     */
    suspend fun getAgentActions(
        agentId: String,
        gameId: String,
        limit: Int = 20
    ): List<AgentAction> = withContext(Dispatchers.Default) {
        database.gameQueries.selectRecentAgentActions(agentId, gameId, limit.toLong())
            .executeAsList()
            .mapNotNull { row ->
                try {
                    AgentAction(
                        id = row.id,
                        agentId = row.agentId,
                        gameId = row.gameId,
                        actionType = AgentActionType.valueOf(row.actionType),
                        actionData = row.actionJson,
                        reasoning = row.reasoning,
                        context = AgentActionContext(
                            playerLevel = row.playerLevel.toInt(),
                            npcId = row.npcId,
                            questId = row.questId,
                            plotThreadId = row.plotThreadId,
                            locationId = row.locationId
                        ),
                        timestamp = row.timestamp
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get actions by type for analysis.
     *
     * @param agentId The agent identifier
     * @param gameId The game session ID
     * @param actionType The type of action to filter by
     * @param limit Maximum number of actions to return
     * @return List of matching actions
     */
    suspend fun getAgentActionsByType(
        agentId: String,
        gameId: String,
        actionType: AgentActionType,
        limit: Int = 20
    ): List<AgentAction> = withContext(Dispatchers.Default) {
        database.gameQueries.selectAgentActionsByType(
            agentId,
            gameId,
            actionType.name,
            limit.toLong()
        )
            .executeAsList()
            .mapNotNull { row ->
                try {
                    AgentAction(
                        id = row.id,
                        agentId = row.agentId,
                        gameId = row.gameId,
                        actionType = AgentActionType.valueOf(row.actionType),
                        actionData = row.actionJson,
                        reasoning = row.reasoning,
                        context = AgentActionContext(
                            playerLevel = row.playerLevel.toInt(),
                            npcId = row.npcId,
                            questId = row.questId,
                            plotThreadId = row.plotThreadId,
                            locationId = row.locationId
                        ),
                        timestamp = row.timestamp
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get all actions for a game (across all agents).
     * Useful for debugging or creating a complete action timeline.
     *
     * @param gameId The game session ID
     * @param limit Maximum number of actions to return
     * @return List of all actions in chronological order
     */
    suspend fun getAllActionsForGame(gameId: String, limit: Int = 100): List<AgentAction> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectAllActionsForGame(gameId, limit.toLong())
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        AgentAction(
                            id = row.id,
                            agentId = row.agentId,
                            gameId = row.gameId,
                            actionType = AgentActionType.valueOf(row.actionType),
                            actionData = row.actionJson,
                            reasoning = row.reasoning,
                            context = AgentActionContext(
                                playerLevel = row.playerLevel.toInt(),
                                npcId = row.npcId,
                                questId = row.questId,
                                plotThreadId = row.plotThreadId,
                                locationId = row.locationId
                            ),
                            timestamp = row.timestamp
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    // ========================================
    // MEMORY CONSOLIDATION
    // ========================================

    /**
     * Consolidate agent memory by summarizing old messages.
     *
     * Process:
     * 1. Keep recent N messages
     * 2. Summarize older messages into consolidated context
     * 3. Save consolidation snapshot for reference
     * 4. Update agent memory
     *
     * @param agentId The agent identifier
     * @param gameId The game session ID
     * @param summary Summarized context from old messages (created by LLM)
     * @param keepRecentCount How many recent messages to preserve
     * @param playerLevel Current player level
     * @param levelRangeStart Level range start for this consolidation
     * @param levelRangeEnd Level range end for this consolidation
     * @return ConsolidationResult with before/after stats
     */
    suspend fun consolidateAgentMemory(
        agentId: String,
        gameId: String,
        summary: String,
        keepRecentCount: Int = 20,
        playerLevel: Int,
        levelRangeStart: Int,
        levelRangeEnd: Int
    ): ConsolidationResult? = withContext(Dispatchers.Default) {
        // Load current memory
        val currentMemory = loadAgentMemory(agentId, gameId) ?: return@withContext null

        val tokensBefore = currentMemory.estimateTokens()
        val messagesBefore = currentMemory.messages.size

        // Create consolidated memory
        val consolidatedMemory = currentMemory.consolidate(keepRecentCount, summary)

        // Save consolidation snapshot
        val snapshot = ConsolidationSnapshot(
            agentId = agentId,
            gameId = gameId,
            consolidationType = ConsolidationType.PARTIAL,
            summary = summary,
            messagesConsolidated = messagesBefore - keepRecentCount,
            playerLevelStart = levelRangeStart,
            playerLevelEnd = levelRangeEnd,
            timestamp = currentTimeMillis()
        )

        saveConsolidationSnapshot(snapshot)

        // Save updated memory
        saveAgentMemory(consolidatedMemory)

        val tokensAfter = consolidatedMemory.estimateTokens()
        val messagesAfter = consolidatedMemory.messages.size

        ConsolidationResult(
            updatedMemory = consolidatedMemory,
            snapshot = snapshot,
            tokensBefore = tokensBefore,
            tokensAfter = tokensAfter,
            messagesBefore = messagesBefore,
            messagesAfter = messagesAfter
        )
    }

    /**
     * Save a consolidation snapshot.
     */
    private suspend fun saveConsolidationSnapshot(snapshot: ConsolidationSnapshot) =
        withContext(Dispatchers.Default) {
            val summaryJson = json.encodeToString(snapshot.summary)
            val timestamp = currentTimeMillis() / 1000

            database.gameQueries.insertAgentConsolidation(
                agentId = snapshot.agentId,
                gameId = snapshot.gameId,
                consolidationType = snapshot.consolidationType.name,
                summaryJson = summaryJson,
                messagesConsolidated = snapshot.messagesConsolidated.toLong(),
                playerLevelStart = snapshot.playerLevelStart.toLong(),
                playerLevelEnd = snapshot.playerLevelEnd.toLong(),
                timestamp = timestamp
            )
        }

    /**
     * Get consolidation history for an agent.
     *
     * @param agentId The agent identifier
     * @param gameId The game session ID
     * @param limit Maximum number of snapshots to return
     * @return List of consolidation snapshots
     */
    suspend fun getConsolidationHistory(
        agentId: String,
        gameId: String,
        limit: Int = 10
    ): List<ConsolidationSnapshot> = withContext(Dispatchers.Default) {
        database.gameQueries.selectAgentConsolidations(agentId, gameId, limit.toLong())
            .executeAsList()
            .mapNotNull { row ->
                try {
                    ConsolidationSnapshot(
                        id = row.id,
                        agentId = row.agentId,
                        gameId = row.gameId,
                        consolidationType = ConsolidationType.valueOf(row.consolidationType),
                        summary = json.decodeFromString(row.summaryJson),
                        messagesConsolidated = row.messagesConsolidated.toInt(),
                        playerLevelStart = row.playerLevelStart.toInt(),
                        playerLevelEnd = row.playerLevelEnd.toInt(),
                        timestamp = row.timestamp
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get the latest consolidation for an agent.
     * Useful for understanding what context has been summarized.
     *
     * @param agentId The agent identifier
     * @param gameId The game session ID
     * @return Latest consolidation snapshot, or null if never consolidated
     */
    suspend fun getLatestConsolidation(
        agentId: String,
        gameId: String
    ): ConsolidationSnapshot? = withContext(Dispatchers.Default) {
        database.gameQueries.selectLatestConsolidation(agentId, gameId)
            .executeAsOneOrNull()
            ?.let { row ->
                try {
                    ConsolidationSnapshot(
                        id = row.id,
                        agentId = row.agentId,
                        gameId = row.gameId,
                        consolidationType = ConsolidationType.valueOf(row.consolidationType),
                        summary = json.decodeFromString(row.summaryJson),
                        messagesConsolidated = row.messagesConsolidated.toInt(),
                        playerLevelStart = row.playerLevelStart.toInt(),
                        playerLevelEnd = row.playerLevelEnd.toInt(),
                        timestamp = row.timestamp
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    // ========================================
    // CLEANUP OPERATIONS
    // ========================================

    /**
     * Delete all agent data for a game when the game is deleted.
     * Called automatically as part of game deletion.
     *
     * @param gameId The game session ID
     */
    suspend fun deleteAllAgentDataForGame(gameId: String) = withContext(Dispatchers.Default) {
        database.transaction {
            database.gameQueries.deleteAgentMemoriesByGame(gameId)
            database.gameQueries.deleteAgentActionsByGame(gameId)
            database.gameQueries.deleteAgentConsolidationsByGame(gameId)
        }
    }

    /**
     * Delete old agent actions to prevent database bloat.
     * Recommend running this periodically (e.g., keep last 30 days).
     *
     * @param gameId The game session ID
     * @param olderThan Timestamp threshold - delete actions older than this
     */
    suspend fun deleteOldActions(gameId: String, olderThan: Long) = withContext(Dispatchers.Default) {
        database.gameQueries.deleteOldAgentActions(gameId, olderThan)
    }
}
