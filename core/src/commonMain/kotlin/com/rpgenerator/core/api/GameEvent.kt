package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * Events emitted by the game engine.
 * These represent all changes to the game state and narrative.
 */
@Serializable
sealed class GameEvent {
    /**
     * Narrator describing the scene or action results.
     */
    @Serializable
    data class NarratorText(val text: String) : GameEvent()

    /**
     * NPC speaking to the player.
     */
    @Serializable
    data class NPCDialogue(
        val npcId: String,
        val npcName: String,
        val text: String
    ) : GameEvent()

    /**
     * System notification (level up, quest complete, achievement, etc.)
     */
    @Serializable
    data class SystemNotification(val text: String) : GameEvent()

    /**
     * Combat or action log entry.
     */
    @Serializable
    data class CombatLog(val text: String) : GameEvent()

    /**
     * Player stat change.
     */
    @Serializable
    data class StatChange(
        val statName: String,
        val oldValue: Int,
        val newValue: Int
    ) : GameEvent()

    /**
     * Item added to inventory.
     */
    @Serializable
    data class ItemGained(
        val itemId: String,
        val itemName: String,
        val quantity: Int = 1
    ) : GameEvent()

    /**
     * Quest update (new, completed, failed).
     */
    @Serializable
    data class QuestUpdate(
        val questId: String,
        val questName: String,
        val status: QuestStatus
    ) : GameEvent()
}

@Serializable
enum class QuestStatus {
    NEW,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
