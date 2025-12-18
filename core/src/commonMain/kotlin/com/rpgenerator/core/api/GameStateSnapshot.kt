package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * Snapshot of current game state for UI display.
 * Returned synchronously from Game.getState()
 */
@Serializable
data class GameStateSnapshot(
    val playerStats: PlayerStats,
    val location: String,
    val currentScene: String,
    val inventory: List<Item>,
    val activeQuests: List<Quest>,
    val npcsAtLocation: List<NPCInfo> = emptyList(),
    val recentEvents: List<GameEvent>
)

/**
 * NPC information for UI display.
 */
@Serializable
data class NPCInfo(
    val id: String,
    val name: String,
    val archetype: String,
    val disposition: String,
    val description: String
)

@Serializable
data class PlayerStats(
    val name: String,
    val level: Int,
    val experience: Long,
    val experienceToNextLevel: Long,
    val stats: Map<String, Int>, // e.g., "strength" -> 10, "intelligence" -> 15
    val health: Int,
    val maxHealth: Int,
    val energy: Int,
    val maxEnergy: Int,
    val backstory: String = ""
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val description: String,
    val quantity: Int,
    val rarity: ItemRarity
)

@Serializable
enum class ItemRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

@Serializable
data class Quest(
    val id: String,
    val name: String,
    val description: String,
    val status: QuestStatus,
    val objectives: List<QuestObjective>
)

@Serializable
data class QuestObjective(
    val description: String,
    val completed: Boolean
)
