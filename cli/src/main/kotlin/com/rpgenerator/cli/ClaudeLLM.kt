package com.rpgenerator.cli

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Claude-powered LLM implementation using Anthropic's API.
 * Requires ANTHROPIC_API_KEY environment variable.
 */
class ClaudeLLM(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
    private val model: String = ModelRegistry.getDefaultModel("claude")
) : LLMInterface {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(apiKey.isNotEmpty()) {
            "ANTHROPIC_API_KEY environment variable must be set"
        }
    }

    override fun startAgent(systemPrompt: String): AgentStream {
        return ClaudeAgentStream(apiKey, model, systemPrompt, json)
    }

    private class ClaudeAgentStream(
        private val apiKey: String,
        private val model: String,
        private val systemPrompt: String,
        private val json: Json
    ) : AgentStream {

        private val conversationHistory = mutableListOf<Message>()

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            conversationHistory.add(Message("user", message))

            val response = withContext(Dispatchers.IO) {
                callClaudeAPI(conversationHistory)
            }

            // Add assistant's response to history
            conversationHistory.add(Message("assistant", response))

            // Stream the response word by word for smooth output
            val words = response.split(" ")
            words.forEachIndexed { index, word ->
                if (index < words.size - 1) {
                    emit("$word ")
                } else {
                    emit(word)
                }
            }
        }

        private fun callClaudeAPI(messages: List<Message>): String {
            val url = URL("https://api.anthropic.com/v1/messages")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
                connection.doOutput = true

                val request = ClaudeRequest(
                    model = model,
                    max_tokens = 1024,
                    system = systemPrompt,
                    messages = messages
                )

                val requestBody = json.encodeToString(request)
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<ClaudeResponse>(responseBody)
                    return response.content.firstOrNull()?.text ?: ""
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw RuntimeException("Claude API error ($responseCode): $errorBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        val max_tokens: Int,
        val system: String,
        val messages: List<Message>
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ClaudeResponse(
        val content: List<Content>
    )

    @Serializable
    private data class Content(
        val text: String,
        val type: String = "text"
    )
}
