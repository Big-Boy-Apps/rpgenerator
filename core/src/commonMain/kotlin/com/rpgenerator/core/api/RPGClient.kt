package com.rpgenerator.core.api

import app.cash.sqldelight.db.SqlDriver
import com.rpgenerator.core.RPGClientImpl

/**
 * Main entry point for RPGenerator core library.
 * Manages game lifecycle and persistence.
 */
class RPGClient(
    driver: SqlDriver
) {
    private val impl: RPGClientImpl = RPGClientImpl(driver)

    /**
     * Get all saved games from local storage.
     */
    fun getGames(): List<GameInfo> {
        return impl.getGames()
    }

    /**
     * Start a new game with the given configuration.
     *
     * @param config Game configuration (system type, difficulty, preferences)
     * @param llm LLM provider for AI-powered game logic
     * @return Active game instance
     */
    suspend fun startGame(
        config: GameConfig,
        llm: LLMInterface
    ): Game {
        return impl.startGame(config, llm)
    }

    /**
     * Resume an existing game.
     *
     * @param gameInfo Game metadata from getGames()
     * @param llm LLM provider for AI-powered game logic
     * @return Active game instance
     */
    suspend fun resumeGame(
        gameInfo: GameInfo,
        llm: LLMInterface
    ): Game {
        return impl.resumeGame(gameInfo, llm)
    }

    /**
     * Get debug view of a game's state.
     * For development and debugging purposes.
     *
     * @param game The active game instance
     * @return Complete debug view with player, plot, agents, and world state
     */
    suspend fun getDebugView(game: Game): String {
        return impl.getDebugView(game)
    }

    /**
     * Close the database connection.
     * Should be called when the client is no longer needed.
     */
    fun close() {
        impl.close()
    }

    /**
     * Get recent events for a game.
     * For the debug dashboard.
     */
    fun getRecentEvents(game: Game, limit: Int): List<EventLogEntry> {
        return impl.getRecentEvents(game, limit)
    }

    /**
     * Execute a raw SQL query.
     * For the debug dashboard. Only SELECT queries are allowed.
     */
    fun executeRawQuery(sql: String): RawQueryResult {
        return impl.executeRawQuery(sql)
    }

    /**
     * Get data from a specific table.
     * For the debug dashboard.
     */
    fun getTableData(tableName: String, gameId: String?, limit: Int): RawQueryResult {
        return impl.getTableData(tableName, gameId, limit)
    }

    /**
     * Get plot threads for a game.
     * For the debug dashboard.
     */
    fun getPlotThreads(game: Game): List<PlotThreadEntry> {
        return impl.getPlotThreads(game)
    }

    /**
     * Get plot nodes for a game.
     * For the debug dashboard.
     */
    fun getPlotNodes(game: Game): List<PlotNodeEntry> {
        return impl.getPlotNodes(game)
    }

    /**
     * Get plot edges for a game.
     * For the debug dashboard.
     */
    fun getPlotEdges(game: Game): List<PlotEdgeEntry> {
        return impl.getPlotEdges(game)
    }
}

// Data classes for debug dashboard
data class EventLogEntry(
    val id: Int,
    val timestamp: Long,
    val eventType: String,
    val category: String,
    val importance: String,
    val searchableText: String,
    val npcId: String?,
    val locationId: String?,
    val questId: String?
)

data class RawQueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>
)

data class PlotThreadEntry(
    val id: String,
    val category: String,
    val priority: String,
    val status: String,
    val threadJson: String
)

data class PlotNodeEntry(
    val id: String,
    val threadId: String,
    val tier: Int,
    val sequence: Int,
    val beatType: String,
    val triggered: Boolean,
    val completed: Boolean,
    val abandoned: Boolean,
    val nodeJson: String
)

data class PlotEdgeEntry(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val edgeType: String,
    val weight: Double,
    val disabled: Boolean
)
