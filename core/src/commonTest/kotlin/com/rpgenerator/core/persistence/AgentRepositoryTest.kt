package com.rpgenerator.core.persistence

import com.rpgenerator.core.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for AgentRepository.
 *
 * Tests:
 * - Memory persistence (save/load)
 * - Action logging
 * - Memory consolidation
 * - Cleanup operations
 */
class AgentRepositoryTest {

    private lateinit var database: GameDatabase
    private lateinit var repository: AgentRepository
    private val testGameId = "test_game_123"
    private val testAgentId = "test_agent"

    @BeforeEach
    fun setup() {
        // Create in-memory database for testing
        database = createInMemoryDatabase()
        repository = AgentRepository(database)
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `test save and load agent memory`() = runTest {
        // Create test memory
        val memory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "You are a test agent"),
                AgentMessage(AgentRole.USER, "Hello"),
                AgentMessage(AgentRole.ASSISTANT, "Hi there!")
            ),
            consolidatedContext = null
        )

        // Save memory
        repository.saveAgentMemory(memory)

        // Load memory
        val loaded = repository.loadAgentMemory(testAgentId, testGameId)

        // Verify
        assertNotNull(loaded)
        assertEquals(memory.agentId, loaded.agentId)
        assertEquals(memory.gameId, loaded.gameId)
        assertEquals(3, loaded.messages.size)
        assertEquals("Hello", loaded.messages[1].content)
    }

    @Test
    fun `test load non-existent memory returns null`() = runTest {
        val loaded = repository.loadAgentMemory("non_existent", testGameId)
        assertNull(loaded)
    }

    @Test
    fun `test update existing memory`() = runTest {
        // Create initial memory
        val memory1 = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "System prompt")
            )
        )
        repository.saveAgentMemory(memory1)

        // Update with more messages
        val memory2 = memory1.copy(
            messages = memory1.messages + AgentMessage(AgentRole.USER, "New message")
        )
        repository.saveAgentMemory(memory2)

        // Load and verify
        val loaded = repository.loadAgentMemory(testAgentId, testGameId)
        assertNotNull(loaded)
        assertEquals(2, loaded.messages.size)
    }

    @Test
    fun `test save and retrieve agent action`() = runTest {
        val action = AgentAction(
            agentId = testAgentId,
            gameId = testGameId,
            actionType = AgentActionType.NPC_CREATED,
            actionData = """{"npcId": "npc_001", "name": "Test NPC"}""",
            reasoning = "Player needed a merchant",
            context = AgentActionContext(
                playerLevel = 5,
                npcId = "npc_001",
                locationId = "loc_001"
            )
        )

        // Save action
        repository.saveAgentAction(action)

        // Retrieve actions
        val actions = repository.getAgentActions(testAgentId, testGameId, limit = 10)

        // Verify
        assertEquals(1, actions.size)
        val retrieved = actions[0]
        assertEquals(testAgentId, retrieved.agentId)
        assertEquals(AgentActionType.NPC_CREATED, retrieved.actionType)
        assertEquals("Player needed a merchant", retrieved.reasoning)
        assertEquals(5, retrieved.context.playerLevel)
        assertEquals("npc_001", retrieved.context.npcId)
    }

    @Test
    fun `test get actions by type`() = runTest {
        // Save multiple actions of different types
        repository.saveAgentAction(
            AgentAction(
                agentId = testAgentId,
                gameId = testGameId,
                actionType = AgentActionType.NPC_CREATED,
                actionData = "{}",
                reasoning = "Test 1",
                context = AgentActionContext(playerLevel = 1)
            )
        )

        repository.saveAgentAction(
            AgentAction(
                agentId = testAgentId,
                gameId = testGameId,
                actionType = AgentActionType.QUEST_GENERATED,
                actionData = "{}",
                reasoning = "Test 2",
                context = AgentActionContext(playerLevel = 1)
            )
        )

        repository.saveAgentAction(
            AgentAction(
                agentId = testAgentId,
                gameId = testGameId,
                actionType = AgentActionType.NPC_CREATED,
                actionData = "{}",
                reasoning = "Test 3",
                context = AgentActionContext(playerLevel = 1)
            )
        )

        // Get NPC_CREATED actions
        val npcActions = repository.getAgentActionsByType(
            testAgentId,
            testGameId,
            AgentActionType.NPC_CREATED,
            limit = 10
        )

        // Verify
        assertEquals(2, npcActions.size)
        assertTrue(npcActions.all { it.actionType == AgentActionType.NPC_CREATED })
    }

    @Test
    fun `test memory consolidation`() = runTest {
        // Create memory with many messages
        val messages = buildList {
            add(AgentMessage(AgentRole.SYSTEM, "System prompt"))
            repeat(30) { i ->
                add(AgentMessage(AgentRole.USER, "User message $i"))
                add(AgentMessage(AgentRole.ASSISTANT, "Assistant response $i"))
            }
        }

        val memory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = messages
        )
        repository.saveAgentMemory(memory)

        // Consolidate (keep last 10 messages)
        val summary = "Summary of messages 0-20"
        val result = repository.consolidateAgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            summary = summary,
            keepRecentCount = 10,
            playerLevel = 15,
            levelRangeStart = 1,
            levelRangeEnd = 15
        )

        // Verify consolidation result
        assertNotNull(result)
        assertEquals(61, result.messagesBefore) // 1 system + 60 user/assistant
        assertEquals(10, result.messagesAfter)
        assertTrue(result.tokensAfter < result.tokensBefore)

        // Verify updated memory
        val updated = repository.loadAgentMemory(testAgentId, testGameId)
        assertNotNull(updated)
        assertEquals(10, updated.messages.size)
        assertEquals(summary, updated.consolidatedContext)
        assertNotNull(updated.lastConsolidated)

        // Verify consolidation snapshot was saved
        val consolidations = repository.getConsolidationHistory(testAgentId, testGameId, limit = 10)
        assertEquals(1, consolidations.size)
        assertEquals(summary, consolidations[0].summary)
        assertEquals(ConsolidationType.PARTIAL, consolidations[0].consolidationType)
    }

    @Test
    fun `test get consolidation history`() = runTest {
        val memory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = List(50) { i ->
                AgentMessage(AgentRole.USER, "Message $i")
            }
        )
        repository.saveAgentMemory(memory)

        // Perform multiple consolidations
        repository.consolidateAgentMemory(
            testAgentId, testGameId,
            summary = "First consolidation",
            keepRecentCount = 20,
            playerLevel = 10,
            levelRangeStart = 1,
            levelRangeEnd = 10
        )

        // Add more messages and consolidate again
        val updated = repository.loadAgentMemory(testAgentId, testGameId)!!
        val moreMessages = updated.messages + List(30) { i ->
            AgentMessage(AgentRole.USER, "New message $i")
        }
        repository.saveAgentMemory(updated.copy(messages = moreMessages))

        repository.consolidateAgentMemory(
            testAgentId, testGameId,
            summary = "Second consolidation",
            keepRecentCount = 15,
            playerLevel = 20,
            levelRangeStart = 11,
            levelRangeEnd = 20
        )

        // Get consolidation history
        val history = repository.getConsolidationHistory(testAgentId, testGameId, limit = 10)

        // Verify
        assertEquals(2, history.size)
        assertEquals("Second consolidation", history[0].summary) // Most recent first
        assertEquals("First consolidation", history[1].summary)
    }

    @Test
    fun `test get latest consolidation`() = runTest {
        val memory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = List(50) { AgentMessage(AgentRole.USER, "Message") }
        )
        repository.saveAgentMemory(memory)

        // No consolidation yet
        val before = repository.getLatestConsolidation(testAgentId, testGameId)
        assertNull(before)

        // Consolidate
        repository.consolidateAgentMemory(
            testAgentId, testGameId,
            summary = "Latest summary",
            keepRecentCount = 10,
            playerLevel = 10,
            levelRangeStart = 1,
            levelRangeEnd = 10
        )

        // Get latest
        val latest = repository.getLatestConsolidation(testAgentId, testGameId)
        assertNotNull(latest)
        assertEquals("Latest summary", latest.summary)
    }

    @Test
    fun `test delete agent memory`() = runTest {
        val memory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = listOf(AgentMessage(AgentRole.SYSTEM, "Test"))
        )
        repository.saveAgentMemory(memory)

        // Verify exists
        assertNotNull(repository.loadAgentMemory(testAgentId, testGameId))

        // Delete
        repository.deleteAgentMemory(testAgentId, testGameId)

        // Verify deleted
        assertNull(repository.loadAgentMemory(testAgentId, testGameId))
    }

    @Test
    fun `test delete all agent data for game`() = runTest {
        // Create multiple agents for the same game
        val memory1 = AgentMemory(
            agentId = "agent_1",
            gameId = testGameId,
            messages = listOf(AgentMessage(AgentRole.SYSTEM, "Agent 1"))
        )
        val memory2 = AgentMemory(
            agentId = "agent_2",
            gameId = testGameId,
            messages = listOf(AgentMessage(AgentRole.SYSTEM, "Agent 2"))
        )

        repository.saveAgentMemory(memory1)
        repository.saveAgentMemory(memory2)

        // Save actions
        repository.saveAgentAction(
            AgentAction(
                agentId = "agent_1",
                gameId = testGameId,
                actionType = AgentActionType.AGENT_DECISION,
                actionData = "{}",
                reasoning = "Test",
                context = AgentActionContext(playerLevel = 1)
            )
        )

        // Verify data exists
        assertNotNull(repository.loadAgentMemory("agent_1", testGameId))
        assertNotNull(repository.loadAgentMemory("agent_2", testGameId))

        // Delete all data for game
        repository.deleteAllAgentDataForGame(testGameId)

        // Verify all deleted
        assertNull(repository.loadAgentMemory("agent_1", testGameId))
        assertNull(repository.loadAgentMemory("agent_2", testGameId))
        val actions = repository.getAllActionsForGame(testGameId)
        assertEquals(0, actions.size)
    }

    @Test
    fun `test token estimation`() {
        val memory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "A".repeat(100)), // ~25 tokens
                AgentMessage(AgentRole.USER, "B".repeat(200)),    // ~50 tokens
                AgentMessage(AgentRole.ASSISTANT, "C".repeat(400)) // ~100 tokens
            ),
            consolidatedContext = "D".repeat(1000) // ~250 tokens
        )

        val estimated = memory.estimateTokens()

        // Should be roughly 25 + 50 + 100 + 250 = 425 tokens
        assertTrue(estimated in 400..450, "Expected ~425 tokens, got $estimated")
    }

    @Test
    fun `test memory needs consolidation`() {
        // Small memory - doesn't need consolidation
        val smallMemory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, "Short message")
            )
        )
        assertFalse(smallMemory.needsConsolidation(tokenLimit = 50000))

        // Large memory - needs consolidation
        val largeMemory = AgentMemory(
            agentId = testAgentId,
            gameId = testGameId,
            messages = List(1000) { i ->
                AgentMessage(AgentRole.USER, "A".repeat(500)) // ~125 tokens each
            }
        )
        assertTrue(largeMemory.needsConsolidation(tokenLimit = 50000))
    }

    @Test
    fun `test get all agent memories for game`() = runTest {
        // Create memories for multiple agents
        repository.saveAgentMemory(
            AgentMemory(
                agentId = "game_master",
                gameId = testGameId,
                messages = listOf(AgentMessage(AgentRole.SYSTEM, "GM"))
            )
        )
        repository.saveAgentMemory(
            AgentMemory(
                agentId = "planner",
                gameId = testGameId,
                messages = listOf(AgentMessage(AgentRole.SYSTEM, "Planner"))
            )
        )
        repository.saveAgentMemory(
            AgentMemory(
                agentId = "npc_001",
                gameId = testGameId,
                messages = listOf(AgentMessage(AgentRole.SYSTEM, "NPC"))
            )
        )

        // Get all memories
        val memories = repository.getAllAgentMemories(testGameId)

        // Verify
        assertEquals(3, memories.size)
        val agentIds = memories.map { it.agentId }.toSet()
        assertTrue(agentIds.contains("game_master"))
        assertTrue(agentIds.contains("planner"))
        assertTrue(agentIds.contains("npc_001"))
    }

    /**
     * Helper to create in-memory SQLite database for testing.
     */
    private fun createInMemoryDatabase(): GameDatabase {
        val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
            url = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY
        )
        GameDatabase.Schema.create(driver)
        return GameDatabase(driver)
    }
}
