package com.rpgenerator.core.domain

import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.api.WorldSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class GameState(
    val gameId: String,
    val systemType: SystemType,
    val worldSettings: WorldSettings, // World lore and narrative structure
    val characterSheet: CharacterSheet,
    val currentLocation: Location,
    val playerName: String = "Adventurer",
    val backstory: String = "",
    val discoveredTemplateLocations: Set<String> = emptySet(),
    val customLocations: Map<String, Location> = emptyMap(),
    val npcsByLocation: Map<String, List<NPC>> = emptyMap(), // locationId -> NPCs at that location
    val activeQuests: Map<String, Quest> = emptyMap(),
    val completedQuests: Set<String> = emptySet(),
    val deathCount: Int = 0,
    val hasOpeningNarrationPlayed: Boolean = false
) {
    // Convenience properties for backward compatibility
    val playerLevel: Int get() = characterSheet.level
    val playerXP: Long get() = characterSheet.xp
    val isDead: Boolean get() = characterSheet.resources.currentHP <= 0

    fun discoverLocation(locationId: String): GameState {
        return copy(discoveredTemplateLocations = discoveredTemplateLocations + locationId)
    }

    fun addCustomLocation(location: Location): GameState {
        return copy(customLocations = customLocations + (location.id to location))
    }

    fun moveToLocation(location: Location): GameState {
        return copy(currentLocation = location)
    }

    fun updateCharacterSheet(sheet: CharacterSheet): GameState {
        return copy(characterSheet = sheet)
    }

    fun gainXP(amount: Long): GameState {
        return copy(characterSheet = characterSheet.gainXP(amount))
    }

    fun takeDamage(damage: Int): GameState {
        return copy(characterSheet = characterSheet.takeDamage(damage))
    }

    fun heal(amount: Int): GameState {
        return copy(characterSheet = characterSheet.heal(amount))
    }

    fun addItem(item: InventoryItem): GameState {
        return copy(characterSheet = characterSheet.addToInventory(item))
    }

    fun removeItem(itemId: String, quantity: Int = 1): GameState {
        return copy(characterSheet = characterSheet.removeFromInventory(itemId, quantity))
    }

    fun equipItem(item: EquipmentItem): GameState {
        return copy(characterSheet = characterSheet.equipItem(item))
    }

    fun applyStatusEffect(effect: StatusEffect): GameState {
        return copy(characterSheet = characterSheet.applyStatusEffect(effect))
    }

    fun tickStatusEffects(): GameState {
        return copy(characterSheet = characterSheet.tickStatusEffects())
    }

    fun addNPC(npc: NPC): GameState {
        val currentNPCs = npcsByLocation[npc.locationId] ?: emptyList()
        val updatedNPCs = currentNPCs + npc
        return copy(npcsByLocation = npcsByLocation + (npc.locationId to updatedNPCs))
    }

    fun updateNPC(npc: NPC): GameState {
        val currentNPCs = npcsByLocation[npc.locationId] ?: emptyList()
        val updatedNPCs = currentNPCs.map { if (it.id == npc.id) npc else it }
        return copy(npcsByLocation = npcsByLocation + (npc.locationId to updatedNPCs))
    }

    fun getNPCsAtCurrentLocation(): List<NPC> {
        return npcsByLocation[currentLocation.id] ?: emptyList()
    }

    fun findNPC(npcId: String): NPC? {
        return npcsByLocation.values.flatten().find { it.id == npcId }
    }

    fun findNPCByName(name: String): NPC? {
        val npcs = getNPCsAtCurrentLocation()
        val searchTerm = name.lowercase()

        // Priority 1: Exact match (case insensitive)
        npcs.find { it.name.equals(name, ignoreCase = true) }?.let { return it }

        // Priority 2: Name contains search term
        npcs.find { it.name.lowercase().contains(searchTerm) }?.let { return it }

        // Priority 3: Search term contains part of name (e.g., "administrator arbiter" matches "Arbiter Zyx")
        npcs.find { npc ->
            npc.name.lowercase().split(" ").any { namePart ->
                searchTerm.contains(namePart) && namePart.length > 2
            }
        }?.let { return it }

        // Priority 4: Match by archetype/role (e.g., "guide", "merchant", "administrator")
        npcs.find { npc ->
            npc.archetype.name.lowercase().contains(searchTerm) ||
            searchTerm.contains(npc.archetype.name.lowercase())
        }?.let { return it }

        // Priority 5: Match common title words to archetypes
        val titleMappings = mapOf(
            "administrator" to listOf("TUTORIAL_GUIDE", "SYSTEM_GUIDE"),
            "arbiter" to listOf("TUTORIAL_GUIDE", "SYSTEM_GUIDE", "SAGE"),
            "guide" to listOf("TUTORIAL_GUIDE", "MENTOR", "SAGE"),
            "master" to listOf("MENTOR", "TRAINER", "SAGE"),
            "teacher" to listOf("MENTOR", "TRAINER"),
            "trainer" to listOf("TRAINER", "MENTOR"),
            "merchant" to listOf("MERCHANT", "TRADER"),
            "shopkeeper" to listOf("MERCHANT", "TRADER"),
            "vendor" to listOf("MERCHANT", "TRADER")
        )

        for ((title, archetypes) in titleMappings) {
            if (searchTerm.contains(title)) {
                npcs.find { npc -> archetypes.any { it.equals(npc.archetype.name, ignoreCase = true) } }
                    ?.let { return it }
            }
        }

        return null
    }

    // Quest management methods
    fun addQuest(quest: Quest): GameState {
        return copy(activeQuests = activeQuests + (quest.id to quest.start()))
    }

    fun updateQuest(questId: String, updatedQuest: Quest): GameState {
        if (updatedQuest.status == QuestProgressStatus.COMPLETED) {
            return copy(
                activeQuests = activeQuests - questId,
                completedQuests = completedQuests + questId
            )
        }

        if (updatedQuest.status == QuestProgressStatus.FAILED) {
            return copy(activeQuests = activeQuests - questId)
        }

        return copy(activeQuests = activeQuests + (questId to updatedQuest))
    }

    fun removeQuest(questId: String): GameState {
        return copy(activeQuests = activeQuests - questId)
    }

    fun getQuest(questId: String): Quest? {
        return activeQuests[questId]
    }

    fun updateQuestObjective(questId: String, objectiveId: String, progress: Int): GameState {
        val quest = activeQuests[questId] ?: return this
        val updated = quest.updateObjective(objectiveId, progress)
        return updateQuest(questId, updated)
    }

    fun completeQuest(questId: String): GameState {
        val quest = activeQuests[questId] ?: return this
        val completed = quest.complete()

        // Apply rewards
        var newState = this
        newState = newState.gainXP(completed.rewards.xp)

        completed.rewards.items.forEach { item ->
            newState = newState.addItem(item)
        }

        completed.rewards.unlockedLocationIds.forEach { locationId ->
            newState = newState.discoverLocation(locationId)
        }

        return newState.updateQuest(questId, completed)
    }
}
