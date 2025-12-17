package com.rpgenerator.core.api

import kotlinx.coroutines.flow.Flow

/**
 * Interface for LLM providers.
 * Implementations handle the actual API calls to language models.
 */
interface LLMInterface {
    /**
     * Maximum context tokens supported by this LLM.
     * Used by core library to auto-refresh agents before hitting limits.
     * Default: 200k (Claude's context window)
     */
    val maxContextTokens: Int get() = 200_000

    /**
     * Start a new agent with a system prompt.
     * The agent maintains conversation context across multiple messages.
     *
     * @param systemPrompt Initial system instructions for the agent
     * @return AgentStream for sending messages and receiving responses
     */
    fun startAgent(systemPrompt: String): AgentStream
}

/**
 * A stateful conversation with an LLM agent.
 * Automatically managed by the core library.
 */
interface AgentStream {
    /**
     * Send a message to the agent and receive streaming response.
     *
     * @param message User or game message to send to the agent
     * @return Flow of response text chunks (for streaming display)
     */
    suspend fun sendMessage(message: String): Flow<String>
}
