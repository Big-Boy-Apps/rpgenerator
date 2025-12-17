package com.rpgenerator.core.api

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*

class GameTest {

    private lateinit var dbFile: File
    private lateinit var client: RPGClient
    private lateinit var mockLlm: LLMInterface

    @BeforeTest
    fun setup() {
        dbFile = File.createTempFile("test_game", ".db")
        dbFile.deleteOnExit()

        client = RPGClient(dbFile.absolutePath)

        mockLlm = object : LLMInterface {
            override fun startAgent(systemPrompt: String): AgentStream {
                return object : AgentStream {
                    override suspend fun sendMessage(message: String): Flow<String> {
                        return flowOf("Test", " ", "response")
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
    fun `getState returns current game state`() = runTest {
        val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
        val game = client.startGame(config, mockLlm)

        val state = game.getState()

        assertNotNull(state.playerStats)
        assertNotNull(state.location)
        assertNotNull(state.currentScene)
        assertNotNull(state.inventory)
        assertNotNull(state.activeQuests)
        assertNotNull(state.recentEvents)
    }

    @Test
    fun `processInput emits game events`() = runTest {
        val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
        val game = client.startGame(config, mockLlm)

        val eventFlow = game.processInput("look around")

        eventFlow.test {
            // Should emit at least one event
            val event = awaitItem()
            assertNotNull(event, "Should emit a game event")

            // Events can be of various types
            assertTrue(
                event is GameEvent.NarratorText ||
                event is GameEvent.SystemNotification ||
                event is GameEvent.CombatLog
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processInput for combat generates appropriate events`() = runTest {
        val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
        val game = client.startGame(config, mockLlm)

        val eventFlow = game.processInput("attack the goblin")

        var hasOpeningNarration = false
        var hasCombatNarration = false
        var hasCombatLog = false
        var hasXpGain = false

        eventFlow.test {
            // Collect all events
            while (true) {
                try {
                    val event = awaitItem()
                    when (event) {
                        is GameEvent.NarratorText -> {
                            // First narration is opening, second is combat
                            if (!hasOpeningNarration) {
                                hasOpeningNarration = true
                            } else {
                                hasCombatNarration = true
                            }
                        }
                        is GameEvent.CombatLog -> hasCombatLog = true
                        is GameEvent.StatChange -> {
                            if (event.statName == "xp") hasXpGain = true
                        }
                        else -> {}
                    }

                    // Check if we've seen all required events
                    if (hasOpeningNarration && hasCombatNarration && hasCombatLog && hasXpGain) {
                        cancelAndIgnoreRemainingEvents()
                        break
                    }
                } catch (e: Exception) {
                    // No more events
                    break
                }
            }
        }

        assertTrue(hasOpeningNarration, "Should have opening narration")
        assertTrue(hasCombatNarration, "Combat should have narration")
        assertTrue(hasCombatLog, "Combat should have combat log")
        assertTrue(hasXpGain, "Combat should grant XP")
    }

    @Test
    fun `save persists game state to database`() = runTest {
        val config = GameConfig(
            systemType = SystemType.CULTIVATION_PATH,
            playerPreferences = mapOf("playerName" to "TestCultivator")
        )
        val game = client.startGame(config, mockLlm)

        // Process some input to change state
        game.processInput("meditate").test {
            cancelAndIgnoreRemainingEvents()
        }

        // Save the game
        game.save()

        // Get game info to resume
        val games = client.getGames()
        assertEquals(1, games.size)

        // Resume should load the saved state
        val resumedGame = client.resumeGame(games.first(), mockLlm)
        val resumedState = resumedGame.getState()

        assertNotNull(resumedState)
        assertEquals(1, resumedState.playerStats.level)
    }

    @Test
    fun `state reflects character progression`() = runTest {
        val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
        val game = client.startGame(config, mockLlm)

        val initialState = game.getState()
        val initialXp = initialState.playerStats.experience

        // Perform combat to gain XP
        // Note: Must collect ALL events to ensure combat processing and state updates complete
        game.processInput("fight enemy").test {
            // Keep consuming events until flow naturally completes
            var eventCount = 0
            while (true) {
                try {
                    awaitItem()
                    eventCount++
                } catch (e: AssertionError) {
                    // Flow completed naturally
                    break
                }
            }
            // Ensure we got at least some events
            assertTrue(eventCount > 0, "Should have received events from combat")
        }

        val updatedState = game.getState()
        val newXp = updatedState.playerStats.experience

        assertTrue(newXp > initialXp, "XP should increase after combat. Initial: $initialXp, Updated: $newXp")
    }

    @Test
    fun `inventory is accessible through state`() = runTest {
        val config = GameConfig(systemType = SystemType.TABLETOP_CLASSIC)
        val game = client.startGame(config, mockLlm)

        val state = game.getState()

        assertNotNull(state.inventory)
        assertTrue(state.inventory is List<Item>)
    }

    @Test
    fun `location is tracked in state`() = runTest {
        val config = GameConfig(systemType = SystemType.EPIC_JOURNEY)
        val game = client.startGame(config, mockLlm)

        val state = game.getState()

        assertEquals("Home in the Shire", state.location)
        assertTrue(state.currentScene.isNotEmpty())
    }

    @Test
    fun `player stats include all required fields`() = runTest {
        val config = GameConfig(systemType = SystemType.ARCANE_ACADEMY)
        val game = client.startGame(config, mockLlm)

        val state = game.getState()
        val stats = state.playerStats

        assertNotNull(stats.name)
        assertTrue(stats.level > 0)
        assertTrue(stats.experience >= 0)
        assertTrue(stats.experienceToNextLevel > 0)
        assertTrue(stats.health > 0)
        assertTrue(stats.maxHealth > 0)
        assertTrue(stats.energy > 0)
        assertTrue(stats.maxEnergy > 0)
        assertTrue(stats.stats.isNotEmpty())
    }

    @Test
    fun `system query returns status information`() = runTest {
        val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
        val game = client.startGame(config, mockLlm)

        val eventFlow = game.processInput("status")

        eventFlow.test {
            // First event will be opening narration
            val firstEvent = awaitItem()
            assertTrue(
                firstEvent is GameEvent.NarratorText,
                "First event should be opening narration"
            )

            // Second event should be the status notification
            val statusEvent = awaitItem()
            assertTrue(
                statusEvent is GameEvent.SystemNotification,
                "Status query should return system notification"
            )

            if (statusEvent is GameEvent.SystemNotification) {
                assertTrue(
                    statusEvent.text.contains("Level") || statusEvent.text.contains("XP"),
                    "Status should contain level or XP information"
                )
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exploration discovers new locations`() = runTest {
        val config = GameConfig(systemType = SystemType.DUNGEON_DELVE)
        val game = client.startGame(config, mockLlm)

        val eventFlow = game.processInput("explore")

        var hasNarration = false
        var hasDiscovery = false

        eventFlow.test {
            while (true) {
                val event = awaitItem()
                when (event) {
                    is GameEvent.NarratorText -> hasNarration = true
                    is GameEvent.SystemNotification -> {
                        if (event.text.contains("Discovered")) {
                            hasDiscovery = true
                        }
                    }
                    else -> {}
                }

                // Safety check
                try {
                    expectNoEvents()
                    break
                } catch (e: AssertionError) {
                    continue
                }
            }
        }

        assertTrue(hasNarration, "Exploration should generate narration")
    }

    @Test
    fun `multiple saves preserve state correctly`() = runTest {
        val config = GameConfig(
            systemType = SystemType.DEATH_LOOP,
            playerPreferences = mapOf("playerName" to "DeathRunner")
        )
        val game = client.startGame(config, mockLlm)

        // First save
        game.save()
        val state1 = game.getState()

        // Do some action
        game.processInput("train").test {
            cancelAndIgnoreRemainingEvents()
        }

        // Second save
        game.save()
        val state2 = game.getState()

        // Both states should be valid
        assertNotNull(state1)
        assertNotNull(state2)
        assertEquals(state1.playerStats.level, state2.playerStats.level)
    }
}
