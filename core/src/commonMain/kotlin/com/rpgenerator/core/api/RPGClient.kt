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
}
