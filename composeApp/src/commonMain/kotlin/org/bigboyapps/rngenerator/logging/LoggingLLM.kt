package org.bigboyapps.rngenerator.logging

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * LLM wrapper that logs all agent queries and responses
 */
class LoggingLLM(
    private val delegate: LLMInterface
) : LLMInterface {

    override val maxContextTokens: Int get() = delegate.maxContextTokens

    override fun startAgent(systemPrompt: String): AgentStream {
        val agentName = extractAgentName(systemPrompt)
        AgentLogger.logQuery(agentName, "[SYSTEM PROMPT]\n$systemPrompt")
        return LoggingAgentStream(delegate.startAgent(systemPrompt), agentName)
    }

    private fun extractAgentName(systemPrompt: String): String {
        // Try to extract a meaningful name from the system prompt
        return when {
            systemPrompt.contains("Narrator", ignoreCase = true) -> "Narrator"
            systemPrompt.contains("NPC", ignoreCase = true) -> "NPC"
            systemPrompt.contains("Game Master", ignoreCase = true) -> "GameMaster"
            systemPrompt.contains("Combat", ignoreCase = true) -> "Combat"
            systemPrompt.contains("Quest", ignoreCase = true) -> "QuestGen"
            systemPrompt.contains("Location", ignoreCase = true) -> "LocationGen"
            systemPrompt.contains("Intent", ignoreCase = true) -> "IntentAnalyzer"
            systemPrompt.contains("backstor", ignoreCase = true) -> "BackstoryGen"
            systemPrompt.contains("stat", ignoreCase = true) -> "StatsGen"
            systemPrompt.contains("playstyle", ignoreCase = true) -> "PlaystyleGen"
            else -> "Agent"
        }
    }

    private class LoggingAgentStream(
        private val delegate: AgentStream,
        private val agentName: String
    ) : AgentStream {
        override suspend fun sendMessage(message: String): Flow<String> = flow {
            AgentLogger.logQuery(agentName, message)

            val responseChunks = mutableListOf<String>()
            delegate.sendMessage(message).collect { chunk ->
                responseChunks.add(chunk)
                emit(chunk)
            }

            val fullResponse = responseChunks.joinToString("")
            AgentLogger.logResponse(agentName, fullResponse)
        }
    }
}
