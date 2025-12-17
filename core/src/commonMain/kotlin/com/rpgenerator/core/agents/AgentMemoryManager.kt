package com.rpgenerator.core.agents

import com.rpgenerator.core.domain.AgentAction
import com.rpgenerator.core.domain.AgentActionContext
import com.rpgenerator.core.domain.AgentActionType
import com.rpgenerator.core.domain.AgentMemory
import com.rpgenerator.core.domain.AgentMemoryConfig
import com.rpgenerator.core.domain.AgentMessage
import com.rpgenerator.core.domain.AgentRole
import com.rpgenerator.core.domain.ConsolidationResult
import com.rpgenerator.core.domain.ConsolidationSnapshot
import com.rpgenerator.core.persistence.AgentRepository

/**
 * Helper class for managing agent memory in AI agents.
 *
 * This class provides a high-level interface for agents to:
 * - Load/save conversation history
 * - Log important actions
 * - Automatically consolidate when needed
 * - Track memory usage
 *
 * Usage in an agent:
 * ```
 * class GameMasterAgent(
 *     private val llm: LLMInterface,
 *     private val agentRepository: AgentRepository
 * ) {
 *     private val memoryManager = AgentMemoryManager(
 *         agentId = "game_master",
 *         repository = agentRepository,
 *         config = AgentMemoryConfig(tokenLimit = 40000)
 *     )
 *
 *     suspend fun initialize(gameId: String) {
 *         memoryManager.loadMemory(gameId)
 *     }
 *
 *     suspend fun createNPC(...): NPC {
 *         // ... agent logic ...
 *         memoryManager.logAction(...)
 *         return npc
 *     }
 * }
 * ```
 */
internal class AgentMemoryManager(
    private val agentId: String,
    private val repository: AgentRepository,
    private val config: AgentMemoryConfig = AgentMemoryConfig()
) {
    private var currentMemory: AgentMemory? = null
    private var interactionsSinceLastSave = 0

    // ========================================
    // MEMORY LOADING AND SAVING
    // ========================================

    /**
     * Load agent memory for a game session.
     * Call this when agent initializes for a game.
     *
     * @param gameId The game session ID
     * @return Loaded memory, or null if no saved memory exists
     */
    suspend fun loadMemory(gameId: String): AgentMemory? {
        currentMemory = repository.loadAgentMemory(agentId, gameId)
        return currentMemory
    }

    /**
     * Get or create memory for a game session.
     * If no memory exists, creates a new empty memory.
     *
     * @param gameId The game session ID
     * @param systemPrompt Optional system prompt to initialize with
     * @return Agent memory (loaded or newly created)
     */
    suspend fun getOrCreateMemory(
        gameId: String,
        systemPrompt: String? = null
    ): AgentMemory {
        if (currentMemory != null && currentMemory?.gameId == gameId) {
            return currentMemory!!
        }

        currentMemory = repository.loadAgentMemory(agentId, gameId)

        if (currentMemory == null) {
            // Create new memory
            val messages = if (systemPrompt != null) {
                listOf(AgentMessage(AgentRole.SYSTEM, systemPrompt))
            } else {
                emptyList()
            }

            currentMemory = AgentMemory(
                agentId = agentId,
                gameId = gameId,
                messages = messages
            )
            saveMemory()
        }

        return currentMemory!!
    }

    /**
     * Save current memory to database.
     * Called automatically after interactions based on config.autoSaveInterval.
     */
    suspend fun saveMemory() {
        currentMemory?.let { memory ->
            repository.saveAgentMemory(memory)
        }
        interactionsSinceLastSave = 0
    }

    /**
     * Force save memory immediately.
     */
    suspend fun forceSave() {
        saveMemory()
    }

    // ========================================
    // MESSAGE MANAGEMENT
    // ========================================

    /**
     * Add a message to conversation history.
     *
     * @param role The role (USER, ASSISTANT)
     * @param content The message content
     * @param autoSave Whether to auto-save based on config
     */
    suspend fun addMessage(
        role: AgentRole,
        content: String,
        autoSave: Boolean = true
    ) {
        currentMemory?.let { memory ->
            val message = AgentMessage(role, content)
            currentMemory = memory.addMessage(message)

            interactionsSinceLastSave++

            // Check if we need to consolidate
            if (memory.needsConsolidation(config.tokenLimit)) {
                // Note: Actual consolidation requires LLM to create summary
                // This should be triggered externally, but we can log it
                println("WARNING: Agent $agentId memory needs consolidation (${memory.estimateTokens()} tokens)")
            }

            // Auto-save based on config
            if (autoSave && interactionsSinceLastSave >= config.autoSaveInterval) {
                saveMemory()
            }
        }
    }

    /**
     * Get conversation history as formatted string.
     * Useful for debugging or displaying to user.
     *
     * @param includeConsolidated Whether to include consolidated context
     * @return Formatted conversation history
     */
    fun getConversationHistory(includeConsolidated: Boolean = true): String {
        val memory = currentMemory ?: return "No memory loaded"

        val sb = StringBuilder()

        if (includeConsolidated && memory.consolidatedContext != null) {
            sb.appendLine("=== CONSOLIDATED CONTEXT ===")
            sb.appendLine(memory.consolidatedContext)
            sb.appendLine()
        }

        sb.appendLine("=== CONVERSATION HISTORY ===")
        memory.messages.forEach { message ->
            sb.appendLine("[${message.role}] ${message.content}")
        }

        return sb.toString()
    }

    // ========================================
    // ACTION LOGGING
    // ========================================

    /**
     * Log an important agent action with reasoning.
     *
     * @param actionType The type of action
     * @param actionData JSON-serialized action data
     * @param reasoning Why the agent made this decision
     * @param context Current game context
     */
    suspend fun logAction(
        actionType: AgentActionType,
        actionData: String,
        reasoning: String,
        context: AgentActionContext
    ) {
        if (!config.enableActionLogging) return

        val memory = currentMemory ?: return

        val action = AgentAction(
            agentId = agentId,
            gameId = memory.gameId,
            actionType = actionType,
            actionData = actionData,
            reasoning = reasoning,
            context = context
        )

        repository.saveAgentAction(action)
    }

    /**
     * Get recent actions for this agent.
     *
     * @param limit Maximum number of actions to return
     * @return List of recent actions
     */
    suspend fun getRecentActions(limit: Int = 20): List<AgentAction> {
        val memory = currentMemory ?: return emptyList()
        return repository.getAgentActions(agentId, memory.gameId, limit)
    }

    // ========================================
    // MEMORY CONSOLIDATION
    // ========================================

    /**
     * Check if memory needs consolidation.
     *
     * @return True if consolidation should be performed
     */
    fun needsConsolidation(): Boolean {
        return currentMemory?.needsConsolidation(config.tokenLimit) ?: false
    }

    /**
     * Get memory statistics.
     *
     * @return Memory stats for monitoring
     */
    fun getMemoryStats(): MemoryStats? {
        val memory = currentMemory ?: return null

        return MemoryStats(
            agentId = agentId,
            gameId = memory.gameId,
            messageCount = memory.messages.size,
            estimatedTokens = memory.estimateTokens(),
            tokenLimit = config.tokenLimit,
            needsConsolidation = memory.needsConsolidation(config.tokenLimit),
            lastConsolidated = memory.lastConsolidated,
            consolidationCount = memory.metadata.consolidationCount
        )
    }

    /**
     * Perform memory consolidation.
     * This should be called by the agent after generating a summary via LLM.
     *
     * @param summary LLM-generated summary of old messages
     * @param playerLevel Current player level
     * @param levelRangeStart Level range start
     * @param levelRangeEnd Level range end
     * @return Consolidation result with stats
     */
    suspend fun consolidate(
        summary: String,
        playerLevel: Int,
        levelRangeStart: Int,
        levelRangeEnd: Int
    ): ConsolidationResult? {
        val memory = currentMemory ?: return null

        val result = repository.consolidateAgentMemory(
            agentId = agentId,
            gameId = memory.gameId,
            summary = summary,
            keepRecentCount = config.keepRecentMessages,
            playerLevel = playerLevel,
            levelRangeStart = levelRangeStart,
            levelRangeEnd = levelRangeEnd
        )

        // Update current memory
        if (result != null) {
            currentMemory = result.updatedMemory
        }

        return result
    }

    /**
     * Get consolidation history.
     *
     * @param limit Maximum number of snapshots to return
     * @return List of consolidation snapshots
     */
    suspend fun getConsolidationHistory(limit: Int = 10): List<ConsolidationSnapshot> {
        val memory = currentMemory ?: return emptyList()
        return repository.getConsolidationHistory(agentId, memory.gameId, limit)
    }
}

/**
 * Memory statistics for monitoring.
 */
data class MemoryStats(
    val agentId: String,
    val gameId: String,
    val messageCount: Int,
    val estimatedTokens: Int,
    val tokenLimit: Int,
    val needsConsolidation: Boolean,
    val lastConsolidated: Long?,
    val consolidationCount: Int
) {
    val tokenUsagePercent: Int
        get() = ((estimatedTokens.toFloat() / tokenLimit) * 100).toInt()

    override fun toString(): String {
        return """
            Agent: $agentId
            Messages: $messageCount
            Tokens: $estimatedTokens / $tokenLimit ($tokenUsagePercent%)
            Needs Consolidation: $needsConsolidation
            Consolidations: $consolidationCount
            Last Consolidated: ${if (lastConsolidated != null) "$lastConsolidated" else "Never"}
        """.trimIndent()
    }
}
