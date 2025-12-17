package com.rpgenerator.core.events

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.tools.EventHighlight
import com.rpgenerator.core.tools.EventSummaryResult

/**
 * Utility functions for working with the event system.
 */
object EventTools {

    /**
     * Search events with advanced filtering.
     */
    fun searchEvents(
        eventStore: EventStore,
        gameId: String,
        query: String? = null,
        categories: List<EventCategory>? = null,
        npcId: String? = null,
        locationId: String? = null,
        questId: String? = null,
        limit: Int = 20
    ): List<EventMetadata> {
        // Use advanced search if any filters are specified
        if (query != null || categories != null || npcId != null ||
            locationId != null || questId != null) {

            // If single category with no other filters, use optimized query
            if (categories != null && categories.size == 1 &&
                query == null && npcId == null && locationId == null && questId == null) {
                return eventStore.getByCategory(gameId, categories.first(), limit)
            }

            // Use advanced search for complex queries
            return eventStore.searchAdvanced(
                gameId = gameId,
                query = query,
                category = categories?.firstOrNull(),
                npcId = npcId,
                locationId = locationId,
                questId = questId,
                limit = limit
            )
        }

        // Fast path: get recent events
        return eventStore.getRecentEvents(gameId, limit)
    }

    /**
     * Generate a summary of game events.
     */
    internal fun generateEventSummary(
        eventStore: EventStore,
        gameId: String,
        maxEvents: Int = 50
    ): EventSummaryResult {
        val totalEvents = eventStore.getTotalEventCount(gameId)
        val categoryCounts = eventStore.getEventStatistics(gameId)
            .mapKeys { it.key.name }

        // Get high and critical importance events
        val importantEvents = eventStore.searchAdvanced(
            gameId = gameId,
            limit = maxEvents
        ).filter { metadata ->
            metadata.importance == EventImportance.HIGH ||
            metadata.importance == EventImportance.CRITICAL
        }

        val highlights = importantEvents.map { metadata ->
            EventHighlight(
                category = metadata.category.name,
                importance = metadata.importance.name,
                text = metadata.searchableText,
                timestamp = metadata.timestamp
            )
        }

        // Generate text summary
        val summary = buildSummaryText(categoryCounts, highlights, totalEvents)

        return EventSummaryResult(
            totalEvents = totalEvents,
            categoryCounts = categoryCounts,
            recentHighlights = highlights,
            summary = summary
        )
    }

    /**
     * Get recent events as plain GameEvent list (backward compatibility).
     */
    fun getRecentEvents(
        eventStore: EventStore,
        gameId: String,
        limit: Int = 20
    ): List<GameEvent> {
        return searchEvents(eventStore, gameId, limit = limit)
            .map { it.event }
    }

    /**
     * Search by text query (backward compatibility).
     */
    fun searchByText(
        eventStore: EventStore,
        gameId: String,
        query: String,
        limit: Int = 20
    ): List<GameEvent> {
        return searchEvents(eventStore, gameId, query = query, limit = limit)
            .map { it.event }
    }

    private fun buildSummaryText(
        categoryCounts: Map<String, Long>,
        highlights: List<EventHighlight>,
        totalEvents: Long
    ): String {
        val builder = StringBuilder()
        builder.append("Game History Summary:\n")
        builder.append("Total Events: $totalEvents\n\n")

        if (categoryCounts.isNotEmpty()) {
            builder.append("Events by Category:\n")
            categoryCounts.entries
                .sortedByDescending { it.value }
                .forEach { (category, count) ->
                    builder.append("- $category: $count\n")
                }
            builder.append("\n")
        }

        if (highlights.isNotEmpty()) {
            builder.append("Notable Events:\n")
            highlights.take(10).forEach { highlight ->
                builder.append("- [${highlight.importance}] ${highlight.text}\n")
            }
        }

        return builder.toString()
    }
}
