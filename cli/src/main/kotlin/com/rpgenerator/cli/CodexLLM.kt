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
 * OpenAI Codex LLM implementation.
 * Can use GitHub Copilot token or OpenAI API key.
 *
 * This allows using GitHub Copilot subscription instead of OpenAI API credits.
 */
class CodexLLM(
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: System.getenv("GITHUB_TOKEN") ?: "",
    private val model: String = "gpt-4" // Codex model
) : LLMInterface {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        require(apiKey.isNotEmpty()) {
            "OPENAI_API_KEY or GITHUB_TOKEN environment variable must be set.\n" +
            "For GitHub Copilot users, get your token from GitHub settings."
        }
    }

    override fun startAgent(systemPrompt: String): AgentStream {
        return CodexAgentStream(apiKey, model, systemPrompt, json)
    }

    private class CodexAgentStream(
        private val apiKey: String,
        private val model: String,
        private val systemPrompt: String,
        private val json: Json
    ) : AgentStream {

        private val conversationHistory = mutableListOf<Message>()

        init {
            conversationHistory.add(Message("system", systemPrompt))
        }

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            conversationHistory.add(Message("user", message))

            val response = withContext(Dispatchers.IO) {
                callCodexAPI(conversationHistory)
            }

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

        private fun callCodexAPI(messages: List<Message>): String {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true

                val request = CodexRequest(
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
                    val response = json.decodeFromString<CodexResponse>(responseBody)
                    return response.choices.firstOrNull()?.message?.content ?: ""
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw RuntimeException("Codex API error ($responseCode): $errorBody")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    @Serializable
    private data class CodexRequest(
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
    private data class CodexResponse(
        val choices: List<Choice>
    )

    @Serializable
    private data class Choice(
        val message: Message
    )
}
