package com.rpgenerator.core.domain

import com.rpgenerator.core.api.SystemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestSystemTest {

    private fun createTestGameState(): GameState {
        val baseStats = Stats(strength = 10, dexterity = 10, constitution = 10)
        val resources = Resources.fromStats(baseStats)
        val characterSheet = CharacterSheet(
            level = 1,
            xp = 0L,
            baseStats = baseStats,
            resources = resources
        )

        val startLocation = Location(
            id = "test-start",
            name = "Test Village",
            zoneId = "test-zone",
            biome = Biome.SETTLEMENT,
            description = "A small test village",
            danger = 1,
            connections = listOf("test-forest"),
            features = listOf("Town Square"),
            lore = "Test lore"
        )

        return GameState(
            gameId = "test-game",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = characterSheet,
            currentLocation = startLocation
        )
    }

    @Test
    fun `quest can be added to game state`() {
        var gameState = createTestGameState()

        val quest = QuestTemplates.createKillQuest(
            id = "quest-1",
            enemyName = "Goblin",
            count = 3,
            level = 1,
            location = gameState.currentLocation
        )

        gameState = gameState.addQuest(quest)

        assertEquals(1, gameState.activeQuests.size)
        assertTrue(gameState.activeQuests.containsKey("quest-1"))
        assertEquals(QuestProgressStatus.IN_PROGRESS, gameState.activeQuests["quest-1"]?.status)
    }

    @Test
    fun `quest objective progress updates correctly`() {
        var gameState = createTestGameState()

        val quest = QuestTemplates.createKillQuest(
            id = "quest-1",
            enemyName = "Goblin",
            count = 3,
            level = 1,
            location = gameState.currentLocation
        )

        gameState = gameState.addQuest(quest)

        val objective = quest.objectives.first()

        // Update progress
        gameState = gameState.updateQuestObjective("quest-1", objective.id, 1)

        val updatedQuest = gameState.activeQuests["quest-1"]
        val updatedObjective = updatedQuest?.objectives?.first()

        assertEquals(1, updatedObjective?.currentProgress)
        assertEquals(3, updatedObjective?.targetProgress)
        assertFalse(updatedObjective?.isComplete() ?: true)
    }

    @Test
    fun `quest completes when all objectives are met`() {
        var gameState = createTestGameState()

        val quest = QuestTemplates.createKillQuest(
            id = "quest-1",
            enemyName = "Goblin",
            count = 3,
            level = 1,
            location = gameState.currentLocation
        )

        gameState = gameState.addQuest(quest)

        val objective = quest.objectives.first()

        // Complete all objectives
        gameState = gameState.updateQuestObjective("quest-1", objective.id, 3)

        // Quest should be moved to completedQuests when all objectives are met
        assertFalse(gameState.activeQuests.containsKey("quest-1"))
        assertTrue(gameState.completedQuests.contains("quest-1"))
    }

    @Test
    fun `completing quest grants rewards`() {
        var gameState = createTestGameState()

        val rewardItem = InventoryItem(
            id = "reward-potion",
            name = "Health Potion",
            description = "Restores 50 HP",
            type = ItemType.CONSUMABLE,
            quantity = 2
        )

        val quest = Quest(
            id = "quest-1",
            name = "Test Quest",
            description = "A test quest",
            type = QuestType.KILL,
            objectives = listOf(
                QuestObjective(
                    id = "obj-1",
                    description = "Kill 1 Goblin",
                    type = ObjectiveType.KILL,
                    targetId = "goblin",
                    currentProgress = 1,
                    targetProgress = 1
                )
            ),
            rewards = QuestRewards(
                xp = 100L,
                items = listOf(rewardItem)
            )
        )

        gameState = gameState.addQuest(quest)

        val initialXP = gameState.playerXP

        // Complete the quest
        gameState = gameState.completeQuest("quest-1")

        // Quest should be moved to completed
        assertFalse(gameState.activeQuests.containsKey("quest-1"))
        assertTrue(gameState.completedQuests.contains("quest-1"))

        // XP should be granted
        assertEquals(initialXP + 100L, gameState.playerXP)

        // Items should be added to inventory
        assertTrue(gameState.characterSheet.inventory.hasItem("reward-potion", 2))
    }

    @Test
    fun `quest objective progress description shows correctly`() {
        val objective = QuestObjective(
            id = "obj-1",
            description = "Defeat Wolves",
            type = ObjectiveType.KILL,
            targetId = "wolf",
            currentProgress = 2,
            targetProgress = 5
        )

        val description = objective.progressDescription()
        assertTrue(description.contains("2/5"))
    }

    @Test
    fun `quest cannot exceed target progress`() {
        var gameState = createTestGameState()

        val quest = QuestTemplates.createKillQuest(
            id = "quest-1",
            enemyName = "Goblin",
            count = 5,
            level = 1,
            location = gameState.currentLocation
        )

        gameState = gameState.addQuest(quest)

        val objective = quest.objectives.first()

        // Add some initial progress
        gameState = gameState.updateQuestObjective("quest-1", objective.id, 2)

        // Try to add excessive progress (2 + 10 = 12, but target is 5, should cap at 5)
        gameState = gameState.updateQuestObjective("quest-1", objective.id, 10)

        // Quest should be completed since capped progress (5) meets the target
        assertFalse(gameState.activeQuests.containsKey("quest-1"))
        assertTrue(gameState.completedQuests.contains("quest-1"))
    }

    @Test
    fun `quest templates create correct quest types`() {
        val location = Location(
            id = "test-forest",
            name = "Dark Forest",
            zoneId = "test-zone",
            biome = Biome.FOREST,
            description = "A dark and dangerous forest",
            danger = 3,
            connections = emptyList(),
            features = listOf("Ancient Trees"),
            lore = "Test lore"
        )

        val killQuest = QuestTemplates.createKillQuest("q1", "Wolf", 5, 2, location)
        assertEquals(QuestType.KILL, killQuest.type)
        assertEquals(1, killQuest.objectives.size)
        assertEquals(ObjectiveType.KILL, killQuest.objectives.first().type)
        assertEquals(5, killQuest.objectives.first().targetProgress)

        val collectQuest = QuestTemplates.createCollectQuest("q2", "Herb", 10, 2, location)
        assertEquals(QuestType.COLLECT, collectQuest.type)
        assertEquals(ObjectiveType.COLLECT, collectQuest.objectives.first().type)

        val talkQuest = QuestTemplates.createTalkQuest("q3", "Elder", 2, location)
        assertEquals(QuestType.TALK, talkQuest.type)
        assertEquals(ObjectiveType.TALK, talkQuest.objectives.first().type)
    }

    @Test
    fun `quest rewards can unlock locations`() {
        var gameState = createTestGameState()

        val quest = Quest(
            id = "quest-1",
            name = "Unlock Forest",
            description = "Find the path to the forest",
            type = QuestType.EXPLORE,
            objectives = listOf(
                QuestObjective(
                    id = "obj-1",
                    description = "Find the hidden path",
                    type = ObjectiveType.EXPLORE,
                    targetId = "test-forest",
                    currentProgress = 1,
                    targetProgress = 1
                )
            ),
            rewards = QuestRewards(
                xp = 50L,
                unlockedLocationIds = listOf("secret-cave")
            )
        )

        gameState = gameState.addQuest(quest)

        assertFalse(gameState.discoveredTemplateLocations.contains("secret-cave"))

        gameState = gameState.completeQuest("quest-1")

        assertTrue(gameState.discoveredTemplateLocations.contains("secret-cave"))
    }

    @Test
    fun `multiple quests can be active simultaneously`() {
        var gameState = createTestGameState()

        val quest1 = QuestTemplates.createKillQuest("q1", "Goblin", 3, 1, gameState.currentLocation)
        val quest2 = QuestTemplates.createCollectQuest("q2", "Herb", 5, 1, gameState.currentLocation)
        val quest3 = QuestTemplates.createTalkQuest("q3", "Elder", 1, gameState.currentLocation)

        gameState = gameState.addQuest(quest1)
        gameState = gameState.addQuest(quest2)
        gameState = gameState.addQuest(quest3)

        assertEquals(3, gameState.activeQuests.size)
        assertTrue(gameState.activeQuests.containsKey("q1"))
        assertTrue(gameState.activeQuests.containsKey("q2"))
        assertTrue(gameState.activeQuests.containsKey("q3"))
    }

    @Test
    fun `quest prerequisites check player level`() {
        val gameState = createTestGameState()

        val quest = Quest(
            id = "quest-1",
            name = "High Level Quest",
            description = "A quest for experienced adventurers",
            type = QuestType.KILL,
            objectives = listOf(
                QuestObjective(
                    id = "obj-1",
                    description = "Defeat Dragon",
                    type = ObjectiveType.KILL,
                    targetId = "dragon",
                    targetProgress = 1
                )
            ),
            rewards = QuestRewards(xp = 1000L),
            prerequisites = QuestPrerequisites(minimumLevel = 10)
        )

        // Player is level 1, quest requires level 10
        assertFalse(quest.canStart(gameState))
    }

    @Test
    fun `quest can fail and be removed from active quests`() {
        var gameState = createTestGameState()

        val quest = QuestTemplates.createKillQuest("quest-1", "Goblin", 3, 1, gameState.currentLocation)
        gameState = gameState.addQuest(quest)

        assertTrue(gameState.activeQuests.containsKey("quest-1"))

        val failedQuest = quest.fail()
        gameState = gameState.updateQuest("quest-1", failedQuest)

        // Failed quest should be removed from active quests
        assertFalse(gameState.activeQuests.containsKey("quest-1"))
        assertFalse(gameState.completedQuests.contains("quest-1"))
    }
}
