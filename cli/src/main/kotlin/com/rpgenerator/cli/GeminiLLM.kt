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
 * Google Gemini-powered LLM implementation.
 * Requires GOOGLE_API_KEY environment variable.
 */
class GeminiLLM(
    private val apiKey: String = System.getenv("GOOGLE_API_KEY") ?: "",
    private val model: String = ModelRegistry.getDefaultModel("gemini")
) : LLMInterface {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(apiKey.isNotEmpty()) {
            "GOOGLE_API_KEY environment variable must be set"
        }
    }

    override fun startAgent(systemPrompt: String): AgentStream {
        return GeminiAgentStream(apiKey, model, systemPrompt, json)
    }

    private class GeminiAgentStream(
        private val apiKey: String,
        private val model: String,
        private val systemPrompt: String,
        private val json: Json
    ) : AgentStream {

        private val conversationHistory = mutableListOf<Content>()

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            // Build contents list with system instruction and history
            val contents = buildList {
                // Add conversation history
                addAll(conversationHistory)
                // Add current user message
                add(Content(parts = listOf(Part(message)), role = "user"))
            }

            val response = withContext(Dispatchers.IO) {
                callGeminiAPI(contents)
            }

            // Add user message and response to history
            conversationHistory.add(Content(parts = listOf(Part(message)), role = "user"))
            conversationHistory.add(Content(parts = listOf(Part(response)), role = "model"))

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

        private fun callGeminiAPI(contents: List<Content>): String {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = SystemInstruction(
                        parts = listOf(Part(systemPrompt))
                    ),
                    generationConfig = GenerationConfig(
                        maxOutputTokens = 1024
                    )
                )

                val requestBody = json.encodeToString(request)
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<GeminiResponse>(responseBody)
                    return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw RuntimeException("Gemini API error ($responseCode): $errorBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<Content>,
        val systemInstruction: SystemInstruction,
        val generationConfig: GenerationConfig
    )

    @Serializable
    private data class SystemInstruction(
        val parts: List<Part>
    )

    @Serializable
    private data class GenerationConfig(
        val maxOutputTokens: Int
    )

    @Serializable
    private data class Content(
        val parts: List<Part>,
        val role: String
    )

    @Serializable
    private data class Part(
        val text: String
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<Candidate>
    )

    @Serializable
    private data class Candidate(
        val content: Content
    )
}
