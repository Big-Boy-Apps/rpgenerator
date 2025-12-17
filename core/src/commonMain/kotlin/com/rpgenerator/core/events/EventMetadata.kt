package com.rpgenerator.core.events

import com.rpgenerator.core.api.GameEvent
import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Metadata wrapper for game events that includes categorization,
 * importance, and entity links for better context selection.
 */
@Serializable
data class EventMetadata(
    val event: GameEvent,
    val category: EventCategory,
    val importance: EventImportance,
    val timestamp: Long = currentTimeMillis(),

    // Entity links for relationship tracking
    val npcId: String? = null,
    val locationId: String? = null,
    val questId: String? = null,
    val itemId: String? = null
) {
    /**
     * Extract searchable text from the event for full-text search.
     */
    val searchableText: String get() = when (event) {
        is GameEvent.NarratorText -> event.text
        is GameEvent.NPCDialogue -> "${event.npcName}: ${event.text}"
        is GameEvent.SystemNotification -> event.text
        is GameEvent.CombatLog -> event.text
        is GameEvent.StatChange -> "${event.statName}: ${event.oldValue} -> ${event.newValue}"
        is GameEvent.ItemGained -> "Gained ${event.quantity}x ${event.itemName}"
        is GameEvent.QuestUpdate -> "${event.status} - ${event.questName}"
    }

    companion object {
        /**
         * Infer metadata from a GameEvent using heuristics.
         */
        fun fromGameEvent(
            event: GameEvent,
            gameId: String,
            category: EventCategory? = null,
            importance: EventImportance? = null,
            npcId: String? = null,
            locationId: String? = null,
            questId: String? = null,
            itemId: String? = null
        ): EventMetadata = fromEvent(event, category, importance, npcId, locationId, questId, itemId)

        fun fromEvent(
            event: GameEvent,
            category: EventCategory? = null,
            importance: EventImportance? = null,
            npcId: String? = null,
            locationId: String? = null,
            questId: String? = null,
            itemId: String? = null
        ): EventMetadata {
            val inferredCategory = category ?: inferCategory(event)
            val inferredImportance = importance ?: inferImportance(event)

            return EventMetadata(
                event = event,
                category = inferredCategory,
                importance = inferredImportance,
                npcId = npcId ?: extractNpcId(event),
                locationId = locationId,
                questId = questId ?: extractQuestId(event),
                itemId = itemId ?: extractItemId(event)
            )
        }

        private fun inferCategory(event: GameEvent): EventCategory {
            return when (event) {
                is GameEvent.CombatLog -> EventCategory.COMBAT
                is GameEvent.NPCDialogue -> EventCategory.DIALOGUE
                is GameEvent.QuestUpdate -> EventCategory.QUEST
                is GameEvent.ItemGained -> EventCategory.INVENTORY
                is GameEvent.StatChange -> {
                    if (event.statName.equals("level", ignoreCase = true)) {
                        EventCategory.CHARACTER_PROGRESSION
                    } else {
                        EventCategory.COMBAT
                    }
                }
                is GameEvent.NarratorText -> {
                    // Try to infer from text content
                    val lowerText = event.text.lowercase()
                    when {
                        lowerText.contains("attack") || lowerText.contains("damage") ||
                        lowerText.contains("hit") || lowerText.contains("kill") ->
                            EventCategory.COMBAT

                        lowerText.contains("discover") || lowerText.contains("explore") ||
                        lowerText.contains("travel") || lowerText.contains("arrive") ->
                            EventCategory.EXPLORATION

                        lowerText.contains("level up") || lowerText.contains("gained skill") ->
                            EventCategory.CHARACTER_PROGRESSION

                        else -> EventCategory.NARRATIVE
                    }
                }
                is GameEvent.SystemNotification -> EventCategory.SYSTEM
            }
        }

        private fun inferImportance(event: GameEvent): EventImportance {
            return when (event) {
                is GameEvent.QuestUpdate -> {
                    when (event.status) {
                        com.rpgenerator.core.api.QuestStatus.NEW -> EventImportance.HIGH
                        com.rpgenerator.core.api.QuestStatus.COMPLETED -> EventImportance.CRITICAL
                        com.rpgenerator.core.api.QuestStatus.FAILED -> EventImportance.HIGH
                        else -> EventImportance.NORMAL
                    }
                }
                is GameEvent.StatChange -> {
                    if (event.statName.equals("level", ignoreCase = true)) {
                        EventImportance.CRITICAL
                    } else {
                        EventImportance.NORMAL
                    }
                }
                is GameEvent.NPCDialogue -> EventImportance.NORMAL
                is GameEvent.CombatLog -> {
                    val lowerText = event.text.lowercase()
                    if (lowerText.contains("defeated") || lowerText.contains("killed") ||
                        lowerText.contains("died")) {
                        EventImportance.HIGH
                    } else {
                        EventImportance.NORMAL
                    }
                }
                is GameEvent.ItemGained -> {
                    // Could be enhanced to check item rarity
                    EventImportance.NORMAL
                }
                is GameEvent.NarratorText -> {
                    val lowerText = event.text.lowercase()
                    when {
                        lowerText.contains("discover") && lowerText.contains("secret") ->
                            EventImportance.HIGH
                        lowerText.length > 200 -> EventImportance.NORMAL
                        else -> EventImportance.LOW
                    }
                }
                is GameEvent.SystemNotification -> EventImportance.LOW
            }
        }

        private fun extractNpcId(event: GameEvent): String? {
            return when (event) {
                is GameEvent.NPCDialogue -> event.npcId
                else -> null
            }
        }

        private fun extractQuestId(event: GameEvent): String? {
            return when (event) {
                is GameEvent.QuestUpdate -> event.questId
                else -> null
            }
        }

        private fun extractItemId(event: GameEvent): String? {
            return when (event) {
                is GameEvent.ItemGained -> event.itemId
                else -> null
            }
        }
    }
}
