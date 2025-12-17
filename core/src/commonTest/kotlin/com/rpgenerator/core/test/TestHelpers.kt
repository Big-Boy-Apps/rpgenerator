package com.rpgenerator.core.test

import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*

internal object TestHelpers {

    internal fun createTestGameState(
        gameId: String = "test-game",
        systemType: SystemType = SystemType.SYSTEM_INTEGRATION,
        playerLevel: Int = 1,
        playerXP: Long = 0L,
        location: Location? = null,
        hasOpeningNarrationPlayed: Boolean = true  // Default to true for tests to avoid unexpected narration events
    ): GameState {
        val characterSheet = createTestCharacterSheet(playerLevel, playerXP)

        return GameState(
            gameId = gameId,
            systemType = systemType,
            characterSheet = characterSheet,
            currentLocation = location ?: createTestLocation(),
            hasOpeningNarrationPlayed = hasOpeningNarrationPlayed
        )
    }

    internal fun createTestCharacterSheet(
        level: Int = 1,
        xp: Long = 0L,
        baseStats: Stats = Stats()
    ): CharacterSheet {
        val resources = Resources.fromStats(baseStats)

        return CharacterSheet(
            level = level,
            xp = xp,
            baseStats = baseStats,
            resources = resources
        )
    }

    internal fun createTestLocation(
        id: String = "test-location",
        name: String = "Test Location",
        danger: Int = 1
    ): Location {
        return Location(
            id = id,
            name = name,
            zoneId = "test-zone",
            biome = Biome.FOREST,
            description = "A test location for unit tests",
            danger = danger,
            connections = emptyList(),
            features = emptyList(),
            lore = "Test lore"
        )
    }
}
