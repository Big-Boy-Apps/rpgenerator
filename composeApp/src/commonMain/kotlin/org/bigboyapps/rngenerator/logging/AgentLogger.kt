package org.bigboyapps.rngenerator.logging

import com.rpgenerator.core.util.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Log entry for agent communication
 */
data class AgentLogEntry(
    val timestamp: Long,
    val agentName: String,
    val direction: Direction,
    val content: String
) {
    enum class Direction { QUERY, RESPONSE }

    val formattedTime: String
        get() {
            val seconds = (timestamp / 1000) % 60
            val minutes = (timestamp / 1000 / 60) % 60
            val hours = (timestamp / 1000 / 60 / 60) % 24
            return String.format("%02d:%02d:%02d", hours.toInt(), minutes.toInt(), seconds.toInt())
        }
}

/**
 * Singleton logger that collects all agent queries and responses
 */
object AgentLogger {
    private val _logs = MutableStateFlow<List<AgentLogEntry>>(emptyList())
    val logs: StateFlow<List<AgentLogEntry>> = _logs.asStateFlow()

    private val maxLogs = 500

    fun logQuery(agentName: String, query: String) {
        addEntry(AgentLogEntry(
            timestamp = currentTimeMillis(),
            agentName = agentName,
            direction = AgentLogEntry.Direction.QUERY,
            content = query
        ))
    }

    fun logResponse(agentName: String, response: String) {
        addEntry(AgentLogEntry(
            timestamp = currentTimeMillis(),
            agentName = agentName,
            direction = AgentLogEntry.Direction.RESPONSE,
            content = response
        ))
    }

    private fun addEntry(entry: AgentLogEntry) {
        _logs.value = (_logs.value + entry).takeLast(maxLogs)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

// Helper to format without String.format (KMP compatible)
private fun String.Companion.format(pattern: String, vararg args: Any): String {
    var result = pattern
    args.forEach { arg ->
        result = result.replaceFirst("%02d", (arg as Int).toString().padStart(2, '0'))
    }
    return result
}
