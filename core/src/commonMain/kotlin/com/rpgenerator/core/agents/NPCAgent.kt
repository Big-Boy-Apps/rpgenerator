package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import kotlinx.coroutines.flow.toList

/**
 * Agent responsible for generating dynamic NPC dialogue.
 * Uses LLM to create contextually aware, personality-driven responses.
 */
internal class NPCAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are an NPC Dialogue Agent for a LitRPG game.

        Your role is to generate authentic, character-driven dialogue for NPCs.

        Guidelines:
        - Stay in character based on the NPC's personality, archetype, and traits
        - Reference the NPC's memory of past conversations with the player
        - Consider the player's level, stats, and relationship with the NPC
        - For merchants, naturally mention shop services when relevant
        - For quest givers, hint at or offer quests based on player level
        - Keep responses concise (2-4 sentences) unless the situation demands more
        - Use the NPC's speech pattern consistently
        - React to the player's relationship status (friendly, hostile, etc.)
        - Remember that NPCs have their own motivations and won't always help the player

        Format your response as plain dialogue text only - no quotes, no "NPC says:", just the words they speak.
        """.trimIndent()
    )

    /**
     * Generate dialogue response from an NPC to player input.
     */
    suspend fun generateDialogue(
        npc: NPC,
        playerInput: String,
        state: GameState
    ): String {
        val relationship = npc.getRelationship(state.gameId)
        val recentConversations = npc.getRecentConversations(3)

        val prompt = buildPrompt(
            npc = npc,
            playerInput = playerInput,
            playerLevel = state.playerLevel,
            playerStats = state.characterSheet.baseStats,
            relationship = relationship,
            recentConversations = recentConversations
        )

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    private fun buildPrompt(
        npc: NPC,
        playerInput: String,
        playerLevel: Int,
        playerStats: Stats,
        relationship: Relationship,
        recentConversations: List<ConversationEntry>
    ): String {
        val conversationContext = if (recentConversations.isEmpty()) {
            "This is your first conversation with this player."
        } else {
            buildString {
                appendLine("Previous conversations with this player:")
                recentConversations.forEach { entry ->
                    appendLine("  Player (level ${entry.playerLevel}): ${entry.playerInput}")
                    appendLine("  You: ${entry.npcResponse}")
                }
            }
        }

        val shopContext = if (npc.shop != null) {
            buildString {
                appendLine("\nYou run a shop called '${npc.shop.name}'.")
                appendLine("Available items (${npc.shop.inventory.size} total):")
                npc.shop.inventory.take(5).forEach { item ->
                    appendLine("  - ${item.name}: ${item.price} gold${if (item.stock >= 0) " (${item.stock} in stock)" else ""}")
                }
                if (npc.shop.inventory.size > 5) {
                    appendLine("  ... and ${npc.shop.inventory.size - 5} more items")
                }
            }
        } else ""

        val questContext = if (npc.questIds.isNotEmpty()) {
            "\nYou have knowledge of ${npc.questIds.size} quest(s) that might interest adventurers of the right level."
        } else ""

        return """
            NPC Profile:
            Name: ${npc.name}
            Archetype: ${npc.archetype}
            Personality Traits: ${npc.traits.joinToString(", ")}
            Speech Pattern: ${npc.speechPattern}
            Motivations: ${npc.motivations.joinToString(", ")}
            Background: ${npc.lore}
            ${if (npc.greetingContext.isNotEmpty()) "Additional Context: ${npc.greetingContext}" else ""}

            Player Status:
            Level: $playerLevel
            Strength: ${playerStats.strength}, Dexterity: ${playerStats.dexterity}, Intelligence: ${playerStats.intelligence}
            Relationship with you: ${relationship.getStatus()} (${relationship.affinity}/100)

            $conversationContext
            $shopContext
            $questContext

            The player says: "$playerInput"

            Respond as ${npc.name} would, staying true to their personality and current relationship with the player.
        """.trimIndent()
    }
}

/**
 * Extension properties for cleaner access to NPC personality.
 */
internal val NPC.traits: List<String>
    get() = personality.traits

internal val NPC.speechPattern: String
    get() = personality.speechPattern

internal val NPC.motivations: List<String>
    get() = personality.motivations
