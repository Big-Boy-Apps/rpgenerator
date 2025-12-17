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
 * OpenAI-powered LLM implementation using OpenAI's API.
 * Requires OPENAI_API_KEY environment variable.
 */
class OpenAILLM(
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "",
    private val model: String = ModelRegistry.getDefaultModel("openai")
) : LLMInterface {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(apiKey.isNotEmpty()) {
            "OPENAI_API_KEY environment variable must be set"
        }
    }

    override fun startAgent(systemPrompt: String): AgentStream {
        return OpenAIAgentStream(apiKey, model, systemPrompt, json)
    }

    private class OpenAIAgentStream(
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
                callOpenAIAPI(conversationHistory)
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

        private fun callOpenAIAPI(messages: List<Message>): String {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true

                val request = OpenAIRequest(
                    model = model,
                    messages = messages,
                    max_tokens = 1024
                )

                val requestBody = json.encodeToString(request)
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<OpenAIResponse>(responseBody)
                    return response.choices.firstOrNull()?.message?.content ?: ""
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw RuntimeException("OpenAI API error ($responseCode): $errorBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    @Serializable
    private data class OpenAIRequest(
        val model: String,
        val messages: List<Message>,
        val max_tokens: Int
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: String
    )

    @Serializable
    private data class OpenAIResponse(
        val choices: List<Choice>
    )

    @Serializable
    private data class Choice(
        val message: Message
    )
}
