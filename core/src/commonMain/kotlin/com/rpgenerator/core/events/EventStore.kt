package com.rpgenerator.core.events

import app.cash.sqldelight.db.SqlDriver
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.persistence.GameDatabase
import com.rpgenerator.core.persistence.createInMemoryDriver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Event storage layer that manages persistence and caching of game events.
 * Uses SQLDelight for persistence with an in-memory cache for recent events.
 */
class EventStore(
    driver: SqlDriver? = null,
    private val cacheSize: Int = 100
) {
    private val database: GameDatabase
    private val queries get() = database.gameQueries

    // In-memory cache of recent events for fast access
    private val eventCache = mutableMapOf<String, MutableList<EventMetadata>>()

    init {
        val actualDriver = driver ?: createInMemoryDriver()
        database = GameDatabase(actualDriver)
    }

    /**
     * Store an event with metadata.
     */
    fun storeEvent(
        gameId: String,
        eventMetadata: EventMetadata
    ) {
        // Serialize event to JSON
        val eventData = Json.encodeToString(eventMetadata.event)
        val eventType = eventMetadata.event::class.simpleName ?: "Unknown"

        // Store in database
        queries.insertEvent(
            gameId = gameId,
            eventType = eventType,
            eventJson = eventData,
            timestamp = eventMetadata.timestamp,
            category = eventMetadata.category.name,
            importance = eventMetadata.importance.name,
            searchableText = eventMetadata.searchableText,
            npcId = eventMetadata.npcId,
            locationId = eventMetadata.locationId,
            questId = eventMetadata.questId,
            itemId = eventMetadata.itemId
        )

        // Update cache
        val cache = eventCache.getOrPut(gameId) { mutableListOf() }
        cache.add(eventMetadata)

        // Trim cache if needed
        if (cache.size > cacheSize) {
            cache.removeAt(0)
        }
    }

    /**
     * Get recent events with optional limit.
     */
    fun getRecentEvents(gameId: String, limit: Int = 20): List<EventMetadata> {
        // Check cache first for efficiency
        val cached = eventCache[gameId]
        if (cached != null && cached.size >= limit) {
            return cached.takeLast(limit)
        }

        // Fetch from database
        return queries.selectRecentEvents(gameId, limit.toLong())
            .executeAsList()
            .mapNotNull { row ->
                try {
                    EventMetadata(
                        event = Json.decodeFromString(row.eventJson),
                        category = EventCategory.valueOf(row.category),
                        importance = EventImportance.valueOf(row.importance),
                        timestamp = row.timestamp,
                        npcId = row.npcId,
                        locationId = row.locationId,
                        questId = row.questId,
                        itemId = row.itemId
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Search events by text query.
     */
    fun searchByText(gameId: String, query: String, limit: Int = 50): List<EventMetadata> {
        return queries.searchEventsByText(gameId, query, limit.toLong())
            .executeAsList()
            .mapNotNull { row ->
                try {
                    EventMetadata(
                        event = Json.decodeFromString(row.eventJson),
                        category = EventCategory.valueOf(row.category),
                        importance = EventImportance.valueOf(row.importance),
                        timestamp = row.timestamp,
                        npcId = row.npcId,
                        locationId = row.locationId,
                        questId = row.questId,
                        itemId = row.itemId
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get events by category.
     */
    fun getByCategory(gameId: String, category: EventCategory, limit: Int = 50): List<EventMetadata> {
        return queries.getEventsByCategory(gameId, category.name, limit.toLong())
            .executeAsList()
            .mapNotNull { row ->
                try {
                    EventMetadata(
                        event = Json.decodeFromString(row.eventJson),
                        category = EventCategory.valueOf(row.category),
                        importance = EventImportance.valueOf(row.importance),
                        timestamp = row.timestamp,
                        npcId = row.npcId,
                        locationId = row.locationId,
                        questId = row.questId,
                        itemId = row.itemId
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Advanced search with multiple filters.
     */
    fun searchAdvanced(
        gameId: String,
        query: String? = null,
        category: EventCategory? = null,
        npcId: String? = null,
        locationId: String? = null,
        questId: String? = null,
        limit: Int = 50
    ): List<EventMetadata> {
        return queries.searchEventsAdvanced(
            gameId,
            query,
            category?.name,
            npcId,
            locationId,
            questId,
            limit.toLong()
        ).executeAsList()
            .mapNotNull { row ->
                try {
                    EventMetadata(
                        event = Json.decodeFromString(row.eventJson),
                        category = EventCategory.valueOf(row.category),
                        importance = EventImportance.valueOf(row.importance),
                        timestamp = row.timestamp,
                        npcId = row.npcId,
                        locationId = row.locationId,
                        questId = row.questId,
                        itemId = row.itemId
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get event statistics by category.
     */
    fun getEventStatistics(gameId: String): Map<EventCategory, Long> {
        return queries.getEventCountByCategory(gameId)
            .executeAsList()
            .associate { row ->
                EventCategory.valueOf(row.category) to row.COUNT
            }
    }

    /**
     * Get total event count.
     */
    fun getTotalEventCount(gameId: String): Long {
        return queries.getTotalEventCount(gameId).executeAsOne()
    }

    /**
     * Clean up old events.
     */
    fun cleanupOldEvents(gameId: String, olderThanTimestamp: Long) {
        queries.deleteOldEvents(gameId, olderThanTimestamp)

        // Clear cache for this game
        eventCache.remove(gameId)
    }

    /**
     * Delete all events for a game.
     */
    fun deleteGameEvents(gameId: String) {
        queries.deleteEvents(gameId)
        eventCache.remove(gameId)
    }

    /**
     * Warm up cache with recent events.
     */
    fun warmupCache(gameId: String) {
        val recent = getRecentEvents(gameId, cacheSize)
        eventCache[gameId] = recent.toMutableList()
    }

}
