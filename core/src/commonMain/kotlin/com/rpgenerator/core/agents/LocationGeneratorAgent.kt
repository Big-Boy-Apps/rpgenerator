package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.Biome
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.Location
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.currentTimeMillis

@Serializable
internal data class LocationGenerationResponse(
    val name: String,
    val biome: String,
    val description: String,
    val danger: Int,
    val features: List<String>,
    val lore: String
)

internal class LocationGeneratorAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the Location Generator - an AI that creates immersive, contextual locations for LitRPG games.

        Your task is to generate new locations that:
        1. Fit naturally within the parent location's environment
        2. Match the discovery context (what the player was doing/looking for)
        3. Scale appropriately with player level
        4. Are rich with atmospheric details and lore

        Available biomes: FOREST, MOUNTAINS, CAVE, DUNGEON, RUINS, SETTLEMENT, WASTELAND, JUNGLE, TUNDRA, DESERT, COSMIC_VOID, TUTORIAL_ZONE

        Respond in JSON format:
        {
            "name": "Location Name",
            "biome": "BIOME_TYPE",
            "description": "Vivid 1-2 sentence description of what the player sees",
            "danger": 1-20,
            "features": ["feature1", "feature2", "feature3"],
            "lore": "1-2 sentences of backstory or atmosphere"
        }

        IMPORTANT:
        - Danger level should be close to player level (±3 levels)
        - Features should be specific and actionable (e.g., "collapsed_bridge", "merchant_tent", "ancient_statue")
        - Description should be evocative and set the mood
        - Lore should hint at story hooks or world-building
        - Name should be memorable and descriptive
        """.trimIndent()
    )

    suspend fun generateLocation(
        parentLocation: Location,
        discoveryContext: String,
        state: GameState
    ): Location? {
        val prompt = """
            Parent Location: ${parentLocation.name}
            Parent Biome: ${parentLocation.biome}
            Parent Description: ${parentLocation.description}
            Parent Features: ${parentLocation.features.joinToString(", ")}
            Parent Lore: ${parentLocation.lore}

            Player Level: ${state.playerLevel}
            System Type: ${state.systemType}
            Discovery Context: "$discoveryContext"

            Generate a new location that the player discovered from ${parentLocation.name}.
            The location should:
            - Connect logically to the parent location's environment and biome
            - Reflect what the player was doing: "$discoveryContext"
            - Have danger level appropriate for level ${state.playerLevel} player (${state.playerLevel - 2} to ${state.playerLevel + 3})
            - Feel distinct yet connected to ${parentLocation.name}

            Respond with JSON only.
        """.trimIndent()

        val responseText = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseResponse(responseText, parentLocation, state)
    }

    private fun parseResponse(
        text: String,
        parentLocation: Location,
        state: GameState
    ): Location? {
        return try {
            // Extract JSON from potential markdown code blocks
            val jsonText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val response = Json.decodeFromString<LocationGenerationResponse>(jsonText)

            // Validate and convert biome
            val biome = try {
                Biome.valueOf(response.biome.uppercase())
            } catch (e: Exception) {
                // Fallback to parent biome if invalid
                parentLocation.biome
            }

            // Clamp danger level to reasonable range
            val danger = response.danger.coerceIn(1, 20)

            // Generate unique ID
            val locationId = "custom_${currentTimeMillis()}_${response.name.lowercase().replace(" ", "_")}"

            Location(
                id = locationId,
                name = response.name,
                zoneId = parentLocation.zoneId,
                biome = biome,
                description = response.description,
                danger = danger,
                connections = listOf(parentLocation.id),
                features = response.features,
                lore = response.lore
            )
        } catch (e: Exception) {
            // Log error in production, return null for now
            null
        }
    }
}
