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
 * Grok (xAI) powered LLM implementation.
 * Requires XAI_API_KEY environment variable.
 * Note: Grok uses OpenAI-compatible API format.
 */
class GrokLLM(
    private val apiKey: String = System.getenv("XAI_API_KEY") ?: "",
    private val model: String = ModelRegistry.getDefaultModel("grok")
) : LLMInterface {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(apiKey.isNotEmpty()) {
            "XAI_API_KEY environment variable must be set"
        }
    }

    override fun startAgent(systemPrompt: String): AgentStream {
        return GrokAgentStream(apiKey, model, systemPrompt, json)
    }

    private class GrokAgentStream(
        private val apiKey: String,
        private val model: String,
        private val systemPrompt: String,
        private val json: Json
    ) : AgentStream {

        private val conversationHistory = mutableListOf<Message>()

        init {
            // Add system message at the start
            conversationHistory.add(Message("system", systemPrompt))
        }

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            conversationHistory.add(Message("user", message))

            val response = withContext(Dispatchers.IO) {
                callGrokAPI(conversationHistory)
            }

            // Add assistant's response to history
            conversationHistory.add(Message("assistant", response))

            // Stream the response word by word
            val words = response.split(" ")
            words.forEachIndexed { index, word ->
                if (index < words.size - 1) {
                    emit("$word ")
                } else {
                    emit(word)
                }
            }
        }

        private fun callGrokAPI(messages: List<Message>): String {
            // Grok uses xAI's endpoint with OpenAI-compatible format
            val url = URL("https://api.x.ai/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true

                val request = GrokRequest(
                    model = model,
                    messages = messages,
                    max_tokens = 1024,
                    temperature = 0.7
                )

                val requestBody = json.encodeToString(request)
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<GrokResponse>(responseBody)
                    return response.choices.firstOrNull()?.message?.content ?: ""
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw RuntimeException("Grok API error ($responseCode): $errorBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    @Serializable
    private data class GrokRequest(
        val model: String,
        val messages: List<Message>,
        val max_tokens: Int,
        val temperature: Double
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: String
    )

    @Serializable
    private data class GrokResponse(
        val choices: List<Choice>
    )

    @Serializable
    private data class Choice(
        val message: Message
    )
}
