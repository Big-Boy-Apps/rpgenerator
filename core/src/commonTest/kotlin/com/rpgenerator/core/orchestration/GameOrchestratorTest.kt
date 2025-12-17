package com.rpgenerator.core.orchestration

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.test.MockLLMInterface
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameOrchestratorTest {

    @Test
    fun `combat flow emits correct events`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()

        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I attack the goblin").test {
            val event1 = awaitItem()
            assertTrue(event1 is GameEvent.NarratorText, "First event should be narration")

            val event2 = awaitItem()
            assertTrue(event2 is GameEvent.CombatLog, "Second event should be combat log")

            val event3 = awaitItem()
            assertTrue(event3 is GameEvent.StatChange, "Third event should be stat change")
            assertEquals("xp", event3.statName)

            // Combat may drop loot items and gold - consume all remaining events
            // These can include ItemGained events and SystemNotification for gold
            // Just consume remaining events without asserting on them since loot is random
            cancelAndConsumeRemainingEvents()
        }

        val finalState = orchestrator.getState()
        assertTrue(finalState.playerXP > 0, "XP should have increased")
    }

    @Test
    fun `exploration intent emits only narration`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I look around the forest").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.NarratorText, "Should only emit narration")
            awaitComplete()
        }
    }

    @Test
    fun `system query returns stats without narration`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState(playerLevel = 5, playerXP = 250L)
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("show my stats").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.SystemNotification, "Should emit system notification")
            val notification = event as GameEvent.SystemNotification
            assertTrue(notification.text.contains("Level 5"), "Should show level")
            assertTrue(notification.text.contains("250"), "Should show XP")
            awaitComplete()
        }
    }

    @Test
    fun `npc dialogue intent shows not implemented message`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I talk to the merchant").test {
            val event = awaitItem()
            // NPC dialogue now emits SystemNotification when NPC is not found
            // since "merchant" doesn't exist in the test game state
            assertTrue(event is GameEvent.SystemNotification, "Should emit system notification when NPC not found")
            val notification = event as GameEvent.SystemNotification
            assertTrue(notification.text.contains("no one named"), "Should indicate NPC not found")
            awaitComplete()
        }
    }

    @Test
    fun `level up occurs at 100 XP`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState(playerXP = 150L)
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I attack the goblin").test {
            val narration = awaitItem()
            assertTrue(narration is GameEvent.NarratorText, "First event should be narration")

            val combatLog = awaitItem()
            assertTrue(combatLog is GameEvent.CombatLog, "Second event should be combat log")

            val statChange = awaitItem()
            assertTrue(statChange is GameEvent.StatChange, "Third event should be stat change")

            val levelUpNotification = awaitItem()
            assertTrue(levelUpNotification is GameEvent.SystemNotification, "Should emit level up notification")
            assertTrue((levelUpNotification as GameEvent.SystemNotification).text.contains("Level up"))
            assertTrue(levelUpNotification.text.contains("level 2"))

            // Combat may drop loot items and gold after level up - consume all remaining events
            // Just consume remaining events without asserting on them since loot is random
            cancelAndConsumeRemainingEvents()
        }

        val finalState = orchestrator.getState()
        assertEquals(2, finalState.playerLevel, "Should be level 2")
    }

}
