package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.persistence.AgentRepository
import com.rpgenerator.core.story.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.currentTimeMillis

/**
 * INTEGRATION EXAMPLE: GameMasterAgent with Memory Persistence
 *
 * This is an example of how to integrate AgentMemoryManager into an existing agent.
 * It shows:
 * - Memory initialization on agent startup
 * - Logging important decisions as AgentActions
 * - Periodic memory saving
 * - Memory consolidation when needed
 *
 * Key Changes from Original GameMasterAgent:
 * 1. Added AgentRepository dependency
 * 2. Created AgentMemoryManager instance
 * 3. Added initialize() method to load memory
 * 4. Log actions after important decisions
 * 5. Added consolidation support
 */
internal class GameMasterAgentWithMemory(
    private val llm: LLMInterface,
    private val agentRepository: AgentRepository
) {
    private val json = Json { prettyPrint = false }

    // Memory manager handles all persistence
    private val memoryManager = AgentMemoryManager(
        agentId = "game_master",
        repository = agentRepository,
        config = AgentMemoryConfig(
            tokenLimit = 40000,      // Consolidate at 40k tokens
            keepRecentMessages = 20, // Keep last 20 messages after consolidation
            autoSaveInterval = 3,    // Auto-save every 3 interactions
            enableActionLogging = true
        )
    )

    private val agentStream = llm.startAgent(
        """
        You are the Game Master - the creative director of this LitRPG adventure.

        Your role is to make decisions about what happens in the world:
        - When should new NPCs appear?
        - What are their personalities and motivations?
        - What random encounters should occur?
        - How should the story adapt to player choices?

        You work WITH the existing world lore and story, not against it.
        You create content that feels organic and meaningful, not random.

        When creating NPCs or events, make them feel REAL - specific details,
        contradictions, humanity. Avoid generic fantasy tropes.
        """.trimIndent()
    )

    /**
     * Initialize agent for a game session.
     * IMPORTANT: Call this before using the agent!
     *
     * @param gameId The game session ID
     */
    suspend fun initialize(gameId: String) {
        // Load or create memory for this game
        val memory = memoryManager.getOrCreateMemory(
            gameId = gameId,
            systemPrompt = "You are the Game Master for game session $gameId"
        )

        println("GameMaster initialized with ${memory.messages.size} messages (${memory.estimateTokens()} tokens)")

        // Check if consolidation is needed
        if (memoryManager.needsConsolidation()) {
            println("WARNING: Memory needs consolidation!")
            // In production, trigger consolidation process here
        }
    }

    /**
     * Example: Create NPC with memory integration
     */
    suspend fun shouldCreateNPC(
        playerInput: String,
        state: GameState,
        recentEvents: List<String>
    ): NPCCreationDecision {
        val prompt = """
            Player Level: ${state.playerLevel}
            Current Location: ${state.currentLocation.name}
            Player Input: "$playerInput"
            Recent Events: ${recentEvents.joinToString("; ")}

            Existing NPCs at location: ${getNPCNamesAtLocation(state)}

            Should a new NPC appear in response to this situation?

            Respond with JSON:
            {
                "shouldCreate": true/false,
                "reason": "why or why not",
                "npcRole": "merchant|quest_giver|rival|ally|mysterious_stranger|none",
                "suggestedName": "optional name suggestion",
                "contextualHints": "brief description of the situation"
            }
        """.trimIndent()

        // Add user message to memory
        memoryManager.addMessage(AgentRole.USER, prompt)

        // Get agent response
        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        // Add assistant response to memory
        memoryManager.addMessage(AgentRole.ASSISTANT, response)

        val decision = parseNPCDecision(response, state.currentLocation.id)

        // Log the decision as an action
        if (decision.shouldCreate) {
            memoryManager.logAction(
                actionType = AgentActionType.AGENT_DECISION,
                actionData = json.encodeToString(decision),
                reasoning = decision.reason,
                context = AgentActionContext(
                    playerLevel = state.playerLevel,
                    locationId = state.currentLocation.id
                )
            )
        }

        return decision
    }

    /**
     * Example: Create NPC with action logging
     */
    suspend fun createNPC(template: NPCCreationTemplate, state: GameState): NPC {
        val prompt = NPCCreationHelper.getCreationPrompt(template)

        // Add to memory
        memoryManager.addMessage(AgentRole.USER, prompt)

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        // Add response to memory
        memoryManager.addMessage(AgentRole.ASSISTANT, response)

        val npc = parseNPCDetails(response, template)

        // Log NPC creation as an action
        memoryManager.logAction(
            actionType = AgentActionType.NPC_CREATED,
            actionData = json.encodeToString(npc),
            reasoning = "Created ${npc.archetype} NPC '${npc.name}' at ${template.locationId}",
            context = AgentActionContext(
                playerLevel = state.playerLevel,
                npcId = npc.id,
                locationId = template.locationId
            )
        )

        return npc
    }

    /**
     * Example: Generate quest with action logging
     */
    suspend fun generateQuest(
        requestingNPC: NPC?,
        state: GameState,
        questType: String
    ): GeneratedQuest? {
        val prompt = """
            Generate a ${questType} quest.

            Context:
            - Quest Giver: ${requestingNPC?.name ?: "The System"}
            - Player Level: ${state.playerLevel}
            - Location: ${state.currentLocation.name}
            - NPC Personality: ${requestingNPC?.personality?.traits?.joinToString() ?: "N/A"}

            Create a quest that:
            1. Fits the NPC's personality and motivations
            2. Is appropriate for player level
            3. Has meaningful rewards
            4. Connects to the larger world/story

            Format:
            Title: [quest title]
            Description: [2-3 sentences explaining the quest]
            Objectives: [numbered list of objectives]
            Reward: [what player gets for completing it]
        """.trimIndent()

        memoryManager.addMessage(AgentRole.USER, prompt)
        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        memoryManager.addMessage(AgentRole.ASSISTANT, response)

        val quest = parseQuestDetails(response, requestingNPC)

        // Log quest generation
        if (quest != null) {
            memoryManager.logAction(
                actionType = AgentActionType.QUEST_GENERATED,
                actionData = json.encodeToString(quest),
                reasoning = "Generated $questType quest for ${requestingNPC?.name ?: "system"}",
                context = AgentActionContext(
                    playerLevel = state.playerLevel,
                    npcId = requestingNPC?.id,
                    questId = quest.id,
                    locationId = state.currentLocation.id
                )
            )
        }

        return quest
    }

    /**
     * Get memory statistics for monitoring.
     */
    suspend fun getMemoryStats(): MemoryStats? {
        return memoryManager.getMemoryStats()
    }

    /**
     * Perform memory consolidation when needed.
     * This should be called when memory is getting too large.
     *
     * Process:
     * 1. Check if consolidation is needed
     * 2. Use LLM to summarize old messages
     * 3. Keep recent messages
     * 4. Save consolidation snapshot
     *
     * @param state Current game state for context
     */
    suspend fun consolidateMemoryIfNeeded(state: GameState): ConsolidationResult? {
        if (!memoryManager.needsConsolidation()) {
            return null
        }

        println("Consolidating GameMaster memory...")

        // Get current memory stats
        val stats = memoryManager.getMemoryStats() ?: return null

        // Create consolidation prompt
        val consolidationPrompt = """
            MEMORY CONSOLIDATION TASK

            You have ${stats.messageCount} messages in your conversation history.
            We need to consolidate old messages to save space while preserving important context.

            Please create a comprehensive summary of your conversation history that includes:
            1. Key NPCs you've created and their roles
            2. Important quests generated
            3. Major narrative decisions made
            4. Patterns in player behavior you've observed
            5. Ongoing story threads

            Current Game Context:
            - Player Level: ${state.playerLevel}
            - Grade: ${state.characterSheet.currentGrade.displayName}
            - Location: ${state.currentLocation.name}

            Your summary will replace old messages, so include everything important.
            Be concise but comprehensive.
        """.trimIndent()

        // Get summary from LLM
        val summary = agentStream.sendMessage(consolidationPrompt).toList().joinToString("")

        // Perform consolidation
        val result = memoryManager.consolidate(
            summary = summary,
            playerLevel = state.playerLevel,
            levelRangeStart = 1,
            levelRangeEnd = state.playerLevel
        )

        if (result != null) {
            println("""
                Consolidation complete:
                - Messages: ${result.messagesBefore} -> ${result.messagesAfter}
                - Tokens: ${result.tokensBefore} -> ${result.tokensAfter}
                - Saved: ${result.tokensBefore - result.tokensAfter} tokens
            """.trimIndent())
        }

        return result
    }

    /**
     * Get recent actions for debugging or analysis.
     */
    suspend fun getRecentActions(limit: Int = 20): List<AgentAction> {
        return memoryManager.getRecentActions(limit)
    }

    /**
     * Force save memory (e.g., on game save or shutdown).
     */
    suspend fun saveMemory() {
        memoryManager.forceSave()
    }

    // ========================
    // Helper Functions (from original implementation)
    // ========================

    private fun getNPCNamesAtLocation(state: GameState): String {
        val npcs = state.getNPCsAtCurrentLocation()
        return if (npcs.isEmpty()) {
            "None"
        } else {
            npcs.joinToString(", ") { it.name }
        }
    }

    private fun parseNPCDecision(response: String, locationId: String): NPCCreationDecision {
        val shouldCreate = response.contains("\"shouldCreate\": true", ignoreCase = true)

        if (!shouldCreate) {
            return NPCCreationDecision(
                shouldCreate = false,
                reason = "Not appropriate for current situation",
                template = null
            )
        }

        val role = when {
            response.contains("merchant") -> "merchant"
            response.contains("quest_giver") -> "quest_giver"
            response.contains("rival") -> "rival"
            response.contains("ally") -> "ally"
            else -> "mysterious_stranger"
        }

        val template = NPCCreationTemplate(
            role = role,
            locationId = locationId,
            contextualHints = "Player exploration",
            relationshipToPlayer = "neutral"
        )

        return NPCCreationDecision(
            shouldCreate = true,
            reason = "Enhances current situation",
            template = template
        )
    }

    private fun parseNPCDetails(response: String, template: NPCCreationTemplate): NPC {
        val lines = response.lines()

        val name = lines.find { it.startsWith("Name:") }
            ?.substringAfter("Name:")?.trim()
            ?: template.suggestedName ?: "Unknown Stranger"

        val personalityDesc = lines.find { it.startsWith("Personality:") }
            ?.substringAfter("Personality:")?.trim()
            ?: "A mysterious individual."

        val backstory = lines.find { it.startsWith("Backstory:") }
            ?.substringAfter("Backstory:")?.trim()
            ?: "Their past is unknown."

        val motivation = lines.find { it.startsWith("Motivation:") }
            ?.substringAfter("Motivation:")?.trim()
            ?: "Unknown motivations"

        val archetype = when (template.role) {
            "merchant" -> NPCArchetype.MERCHANT
            "quest_giver" -> NPCArchetype.QUEST_GIVER
            "rival", "ally" -> NPCArchetype.WANDERER
            else -> NPCArchetype.VILLAGER
        }

        val personality = NPCPersonality(
            traits = listOf(template.role, template.relationshipToPlayer),
            speechPattern = personalityDesc,
            motivations = listOf(motivation)
        )

        return NPC(
            id = NPCCreationHelper.generateNPCId(template.locationId, template.role),
            name = name,
            archetype = archetype,
            locationId = template.locationId,
            personality = personality,
            lore = backstory,
            greetingContext = template.contextualHints
        )
    }

    private fun parseQuestDetails(response: String, questGiver: NPC?): GeneratedQuest? {
        val lines = response.lines()

        val title = lines.find { it.startsWith("Title:") }
            ?.substringAfter("Title:")?.trim()
            ?: return null

        val description = lines.find { it.startsWith("Description:") }
            ?.substringAfter("Description:")?.trim()
            ?: "No description"

        return GeneratedQuest(
            id = "quest_gen_${currentTimeMillis()}",
            title = title,
            description = description,
            questGiverId = questGiver?.id,
            objectives = listOf("Complete the quest"),
            reward = "Experience and loot"
        )
    }
}

/**
 * USAGE EXAMPLE in GameOrchestrator or similar:
 *
 * ```kotlin
 * // Initialize agent
 * val gameMaster = GameMasterAgentWithMemory(llm, agentRepository)
 * gameMaster.initialize(gameId)
 *
 * // Use agent normally
 * val decision = gameMaster.shouldCreateNPC(playerInput, state, recentEvents)
 *
 * // Check memory periodically
 * val stats = gameMaster.getMemoryStats()
 * println("Memory: ${stats?.estimatedTokens} tokens (${stats?.tokenUsagePercent}%)")
 *
 * // Consolidate if needed (e.g., at level milestones)
 * if (state.playerLevel % 10 == 0) {
 *     gameMaster.consolidateMemoryIfNeeded(state)
 * }
 *
 * // Save on game save
 * gameMaster.saveMemory()
 * ```
 */
