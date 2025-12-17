package com.rpgenerator.core.story

import com.rpgenerator.core.domain.NPC
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Manages dynamically generated NPCs.
 * All NPCs are created by AI on-demand - no hardcoded characters.
 */
internal class NPCManager {

    // Dynamically generated NPCs (unique to this playthrough)
    private val generatedNPCs = mutableMapOf<String, NPC>()

    /**
     * Get an NPC by ID
     */
    fun getNPC(npcId: String): NPC? {
        return generatedNPCs[npcId]
    }

    /**
     * Get all NPCs at a location
     */
    fun getNPCsAtLocation(locationId: String): List<NPC> {
        return generatedNPCs.values.filter { it.locationId == locationId }
    }

    /**
     * Register a dynamically generated NPC
     */
    fun registerGeneratedNPC(npc: NPC) {
        generatedNPCs[npc.id] = npc
    }

    /**
     * Check if an NPC exists at a location
     */
    fun hasNPCAtLocation(npcName: String, locationId: String): Boolean {
        val allNPCs = getNPCsAtLocation(locationId)
        return allNPCs.any { it.name.equals(npcName, ignoreCase = true) }
    }

    /**
     * Find NPC by name (case-insensitive search)
     */
    fun findNPCByName(name: String, locationId: String? = null): NPC? {
        val searchList = if (locationId != null) {
            getNPCsAtLocation(locationId)
        } else {
            getAllNPCs()
        }

        return searchList.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Get all NPCs
     */
    fun getAllNPCs(): List<NPC> {
        return generatedNPCs.values.toList()
    }

    /**
     * Remove a generated NPC (e.g., if they die or leave)
     */
    fun removeGeneratedNPC(npcId: String) {
        generatedNPCs.remove(npcId)
    }

    /**
     * Get NPC count for debugging
     */
    fun getNPCCount(): Int {
        return generatedNPCs.size
    }

    /**
     * Serialize generated NPCs for save/load
     */
    fun serializeGeneratedNPCs(): Map<String, NPC> {
        return generatedNPCs.toMap()
    }

    /**
     * Load generated NPCs from save
     */
    fun loadGeneratedNPCs(npcs: Map<String, NPC>) {
        generatedNPCs.clear()
        generatedNPCs.putAll(npcs)
    }
}

/**
 * Template for AI to use when creating NPCs
 */
data class NPCCreationTemplate(
    val suggestedName: String? = null,
    val role: String, // "merchant", "quest_giver", "rival", "ally", "mysterious_stranger"
    val locationId: String,
    val contextualHints: String, // What's happening when this NPC is created
    val relationshipToPlayer: String = "neutral" // "hostile", "friendly", "neutral", "romantic"
)

/**
 * Helper for game master to create NPCs
 */
object NPCCreationHelper {

    /**
     * Generate a unique NPC ID
     */
    fun generateNPCId(locationId: String, role: String): String {
        val timestamp = currentTimeMillis()
        return "npc_gen_${locationId}_${role}_${timestamp}"
    }

    /**
     * Provide prompt template for AI to generate NPC details
     */
    fun getCreationPrompt(template: NPCCreationTemplate): String {
        return """
            Create a new NPC for the player to meet.

            Context:
            - Location: ${template.locationId}
            - Role: ${template.role}
            - Situation: ${template.contextualHints}
            - Relationship to player: ${template.relationshipToPlayer}
            ${template.suggestedName?.let { "- Suggested name: $it" } ?: ""}

            Generate:
            1. Name (if not suggested)
            2. Brief personality (2-3 sentences describing their demeanor, speech patterns, quirks)
            3. Brief backstory (2-3 sentences about who they were before Integration and why they're here now)
            4. Current motivation (what do they want right now?)

            Make them feel real - give them specific details, contradictions, humanity.
            They should feel like a person, not a quest dispenser.

            Format:
            Name: [name]
            Personality: [personality description]
            Backstory: [backstory]
            Motivation: [current motivation]
        """.trimIndent()
    }

    /**
     * Example NPC templates for common situations
     */
    fun getMerchantTemplate(locationId: String, context: String): NPCCreationTemplate {
        return NPCCreationTemplate(
            role = "merchant",
            locationId = locationId,
            contextualHints = context,
            relationshipToPlayer = "neutral"
        )
    }

    fun getRivalTemplate(locationId: String, context: String): NPCCreationTemplate {
        return NPCCreationTemplate(
            role = "rival",
            locationId = locationId,
            contextualHints = context,
            relationshipToPlayer = "hostile"
        )
    }

    fun getQuestGiverTemplate(locationId: String, context: String): NPCCreationTemplate {
        return NPCCreationTemplate(
            role = "quest_giver",
            locationId = locationId,
            contextualHints = context,
            relationshipToPlayer = "friendly"
        )
    }

    fun getMysteriousStrangerTemplate(locationId: String, context: String): NPCCreationTemplate {
        return NPCCreationTemplate(
            role = "mysterious_stranger",
            locationId = locationId,
            contextualHints = context,
            relationshipToPlayer = "neutral"
        )
    }

    fun getAllyTemplate(locationId: String, context: String): NPCCreationTemplate {
        return NPCCreationTemplate(
            role = "ally",
            locationId = locationId,
            contextualHints = context,
            relationshipToPlayer = "friendly"
        )
    }
}
