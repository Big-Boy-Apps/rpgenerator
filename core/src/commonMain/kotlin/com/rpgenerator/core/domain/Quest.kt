package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable

@Serializable
internal data class Quest(
    val id: String,
    val name: String,
    val description: String,
    val type: QuestType,
    val objectives: List<QuestObjective>,
    val rewards: QuestRewards,
    val prerequisites: QuestPrerequisites = QuestPrerequisites(),
    val giver: String? = null, // NPC who gave the quest
    val status: QuestProgressStatus = QuestProgressStatus.NOT_STARTED
) {
    fun isComplete(): Boolean = objectives.all { it.isComplete() }

    fun canStart(state: GameState): Boolean {
        return prerequisites.minimumLevel <= state.playerLevel &&
               prerequisites.requiredLocationIds.all { it in state.discoveredTemplateLocations } &&
               prerequisites.requiredItemIds.all { itemId ->
                   state.characterSheet.inventory.hasItem(itemId)
               } &&
               prerequisites.requiredQuestIds.isEmpty() // Will be checked against completedQuests
    }

    fun updateObjective(objectiveId: String, progress: Int): Quest {
        val updated = objectives.map { obj ->
            if (obj.id == objectiveId) {
                obj.copy(currentProgress = (obj.currentProgress + progress).coerceAtMost(obj.targetProgress))
            } else {
                obj
            }
        }

        val newStatus = if (updated.all { it.isComplete() }) {
            QuestProgressStatus.COMPLETED
        } else if (status == QuestProgressStatus.NOT_STARTED) {
            QuestProgressStatus.IN_PROGRESS
        } else {
            status
        }

        return copy(objectives = updated, status = newStatus)
    }

    fun start(): Quest = copy(status = QuestProgressStatus.IN_PROGRESS)

    fun complete(): Quest = copy(status = QuestProgressStatus.COMPLETED)

    fun fail(): Quest = copy(status = QuestProgressStatus.FAILED)
}

@Serializable
internal enum class QuestType {
    KILL,           // Kill X enemies
    COLLECT,        // Collect X items
    EXPLORE,        // Discover X locations
    TALK,           // Talk to specific NPCs
    ESCORT,         // Escort NPC to location
    DELIVER,        // Deliver item to NPC
    MAIN_STORY,     // Main storyline quest
    SIDE_QUEST      // Optional side quest
}

@Serializable
internal enum class QuestProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

@Serializable
internal data class QuestObjective(
    val id: String,
    val description: String,
    val type: ObjectiveType,
    val targetId: String, // enemy name, item id, location id, or npc id
    val currentProgress: Int = 0,
    val targetProgress: Int = 1
) {
    fun isComplete(): Boolean = currentProgress >= targetProgress

    fun progressDescription(): String {
        return when (type) {
            ObjectiveType.KILL -> "$description ($currentProgress/$targetProgress)"
            ObjectiveType.COLLECT -> "$description ($currentProgress/$targetProgress)"
            ObjectiveType.EXPLORE -> "$description ($currentProgress/$targetProgress)"
            ObjectiveType.TALK -> if (isComplete()) "$description (Complete)" else description
            ObjectiveType.REACH_LOCATION -> if (isComplete()) "$description (Complete)" else description
        }
    }
}

@Serializable
internal enum class ObjectiveType {
    KILL,           // Kill enemies
    COLLECT,        // Collect items
    EXPLORE,        // Discover locations
    TALK,           // Talk to NPC
    REACH_LOCATION  // Reach a specific location
}

@Serializable
internal data class QuestRewards(
    val xp: Long = 0L,
    val items: List<InventoryItem> = emptyList(),
    val unlockedLocationIds: List<String> = emptyList(),
    val gold: Int = 0
)

@Serializable
internal data class QuestPrerequisites(
    val minimumLevel: Int = 1,
    val requiredQuestIds: List<String> = emptyList(),
    val requiredLocationIds: List<String> = emptyList(),
    val requiredItemIds: List<String> = emptyList()
)

/**
 * Templates for common quest patterns.
 * Used by QuestGeneratorAgent to create contextual quests.
 */
internal object QuestTemplates {

    fun createKillQuest(
        id: String,
        enemyName: String,
        count: Int,
        level: Int,
        location: Location
    ): Quest {
        val xpReward = count * level * 50L

        return Quest(
            id = id,
            name = "Hunt the $enemyName",
            description = "Defeat $count ${enemyName}s that have been terrorizing ${location.name}.",
            type = QuestType.KILL,
            objectives = listOf(
                QuestObjective(
                    id = "$id-obj-1",
                    description = "Defeat $count ${enemyName}s",
                    type = ObjectiveType.KILL,
                    targetId = enemyName.lowercase(),
                    targetProgress = count
                )
            ),
            rewards = QuestRewards(xp = xpReward),
            prerequisites = QuestPrerequisites(
                minimumLevel = level,
                requiredLocationIds = listOf(location.id)
            )
        )
    }

    fun createCollectQuest(
        id: String,
        itemName: String,
        count: Int,
        level: Int,
        location: Location
    ): Quest {
        val xpReward = count * level * 30L

        return Quest(
            id = id,
            name = "Gather $itemName",
            description = "Collect $count ${itemName}s from ${location.name}.",
            type = QuestType.COLLECT,
            objectives = listOf(
                QuestObjective(
                    id = "$id-obj-1",
                    description = "Collect $count ${itemName}s",
                    type = ObjectiveType.COLLECT,
                    targetId = itemName.lowercase().replace(" ", "-"),
                    targetProgress = count
                )
            ),
            rewards = QuestRewards(xp = xpReward),
            prerequisites = QuestPrerequisites(
                minimumLevel = level,
                requiredLocationIds = listOf(location.id)
            )
        )
    }

    fun createExploreQuest(
        id: String,
        targetLocation: Location,
        level: Int,
        currentLocation: Location
    ): Quest {
        val xpReward = level * 100L

        return Quest(
            id = id,
            name = "Explore ${targetLocation.name}",
            description = "Journey to ${targetLocation.name} and discover what lies there.",
            type = QuestType.EXPLORE,
            objectives = listOf(
                QuestObjective(
                    id = "$id-obj-1",
                    description = "Reach ${targetLocation.name}",
                    type = ObjectiveType.REACH_LOCATION,
                    targetId = targetLocation.id
                )
            ),
            rewards = QuestRewards(
                xp = xpReward,
                unlockedLocationIds = listOf(targetLocation.id)
            ),
            prerequisites = QuestPrerequisites(
                minimumLevel = level,
                requiredLocationIds = listOf(currentLocation.id)
            )
        )
    }

    fun createTalkQuest(
        id: String,
        npcName: String,
        level: Int,
        location: Location
    ): Quest {
        val xpReward = level * 40L

        return Quest(
            id = id,
            name = "Speak with $npcName",
            description = "Find $npcName in ${location.name} and learn what they know.",
            type = QuestType.TALK,
            objectives = listOf(
                QuestObjective(
                    id = "$id-obj-1",
                    description = "Talk to $npcName",
                    type = ObjectiveType.TALK,
                    targetId = npcName.lowercase().replace(" ", "-")
                )
            ),
            rewards = QuestRewards(xp = xpReward),
            prerequisites = QuestPrerequisites(
                minimumLevel = level,
                requiredLocationIds = listOf(location.id)
            )
        )
    }
}
