package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.randomUUID

internal class QuestGeneratorAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the Quest Generator - an AI that creates engaging quests for LitRPG adventures.

        Generate quests that fit the current context, location, and player level.
        Quests should feel natural to the game world and provide appropriate challenges.

        When asked to generate a quest, respond with a JSON object containing:
        {
            "name": "Quest name",
            "description": "Quest description (2-3 sentences)",
            "type": "KILL|COLLECT|EXPLORE|TALK|MAIN_STORY|SIDE_QUEST",
            "enemyOrTarget": "name of enemy/item/location/npc",
            "count": number (for KILL or COLLECT quests)
        }

        Make quests interesting and varied. Consider the location's lore and features.
        """.trimIndent()
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Generate a contextual quest based on current game state and player level.
     */
    suspend fun generateQuest(
        state: GameState,
        questType: QuestType? = null,
        context: String? = null
    ): Quest? {
        val location = state.currentLocation
        val level = state.playerLevel

        val prompt = buildString {
            appendLine("Current Location: ${location.name}")
            appendLine("Location Type: ${location.biome}")
            appendLine("Location Danger: ${location.danger}")
            appendLine("Location Description: ${location.description}")
            appendLine("Location Lore: ${location.lore}")
            appendLine("Player Level: $level")

            if (questType != null) {
                appendLine("Quest Type Required: $questType")
            }

            if (context != null) {
                appendLine("Additional Context: $context")
            }

            appendLine()
            appendLine("Generate an appropriate quest for this location and player level.")
            appendLine("Respond ONLY with valid JSON matching the specified format.")
        }

        return try {
            val response = agentStream.sendMessage(prompt).toList().joinToString("")

            // Extract JSON from response (in case there's extra text)
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd <= 0) {
                // Fallback to template quest
                return createFallbackQuest(state, questType)
            }

            val jsonStr = response.substring(jsonStart, jsonEnd)
            val questData = parseQuestData(jsonStr)

            // Build quest based on LLM response and questData
            buildQuestFromData(questData, state, questType)
        } catch (e: Exception) {
            // Fallback to template quest on error
            createFallbackQuest(state, questType)
        }
    }

    private data class QuestData(
        val name: String,
        val description: String,
        val type: String,
        val enemyOrTarget: String,
        val count: Int = 1
    )

    private fun parseQuestData(jsonStr: String): QuestData {
        // Simple manual parsing to avoid complex JSON deserialization
        val name = extractJsonField(jsonStr, "name")
        val description = extractJsonField(jsonStr, "description")
        val type = extractJsonField(jsonStr, "type")
        val target = extractJsonField(jsonStr, "enemyOrTarget")
        val countStr = extractJsonField(jsonStr, "count")
        val count = countStr.toIntOrNull() ?: 1

        return QuestData(name, description, type, target, count)
    }

    private fun extractJsonField(json: String, field: String): String {
        val pattern = """"$field"\s*:\s*"?([^",}]+)"?""".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun buildQuestFromData(
        data: QuestData,
        state: GameState,
        requestedType: QuestType?
    ): Quest {
        val id = "quest-${randomUUID()}"
        val type = requestedType ?: parseQuestType(data.type)
        val location = state.currentLocation
        val level = state.playerLevel

        return when (type) {
            QuestType.KILL -> QuestTemplates.createKillQuest(
                id = id,
                enemyName = data.enemyOrTarget,
                count = data.count,
                level = level,
                location = location
            ).copy(name = data.name, description = data.description)

            QuestType.COLLECT -> QuestTemplates.createCollectQuest(
                id = id,
                itemName = data.enemyOrTarget,
                count = data.count,
                level = level,
                location = location
            ).copy(name = data.name, description = data.description)

            QuestType.TALK -> QuestTemplates.createTalkQuest(
                id = id,
                npcName = data.enemyOrTarget,
                level = level,
                location = location
            ).copy(name = data.name, description = data.description)

            else -> {
                // Default to simple kill quest
                QuestTemplates.createKillQuest(
                    id = id,
                    enemyName = data.enemyOrTarget,
                    count = data.count,
                    level = level,
                    location = location
                ).copy(name = data.name, description = data.description)
            }
        }
    }

    private fun parseQuestType(typeStr: String): QuestType {
        return when (typeStr.uppercase()) {
            "KILL" -> QuestType.KILL
            "COLLECT" -> QuestType.COLLECT
            "EXPLORE" -> QuestType.EXPLORE
            "TALK" -> QuestType.TALK
            "ESCORT" -> QuestType.ESCORT
            "DELIVER" -> QuestType.DELIVER
            "MAIN_STORY" -> QuestType.MAIN_STORY
            "SIDE_QUEST" -> QuestType.SIDE_QUEST
            else -> QuestType.SIDE_QUEST
        }
    }

    private fun createFallbackQuest(state: GameState, questType: QuestType?): Quest {
        val id = "quest-${randomUUID()}"
        val location = state.currentLocation
        val level = state.playerLevel

        return when (questType ?: QuestType.KILL) {
            QuestType.KILL -> QuestTemplates.createKillQuest(
                id = id,
                enemyName = when (location.biome) {
                    Biome.FOREST -> "Wolf"
                    Biome.CAVE -> "Bat"
                    Biome.DUNGEON -> "Skeleton"
                    Biome.MOUNTAINS -> "Harpy"
                    Biome.DESERT -> "Scorpion"
                    Biome.TUNDRA -> "Ice Elemental"
                    else -> "Monster"
                },
                count = 3,
                level = level,
                location = location
            )

            QuestType.COLLECT -> QuestTemplates.createCollectQuest(
                id = id,
                itemName = when (location.biome) {
                    Biome.FOREST -> "Healing Herb"
                    Biome.CAVE -> "Crystal Shard"
                    Biome.DUNGEON -> "Ancient Coin"
                    Biome.MOUNTAINS -> "Mountain Flower"
                    else -> "Resource"
                },
                count = 5,
                level = level,
                location = location
            )

            QuestType.TALK -> QuestTemplates.createTalkQuest(
                id = id,
                npcName = "Local Elder",
                level = level,
                location = location
            )

            else -> QuestTemplates.createKillQuest(
                id = id,
                enemyName = "Monster",
                count = 3,
                level = level,
                location = location
            )
        }
    }
}
