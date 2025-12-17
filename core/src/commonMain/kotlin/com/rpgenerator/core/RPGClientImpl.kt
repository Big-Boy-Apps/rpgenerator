package com.rpgenerator.core

import app.cash.sqldelight.db.SqlDriver
import com.rpgenerator.core.api.*
import com.rpgenerator.core.character.CharacterCreationService
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.persistence.GameDatabase
import com.rpgenerator.core.persistence.GameRepository
import com.rpgenerator.core.util.randomUUID

/**
 * Implementation of RPGClient.
 * Main entry point for the RPGenerator library.
 */
internal class RPGClientImpl(
    private val driver: SqlDriver
) {
    private val database: GameDatabase
    private val repository: GameRepository

    init {
        // Create database schema if needed
        GameDatabase.Schema.create(driver)
        database = GameDatabase(driver)
        repository = GameRepository(database)
    }

    fun getGames(): List<GameInfo> {
        // Note: This is a blocking call in the public API
        // In a real implementation, we might want to make this suspend
        return kotlinx.coroutines.runBlocking {
            repository.getAllGames()
        }
    }

    suspend fun startGame(
        config: GameConfig,
        llm: LLMInterface
    ): Game {
        val gameId = randomUUID()
        val playerName = config.characterCreation.name

        // Create initial game state with character creation
        val initialState = createInitialState(gameId, config)

        // Save to database
        repository.createGame(
            gameId = gameId,
            playerName = playerName,
            config = config,
            initialState = initialState
        )

        // Return active game instance
        return GameImpl(
            gameId = gameId,
            llm = llm,
            repository = repository,
            initialState = initialState
        )
    }

    suspend fun resumeGame(
        gameInfo: GameInfo,
        llm: LLMInterface
    ): Game {
        // Load game state from database
        val state = repository.loadGameState(gameInfo.id)
            ?: throw IllegalStateException("Game state not found for ${gameInfo.id}")

        // Create game instance
        val game = GameImpl(
            gameId = gameInfo.id,
            llm = llm,
            repository = repository,
            initialState = state
        )

        // Set the playtime from saved data
        game.setInitialPlaytime(gameInfo.playtime)

        return game
    }

    /**
     * Create the initial game state for a new game.
     */
    private fun createInitialState(gameId: String, config: GameConfig): GameState {
        // Use character creation service to generate character
        val (stats, backstory) = CharacterCreationService.createCharacter(
            options = config.characterCreation,
            systemType = config.systemType,
            difficulty = config.difficulty
        )

        val resources = Resources.fromStats(stats)

        val characterSheet = CharacterSheet(
            level = 1,
            xp = 0L,
            baseStats = stats,
            resources = resources
        )

        // Get or generate world settings
        val worldSettings = config.worldSettings
            ?: com.rpgenerator.core.generation.WorldGenerator(null).getDefaultWorld(config.systemType)

        // Get starting location based on system type
        val startingLocation = getStartingLocation(config.systemType)

        return GameState(
            gameId = gameId,
            systemType = config.systemType,
            worldSettings = worldSettings,
            characterSheet = characterSheet,
            currentLocation = startingLocation,
            playerName = config.characterCreation.name,
            backstory = backstory
        )
    }

    /**
     * Get the starting location for the given system type.
     */
    private fun getStartingLocation(systemType: SystemType): Location {
        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> Location(
                id = "tutorial_zone_start",
                name = "Tutorial Zone",
                zoneId = "tutorial",
                biome = Biome.TUTORIAL_ZONE,
                description = "A safe zone where newly integrated beings learn the basics of the System.",
                danger = 1,
                connections = listOf("forest_clearing"),
                features = listOf("Safe Zone", "Training Dummies", "System Terminal"),
                lore = "When the System arrived, it created these zones to help beings adapt to their new reality."
            )

            SystemType.CULTIVATION_PATH -> Location(
                id = "mountain_sect_entrance",
                name = "Sect Entrance",
                zoneId = "mountain_sect",
                biome = Biome.MOUNTAINS,
                description = "The entrance to the Azure Peak Sect, where cultivators begin their journey.",
                danger = 1,
                connections = listOf("mountain_path"),
                features = listOf("Sect Gate", "Elder's Hall", "Spirit Well"),
                lore = "The Azure Peak Sect has produced countless immortals over ten thousand years."
            )

            SystemType.DEATH_LOOP -> Location(
                id = "respawn_chamber",
                name = "Respawn Chamber",
                zoneId = "death_realm",
                biome = Biome.DUNGEON,
                description = "A dark chamber where you wake after each death, slightly stronger than before.",
                danger = 1,
                connections = listOf("first_trial"),
                features = listOf("Death Counter", "Power Archive", "Memory Well"),
                lore = "Each death teaches you something new. Each respawn makes you more dangerous."
            )

            SystemType.DUNGEON_DELVE -> Location(
                id = "dungeon_entrance",
                name = "Dungeon Entrance",
                zoneId = "first_floor",
                biome = Biome.DUNGEON,
                description = "The yawning mouth of the dungeon beckons. Many enter. Few return.",
                danger = 2,
                connections = listOf("first_corridor"),
                features = listOf("Ancient Door", "Warning Signs", "Campfire"),
                lore = "This dungeon has claimed countless lives. Will you be different?"
            )

            SystemType.ARCANE_ACADEMY -> Location(
                id = "academy_courtyard",
                name = "Academy Courtyard",
                zoneId = "academy_grounds",
                biome = Biome.SETTLEMENT,
                description = "The grand courtyard of the Arcane Academy, where aspiring mages begin their studies.",
                danger = 0,
                connections = listOf("library", "practice_grounds"),
                features = listOf("Fountain of Mana", "Notice Board", "Training Circle"),
                lore = "The Academy has stood for a thousand years, training the greatest mages in the realm."
            )

            SystemType.TABLETOP_CLASSIC -> Location(
                id = "tavern_common_room",
                name = "The Prancing Pony",
                zoneId = "starting_town",
                biome = Biome.SETTLEMENT,
                description = "A cozy tavern in a small town. The perfect place for adventurers to meet.",
                danger = 0,
                connections = listOf("town_square", "forest_road"),
                features = listOf("Bar", "Quest Board", "Innkeeper"),
                lore = "Many great adventures have begun in this humble tavern."
            )

            SystemType.EPIC_JOURNEY -> Location(
                id = "shire_home",
                name = "Home in the Shire",
                zoneId = "shire",
                biome = Biome.FOREST,
                description = "Your peaceful home in the Shire. But peace may soon be shattered.",
                danger = 0,
                connections = listOf("shire_road"),
                features = listOf("Cozy Hearth", "Garden", "Round Door"),
                lore = "The Shire has been peaceful for generations. Perhaps too peaceful."
            )

            SystemType.HERO_AWAKENING -> Location(
                id = "city_street",
                name = "City Street",
                zoneId = "downtown",
                biome = Biome.SETTLEMENT,
                description = "An ordinary city street on what seems like an ordinary day. But something is about to change.",
                danger = 1,
                connections = listOf("alley", "main_street"),
                features = listOf("Crowds", "Buildings", "Traffic"),
                lore = "Heroes are not born in glory. They are forged in moments of crisis."
            )
        }
    }

    /**
     * Get debug view of a game's state.
     */
    suspend fun getDebugView(game: Game): String {
        val gameImpl = game as? GameImpl ?: throw IllegalArgumentException("Game must be a GameImpl instance")
        val gameState = gameImpl.getCurrentState()

        val agentRepository = com.rpgenerator.core.persistence.AgentRepository(database)
        val debugService = com.rpgenerator.core.debug.SimpleGameDebugService(database, agentRepository)

        return debugService.generateDebugText(gameState)
    }

    /**
     * Close the database connection.
     * Should be called when the client is no longer needed.
     */
    fun close() {
        driver.close()
    }
}
