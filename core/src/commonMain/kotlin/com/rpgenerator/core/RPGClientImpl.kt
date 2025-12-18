package com.rpgenerator.core

import app.cash.sqldelight.db.SqlDriver
import com.rpgenerator.core.api.*
import com.rpgenerator.core.character.CharacterCreationService
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.persistence.GameDatabase
import com.rpgenerator.core.persistence.GameRepository
import com.rpgenerator.core.persistence.PlotGraphRepository
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
    private val plotRepository: PlotGraphRepository

    init {
        // Create database schema if needed
        GameDatabase.Schema.create(driver)
        database = GameDatabase(driver)
        repository = GameRepository(database)
        plotRepository = PlotGraphRepository(database)
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
            plotRepository = plotRepository,
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
            plotRepository = plotRepository,
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

    /**
     * Get recent events for a game.
     */
    fun getRecentEvents(game: Game, limit: Int): List<EventLogEntry> {
        val events = database.gameQueries.selectRecentEvents(game.id, limit.toLong()).executeAsList()

        return events.map { event ->
            EventLogEntry(
                id = event.id.toInt(),
                timestamp = event.timestamp,
                eventType = event.eventType,
                category = event.category,
                importance = event.importance,
                searchableText = event.searchableText,
                npcId = event.npcId,
                locationId = event.locationId,
                questId = event.questId
            )
        }
    }

    /**
     * Execute a raw SQL query. Only SELECT queries are allowed.
     * Returns columns and rows as strings for display in the debug dashboard.
     * Note: Raw SQL execution is limited in SQLDelight - use table-specific endpoints instead.
     */
    fun executeRawQuery(sql: String): RawQueryResult {
        val trimmedSql = sql.trim().uppercase()
        if (!trimmedSql.startsWith("SELECT")) {
            throw IllegalArgumentException("Only SELECT queries are allowed")
        }

        // SQLDelight doesn't support arbitrary SQL execution easily
        // Return a message directing users to use the table-specific queries
        return RawQueryResult(
            columns = listOf("info"),
            rows = listOf(listOf("Use the table list on the left to browse data. Custom SQL queries are limited."))
        )
    }

    /**
     * Get data from a specific table.
     * Uses SQLDelight queries to fetch data safely.
     */
    fun getTableData(tableName: String, gameId: String?, limit: Int): RawQueryResult {
        return when (tableName) {
            "Game" -> {
                val games = if (gameId != null) {
                    database.gameQueries.selectById(gameId).executeAsOneOrNull()?.let { g -> listOf(g) } ?: emptyList()
                } else {
                    database.gameQueries.selectAll().executeAsList()
                }
                RawQueryResult(
                    columns = listOf("id", "playerName", "systemType", "level", "playtime", "lastPlayedAt", "createdAt"),
                    rows = games.take(limit).map { g -> listOf(g.id, g.playerName, g.systemType, g.level.toString(), g.playtime.toString(), g.lastPlayedAt.toString(), g.createdAt.toString()) }
                )
            }
            "GameEventLog" -> {
                if (gameId == null) {
                    return RawQueryResult(listOf("info"), listOf(listOf("Select a game to view event logs")))
                }
                val events = database.gameQueries.selectRecentEvents(gameId, limit.toLong()).executeAsList()
                RawQueryResult(
                    columns = listOf("id", "gameId", "timestamp", "eventType", "category", "importance", "searchableText"),
                    rows = events.map { e -> listOf(e.id.toString(), e.gameId, e.timestamp.toString(), e.eventType, e.category, e.importance, e.searchableText.take(100)) }
                )
            }
            "PlotThread" -> {
                if (gameId == null) {
                    return RawQueryResult(listOf("info"), listOf(listOf("Select a game to view plot threads")))
                }
                val threads = database.gameQueries.selectPlotThreadsByGame(gameId).executeAsList()
                RawQueryResult(
                    columns = listOf("id", "gameId", "category", "priority", "status"),
                    rows = threads.map { t -> listOf(t.id, t.gameId, t.category, t.priority, t.status) }
                )
            }
            "PlotNode" -> {
                if (gameId == null) {
                    return RawQueryResult(listOf("info"), listOf(listOf("Select a game to view plot nodes")))
                }
                val nodes = database.gameQueries.selectPlotNodesByGame(gameId).executeAsList()
                RawQueryResult(
                    columns = listOf("id", "threadId", "tier", "sequence", "beatType", "triggered", "completed"),
                    rows = nodes.take(limit).map { n -> listOf(n.id, n.threadId, n.tier.toString(), n.sequence.toString(), n.beatType, n.triggered.toString(), n.completed.toString()) }
                )
            }
            "PlotEdge" -> {
                if (gameId == null) {
                    return RawQueryResult(listOf("info"), listOf(listOf("Select a game to view plot edges")))
                }
                val edges = database.gameQueries.selectPlotEdgesByGame(gameId).executeAsList()
                RawQueryResult(
                    columns = listOf("id", "fromNodeId", "toNodeId", "edgeType", "weight", "disabled"),
                    rows = edges.take(limit).map { edge -> listOf(edge.id, edge.fromNodeId, edge.toNodeId, edge.edgeType, edge.weight.toString(), edge.disabled.toString()) }
                )
            }
            else -> {
                // For tables without specific queries, return info message
                RawQueryResult(
                    columns = listOf("info"),
                    rows = listOf(listOf("Table '$tableName' query not implemented. Available: Game, GameEventLog, PlotThread, PlotNode, PlotEdge"))
                )
            }
        }
    }

    /**
     * Get plot threads for a game.
     */
    fun getPlotThreads(game: Game): List<PlotThreadEntry> {
        val threads = database.gameQueries.selectPlotThreadsByGame(game.id).executeAsList()

        return threads.map { thread ->
            PlotThreadEntry(
                id = thread.id,
                category = thread.category,
                priority = thread.priority,
                status = thread.status,
                threadJson = thread.threadJson
            )
        }
    }

    /**
     * Get plot nodes for a game.
     */
    fun getPlotNodes(game: Game): List<PlotNodeEntry> {
        val nodes = database.gameQueries.selectPlotNodesByGame(game.id).executeAsList()

        return nodes.map { node ->
            PlotNodeEntry(
                id = node.id,
                threadId = node.threadId,
                tier = node.tier.toInt(),
                sequence = node.sequence.toInt(),
                beatType = node.beatType,
                triggered = node.triggered != 0L,
                completed = node.completed != 0L,
                abandoned = node.abandoned != 0L,
                nodeJson = node.nodeJson
            )
        }
    }

    /**
     * Get plot edges for a game.
     */
    fun getPlotEdges(game: Game): List<PlotEdgeEntry> {
        val edges = database.gameQueries.selectPlotEdgesByGame(game.id).executeAsList()

        return edges.map { edge ->
            PlotEdgeEntry(
                id = edge.id,
                fromNodeId = edge.fromNodeId,
                toNodeId = edge.toNodeId,
                edgeType = edge.edgeType,
                weight = edge.weight,
                disabled = edge.disabled != 0L
            )
        }
    }
}
