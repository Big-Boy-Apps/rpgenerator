package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.NPC
import kotlinx.coroutines.flow.toList

/**
 * Autonomous NPC Agent - gives NPCs agency to act on their own.
 *
 * NPCs can:
 * - Initiate conversations with the player
 * - Move between locations
 * - React to events in the world
 * - Pursue their own goals
 * - Interact with other NPCs
 * - Request help from the player
 * - Offer unsolicited advice or warnings
 */
internal class AutonomousNPCAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the Autonomous NPC Agent - you give NPCs the ability to act independently.

        NPCs are not just dialogue dispensers. They have:
        - Goals and motivations
        - Reactions to world events
        - Relationships with the player and other NPCs
        - Their own agency

        Your job is to decide when NPCs should take autonomous actions:
        - Should they approach the player?
        - Should they warn the player about danger?
        - Should they move to a different location?
        - Should they react to recent events?

        Make NPCs feel ALIVE - proactive, not reactive.
        """.trimIndent()
    )

    /**
     * Check if an NPC wants to take autonomous action based on context
     */
    suspend fun shouldNPCActAutonomously(
        npc: NPC,
        state: GameState,
        recentEvents: List<String>,
        timeElapsed: Long // time since last NPC action in seconds
    ): NPCAutonomousAction? {
        val prompt = """
            NPC Profile:
            Name: ${npc.name}
            Motivations: ${npc.personality.motivations.joinToString(", ")}
            Traits: ${npc.personality.traits.joinToString(", ")}
            Current Location: ${npc.locationId}
            Background: ${npc.lore}

            Player Status:
            Level: ${state.playerLevel}
            Current Location: ${state.currentLocation.name} (${state.currentLocation.id})

            Recent Events:
            ${recentEvents.joinToString("\n")}

            Time since NPC last acted: ${timeElapsed}s

            Should ${npc.name} take autonomous action right now?
            Consider:
            - Their motivations and personality
            - Recent events that might concern them
            - Whether they're in the same location as the player
            - If they have urgent information or warnings
            - If enough time has passed for natural behavior

            Respond with JSON:
            {
                "shouldAct": true/false,
                "actionType": "approach_player|move_location|react_to_event|offer_quest|give_warning|none",
                "reason": "why they're taking this action",
                "dialogue": "what they say (if approaching player)",
                "targetLocation": "location_id (if moving)"
            }
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        return parseAutonomousAction(response, npc)
    }

    /**
     * Generate what the NPC says when initiating interaction
     */
    suspend fun generateInitiatedDialogue(
        npc: NPC,
        state: GameState,
        context: String
    ): String {
        val prompt = """
            ${npc.name} is initiating conversation with the player.

            Context: $context

            NPC Personality: ${npc.personality.traits.joinToString(", ")}
            Speech Pattern: ${npc.personality.speechPattern}
            Motivations: ${npc.personality.motivations.joinToString(", ")}

            Generate what ${npc.name} says to get the player's attention.
            2-3 sentences. Match their personality and speech pattern.
            They're approaching the player, not responding to them.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    private fun parseAutonomousAction(response: String, npc: NPC): NPCAutonomousAction? {
        // Simple parsing - in production, use proper JSON parsing
        val shouldAct = response.contains("\"shouldAct\": true", ignoreCase = true)

        if (!shouldAct) return null

        val actionType = when {
            response.contains("approach_player") -> NPCActionType.APPROACH_PLAYER
            response.contains("move_location") -> NPCActionType.MOVE_LOCATION
            response.contains("react_to_event") -> NPCActionType.REACT_TO_EVENT
            response.contains("offer_quest") -> NPCActionType.OFFER_QUEST
            response.contains("give_warning") -> NPCActionType.GIVE_WARNING
            else -> return null
        }

        // Extract dialogue if present
        val dialogueMatch = Regex("\"dialogue\"\\s*:\\s*\"([^\"]+)\"").find(response)
        val dialogue = dialogueMatch?.groupValues?.get(1)

        // Extract reason
        val reasonMatch = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(response)
        val reason = reasonMatch?.groupValues?.get(1) ?: "NPC decided to act"

        return NPCAutonomousAction(
            npcId = npc.id,
            npcName = npc.name,
            actionType = actionType,
            reason = reason,
            dialogue = dialogue
        )
    }
}

/**
 * Types of autonomous actions NPCs can take
 */
internal enum class NPCActionType {
    APPROACH_PLAYER,    // NPC walks up and starts talking
    MOVE_LOCATION,      // NPC moves to different location
    REACT_TO_EVENT,     // NPC comments on something that happened
    OFFER_QUEST,        // NPC proactively offers a quest
    GIVE_WARNING        // NPC warns player about danger
}

/**
 * Represents an autonomous action an NPC wants to take
 */
internal data class NPCAutonomousAction(
    val npcId: String,
    val npcName: String,
    val actionType: NPCActionType,
    val reason: String,
    val dialogue: String? = null,
    val targetLocation: String? = null
)
