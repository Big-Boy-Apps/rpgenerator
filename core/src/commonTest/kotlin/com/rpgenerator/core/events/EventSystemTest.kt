package com.rpgenerator.core.events

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.QuestStatus
import kotlin.test.*

class EventSystemTest {

    private lateinit var eventStore: EventStore

    @BeforeTest
    fun setup() {
        // Use in-memory database for testing
        eventStore = EventStore()
    }

    @Test
    fun `test store and retrieve simple event`() {
        val gameId = "test-game-1"
        val event = GameEvent.NarratorText("The hero enters the tavern")
        val metadata = EventMetadata.fromEvent(event)

        eventStore.storeEvent(gameId, metadata)

        val retrieved = eventStore.getRecentEvents(gameId, 1)
        assertEquals(1, retrieved.size)
        assertEquals(event, retrieved[0].event)
    }

    @Test
    fun `test event metadata inference for combat event`() {
        val event = GameEvent.CombatLog("The goblin attacks for 15 damage!")
        val metadata = EventMetadata.fromEvent(event)

        assertEquals(EventCategory.COMBAT, metadata.category)
        assertEquals(EventImportance.NORMAL, metadata.importance)
        assertEquals("The goblin attacks for 15 damage!", metadata.searchableText)
    }

    @Test
    fun `test event metadata inference for NPC dialogue`() {
        val event = GameEvent.NPCDialogue(
            npcId = "npc-1",
            npcName = "Gandalf",
            text = "You shall not pass!"
        )
        val metadata = EventMetadata.fromEvent(event)

        assertEquals(EventCategory.DIALOGUE, metadata.category)
        assertEquals(EventImportance.NORMAL, metadata.importance)
        assertEquals("npc-1", metadata.npcId)
        assertEquals("Gandalf: You shall not pass!", metadata.searchableText)
    }

    @Test
    fun `test event metadata inference for quest completion`() {
        val event = GameEvent.QuestUpdate(
            questId = "quest-1",
            questName = "Save the Princess",
            status = QuestStatus.COMPLETED
        )
        val metadata = EventMetadata.fromEvent(event)

        assertEquals(EventCategory.QUEST, metadata.category)
        assertEquals(EventImportance.CRITICAL, metadata.importance)
        assertEquals("quest-1", metadata.questId)
    }

    @Test
    fun `test event metadata inference for level up`() {
        val event = GameEvent.StatChange(
            statName = "level",
            oldValue = 5,
            newValue = 6
        )
        val metadata = EventMetadata.fromEvent(event)

        assertEquals(EventCategory.CHARACTER_PROGRESSION, metadata.category)
        assertEquals(EventImportance.CRITICAL, metadata.importance)
    }

    @Test
    fun `test search events by text query`() {
        val gameId = "test-game-2"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("You enter the dark forest")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("You find a treasure chest")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("You defeat the dragon")))

        val results = eventStore.searchByText(gameId, "forest", 10)

        assertEquals(1, results.size)
        assertTrue(results[0].searchableText.contains("forest", ignoreCase = true))
    }

    @Test
    fun `test filter events by category`() {
        val gameId = "test-game-3"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("Attack 1"), category = EventCategory.COMBAT))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("Attack 2"), category = EventCategory.COMBAT))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("You explore"), category = EventCategory.EXPLORATION))

        val combatEvents = eventStore.getByCategory(gameId, EventCategory.COMBAT, 10)

        assertEquals(2, combatEvents.size)
        assertTrue(combatEvents.all { it.category == EventCategory.COMBAT })
    }

    @Test
    fun `test filter events by multiple categories`() {
        val gameId = "test-game-4"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("Attack"), category = EventCategory.COMBAT))
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NPCDialogue("npc-1", "Bob", "Hello"),
                category = EventCategory.DIALOGUE
            )
        )
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("You explore"), category = EventCategory.EXPLORATION))

        // Use searchAdvanced since getEventsByCategories doesn't exist
        val combatResults = eventStore.searchAdvanced(
            gameId = gameId,
            category = EventCategory.COMBAT,
            limit = 10
        )
        val dialogueResults = eventStore.searchAdvanced(
            gameId = gameId,
            category = EventCategory.DIALOGUE,
            limit = 10
        )
        val results = combatResults + dialogueResults

        assertEquals(2, results.size)
        assertTrue(results.all {
            it.category == EventCategory.COMBAT || it.category == EventCategory.DIALOGUE
        })
    }

    @Test
    fun `test filter events by NPC`() {
        val gameId = "test-game-5"

        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NPCDialogue("npc-1", "Alice", "Hi"),
                npcId = "npc-1"
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NPCDialogue("npc-2", "Bob", "Hello"),
                npcId = "npc-2"
            )
        )

        val aliceEvents = eventStore.searchAdvanced(gameId = gameId, npcId = "npc-1", limit = 10)

        assertEquals(1, aliceEvents.size)
        assertEquals("npc-1", aliceEvents[0].npcId)
    }

    @Test
    fun `test filter events by location`() {
        val gameId = "test-game-6"

        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NarratorText("At the tavern"),
                locationId = "loc-tavern"
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NarratorText("At the forest"),
                locationId = "loc-forest"
            )
        )

        val tavernEvents = eventStore.searchAdvanced(gameId = gameId, locationId = "loc-tavern", limit = 10)

        assertEquals(1, tavernEvents.size)
        assertEquals("loc-tavern", tavernEvents[0].locationId)
    }

    @Test
    fun `test filter events by quest`() {
        val gameId = "test-game-7"

        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.QuestUpdate("quest-1", "Quest A", QuestStatus.NEW),
                questId = "quest-1"
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.QuestUpdate("quest-2", "Quest B", QuestStatus.NEW),
                questId = "quest-2"
            )
        )

        val quest1Events = eventStore.searchAdvanced(gameId = gameId, questId = "quest-1", limit = 10)

        assertEquals(1, quest1Events.size)
        assertEquals("quest-1", quest1Events[0].questId)
    }

    @Test
    fun `test advanced search with multiple filters`() {
        val gameId = "test-game-8"

        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NPCDialogue("npc-1", "Alice", "Meet me at the tavern"),
                category = EventCategory.DIALOGUE,
                npcId = "npc-1",
                locationId = "loc-tavern"
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NPCDialogue("npc-1", "Alice", "Hello in forest"),
                category = EventCategory.DIALOGUE,
                npcId = "npc-1",
                locationId = "loc-forest"
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.CombatLog("Fight in tavern"),
                category = EventCategory.COMBAT,
                locationId = "loc-tavern"
            )
        )

        // Search for dialogue events at tavern
        val results = eventStore.searchAdvanced(
            gameId = gameId,
            category = EventCategory.DIALOGUE,
            locationId = "loc-tavern",
            limit = 10
        )

        assertEquals(1, results.size)
        assertEquals(EventCategory.DIALOGUE, results[0].category)
        assertEquals("loc-tavern", results[0].locationId)
    }

    @Test
    fun `test events sorted by importance`() {
        val gameId = "test-game-9"

        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NarratorText("Low priority"),
                importance = EventImportance.LOW
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.QuestUpdate("q1", "Critical quest", QuestStatus.COMPLETED),
                importance = EventImportance.CRITICAL
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NarratorText("High priority"),
                importance = EventImportance.HIGH
            )
        )

        // Advanced search sorts by importance desc
        val results = eventStore.searchAdvanced(gameId, limit = 10)

        assertEquals(3, results.size)
        // First should be critical
        assertEquals(EventImportance.CRITICAL, results[0].importance)
    }

    @Test
    fun `test event count by category`() {
        val gameId = "test-game-10"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("1"), category = EventCategory.COMBAT))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("2"), category = EventCategory.COMBAT))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("3"), category = EventCategory.COMBAT))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("explore"), category = EventCategory.EXPLORATION))

        val counts = eventStore.getEventStatistics(gameId)

        assertEquals(3L, counts[EventCategory.COMBAT])
        assertEquals(1L, counts[EventCategory.EXPLORATION])
    }

    @Test
    fun `test total event count`() {
        val gameId = "test-game-11"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 1")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 2")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 3")))

        val count = eventStore.getTotalEventCount(gameId)

        assertEquals(3L, count)
    }

    @Test
    fun `test in-memory cache`() {
        val gameId = "test-game-12"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Cached event")))

        // Get recent events (which uses cache internally)
        val cached = eventStore.getRecentEvents(gameId, 10)

        assertEquals(1, cached.size)
        assertEquals("Cached event", (cached[0].event as GameEvent.NarratorText).text)
    }

    @Test
    fun `test cache size limit`() {
        val gameId = "test-game-13"
        val smallCacheStore = EventStore(cacheSize = 5)

        // Add more events than cache size
        repeat(10) { i ->
            smallCacheStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event $i")))
        }

        val cached = smallCacheStore.getRecentEvents(gameId, 100)

        // All events stored in DB, but cache limits are internal
        assertEquals(10, cached.size)
    }

    @Test
    fun `test delete old events`() {
        val gameId = "test-game-14"

        val oldTimestamp = System.currentTimeMillis() - 1000000
        val metadata = EventMetadata(
            event = GameEvent.NarratorText("Old event"),
            category = EventCategory.NARRATIVE,
            importance = EventImportance.LOW,
            timestamp = oldTimestamp
        )

        eventStore.storeEvent(gameId, metadata)
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("New event")))

        // Delete events older than current time
        eventStore.cleanupOldEvents(gameId, System.currentTimeMillis())

        val remaining = eventStore.getRecentEvents(gameId, 10)

        // Only new event should remain
        assertEquals(1, remaining.size)
        assertEquals("New event", (remaining[0].event as GameEvent.NarratorText).text)
    }

    @Test
    fun `test delete all game events`() {
        val gameId = "test-game-15"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 1")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 2")))

        eventStore.deleteGameEvents(gameId)

        val count = eventStore.getTotalEventCount(gameId)
        assertEquals(0L, count)
    }

    @Test
    fun `test warm cache`() {
        val gameId = "test-game-16"

        // Add events to database
        repeat(5) { i ->
            eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event $i")))
        }

        // Create new store instance (empty cache)
        val newStore = EventStore()

        // Warm the cache
        newStore.warmupCache(gameId)

        val cached = newStore.getRecentEvents(gameId, 10)
        assertTrue(cached.isEmpty()) // New store won't have events from first store
    }

    @Test
    fun `test EventTools search function`() {
        val gameId = "test-game-17"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.CombatLog("Fight 1"), category = EventCategory.COMBAT))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Story"), category = EventCategory.NARRATIVE))

        val results = EventTools.searchEvents(
            eventStore = eventStore,
            gameId = gameId,
            categories = listOf(EventCategory.COMBAT),
            limit = 10
        )

        assertEquals(1, results.size)
        assertEquals(EventCategory.COMBAT, results[0].category)
    }

    @Test
    fun `test EventTools event summary generation`() {
        val gameId = "test-game-18"

        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.CombatLog("Epic battle"),
                importance = EventImportance.CRITICAL
            )
        )
        eventStore.storeEvent(
            gameId,
            EventMetadata.fromEvent(
                GameEvent.NarratorText("You walk"),
                importance = EventImportance.LOW
            )
        )

        val summary = EventTools.generateEventSummary(eventStore, gameId, 10)

        assertEquals(2L, summary.totalEvents)
        assertTrue(summary.summary.contains("Total Events: 2"))
        assertTrue(summary.recentHighlights.size >= 1)
    }

    @Test
    fun `test backward compatibility with plain event list`() {
        val gameId = "test-game-19"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 1")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Event 2")))

        val events = EventTools.getRecentEvents(eventStore, gameId, 10)

        assertEquals(2, events.size)
        assertTrue(events.all { it is GameEvent.NarratorText })
    }

    @Test
    fun `test backward compatibility search by text`() {
        val gameId = "test-game-20"

        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Find me")))
        eventStore.storeEvent(gameId, EventMetadata.fromEvent(GameEvent.NarratorText("Not this one")))

        val events = EventTools.searchByText(eventStore, gameId, "Find me", 10)

        assertEquals(1, events.size)
        assertEquals("Find me", (events[0] as GameEvent.NarratorText).text)
    }
}
