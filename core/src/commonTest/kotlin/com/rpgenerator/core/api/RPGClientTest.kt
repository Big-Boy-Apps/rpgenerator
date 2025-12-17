package com.rpgenerator.core.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*

class RPGClientTest {

    private lateinit var dbFile: File
    private lateinit var client: RPGClient
    private lateinit var mockLlm: LLMInterface

    @BeforeTest
    fun setup() {
        // Create a temporary database file for testing
        dbFile = File.createTempFile("test_rpg", ".db")
        dbFile.deleteOnExit()

        client = RPGClient(dbFile.absolutePath)

        // Create a mock LLM implementation
        mockLlm = object : LLMInterface {
            override fun startAgent(systemPrompt: String): AgentStream {
                return object : AgentStream {
                    override suspend fun sendMessage(message: String): Flow<String> {
                        return flowOf("Test", " ", "stream")
                    }
                }
            }
        }
    }

    @AfterTest
    fun cleanup() {
        client.close()
        dbFile.delete()
    }

    @Test
    fun `getGames returns empty list initially`() {
        val games = client.getGames()
        assertTrue(games.isEmpty(), "Should have no games initially")
    }

    @Test
    fun `startGame creates a new game`() = runTest {
        val config = GameConfig(
            systemType = SystemType.SYSTEM_INTEGRATION,
            difficulty = Difficulty.NORMAL,
            characterCreation = CharacterCreationOptions(name = "TestPlayer")
        )

        val game = client.startGame(config, mockLlm)
        assertNotNull(game, "Game should be created")

        val state = game.getState()
        assertEquals(1, state.playerStats.level, "Starting level should be 1")
        assertEquals("Tutorial Zone", state.location, "Should start in tutorial zone")
    }

    @Test
    fun `startGame saves game to database`() = runTest {
        val config = GameConfig(
            systemType = SystemType.CULTIVATION_PATH,
            difficulty = Difficulty.HARD,
            characterCreation = CharacterCreationOptions(name = "CultivatorTest")
        )

        client.startGame(config, mockLlm)

        val games = client.getGames()
        assertEquals(1, games.size, "Should have 1 saved game")

        val savedGame = games.first()
        assertEquals(SystemType.CULTIVATION_PATH, savedGame.systemType)
        assertEquals(Difficulty.HARD, savedGame.difficulty)
        assertEquals("CultivatorTest", savedGame.playerName)
        assertEquals(1, savedGame.level)
    }

    @Test
    fun `resumeGame loads existing game`() = runTest {
        // Create a game first
        val config = GameConfig(
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL,
            characterCreation = CharacterCreationOptions(name = "Gandalf")
        )

        val originalGame = client.startGame(config, mockLlm)
        originalGame.save()

        // Get the saved game info
        val games = client.getGames()
        assertEquals(1, games.size)
        val gameInfo = games.first()

        // Resume the game
        val resumedGame = client.resumeGame(gameInfo, mockLlm)
        assertNotNull(resumedGame, "Should resume game")

        val state = resumedGame.getState()
        assertEquals(1, state.playerStats.level, "Level should be preserved")
    }

    @Test
    fun `multiple games can be created`() = runTest {
        val configs = listOf(
            GameConfig(SystemType.SYSTEM_INTEGRATION, Difficulty.NORMAL, CharacterCreationOptions(name = "Player1")),
            GameConfig(SystemType.CULTIVATION_PATH, Difficulty.HARD, CharacterCreationOptions(name = "Player2")),
            GameConfig(SystemType.DUNGEON_DELVE, Difficulty.EASY, CharacterCreationOptions(name = "Player3"))
        )

        configs.forEach { config ->
            client.startGame(config, mockLlm)
        }

        val games = client.getGames()
        assertEquals(3, games.size, "Should have 3 saved games")

        val playerNames = games.map { it.playerName }.toSet()
        assertEquals(setOf("Player1", "Player2", "Player3"), playerNames)
    }

    @Test
    fun `games are sorted by last played time`() = runTest {
        val config1 = GameConfig(
            SystemType.SYSTEM_INTEGRATION,
            characterCreation = CharacterCreationOptions(name = "First")
        )
        val game1 = client.startGame(config1, mockLlm)
        game1.save()

        // Sleep to ensure different timestamps (lastPlayedAt is in seconds)
        Thread.sleep(1100)

        val config2 = GameConfig(
            SystemType.CULTIVATION_PATH,
            characterCreation = CharacterCreationOptions(name = "Second")
        )
        val game2 = client.startGame(config2, mockLlm)
        game2.save()

        val games = client.getGames()
        assertEquals(2, games.size)

        // Most recently played should be first
        assertEquals("Second", games[0].playerName)
        assertEquals("First", games[1].playerName)
    }

    @Test
    fun `different system types create appropriate starting locations`() = runTest {
        val systemLocations = mapOf(
            SystemType.SYSTEM_INTEGRATION to "Tutorial Zone",
            SystemType.CULTIVATION_PATH to "Sect Entrance",
            SystemType.DEATH_LOOP to "Respawn Chamber",
            SystemType.DUNGEON_DELVE to "Dungeon Entrance",
            SystemType.ARCANE_ACADEMY to "Academy Courtyard",
            SystemType.TABLETOP_CLASSIC to "The Prancing Pony",
            SystemType.EPIC_JOURNEY to "Home in the Shire",
            SystemType.HERO_AWAKENING to "City Street"
        )

        for ((systemType, expectedLocation) in systemLocations) {
            val config = GameConfig(systemType = systemType)
            val game = client.startGame(config, mockLlm)
            val state = game.getState()

            assertEquals(
                expectedLocation,
                state.location,
                "System type $systemType should start at $expectedLocation"
            )

            // Clean up for next iteration
            client.close()
            dbFile.delete()
            dbFile = File.createTempFile("test_rpg", ".db")
            dbFile.deleteOnExit()
            client = RPGClient(dbFile.absolutePath)
        }
    }

    @Test
    fun `initial character stats are set correctly`() = runTest {
        val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
        val game = client.startGame(config, mockLlm)
        val state = game.getState()

        assertEquals(1, state.playerStats.level)
        assertEquals(0L, state.playerStats.experience)
        assertTrue(state.playerStats.health > 0, "Health should be positive")
        assertTrue(state.playerStats.maxHealth > 0, "Max health should be positive")

        // Check that all base stats are initialized
        assertTrue(state.playerStats.stats.containsKey("strength"))
        assertTrue(state.playerStats.stats.containsKey("dexterity"))
        assertTrue(state.playerStats.stats.containsKey("constitution"))
        assertTrue(state.playerStats.stats.containsKey("intelligence"))
        assertTrue(state.playerStats.stats.containsKey("wisdom"))
        assertTrue(state.playerStats.stats.containsKey("charisma"))
    }
}
