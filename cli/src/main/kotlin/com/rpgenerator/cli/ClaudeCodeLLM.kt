package com.rpgenerator.cli

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * LLM implementation that uses Claude Code CLI.
 * Requires Claude Code CLI to be installed and authenticated.
 *
 * This allows using your Claude Pro subscription instead of API credits.
 */
class ClaudeCodeLLM : LLMInterface {

    init {
        // Check if claude-code CLI is available
        try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw RuntimeException(
                    "Claude Code CLI not found. Install from: https://claude.ai/download"
                )
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Claude Code CLI not found or not authenticated.\n" +
                "Install from: https://claude.ai/download\n" +
                "Then run: claude login"
            )
        }
    }

    override fun startAgent(systemPrompt: String): AgentStream {
        return ClaudeCodeAgentStream(systemPrompt)
    }

    private class ClaudeCodeAgentStream(
        private val systemPrompt: String
    ) : AgentStream {

        private val conversationHistory = mutableListOf<String>()

        init {
            conversationHistory.add("System: $systemPrompt")
        }

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            conversationHistory.add("User: $message")

            val response = withContext(Dispatchers.IO) {
                callClaudeCodeCLI(conversationHistory)
            }

            conversationHistory.add("Assistant: $response")

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

        private fun callClaudeCodeCLI(history: List<String>): String {
            // Build the conversation context - last message is the user prompt
            val userMessage = history.lastOrNull { it.startsWith("User:") }
                ?.removePrefix("User: ")
                ?: return ""

            // Build system context from history (simplified to avoid issues)
            val systemContext = history.firstOrNull { it.startsWith("System:") }
                ?.removePrefix("System: ")
                ?: "You are a game master running a LitRPG adventure."

            // Call claude CLI with --print for non-interactive output
            // Must use stdin when using --system-prompt
            val process = ProcessBuilder(
                "claude",
                "--print",
                "--system-prompt", systemContext,
                "--tools", ""  // Disable tools for narrative generation
            )
                .redirectErrorStream(false)  // Separate stderr to see errors
                .start()

            // Write user message to stdin and close it
            process.outputStream.use { output ->
                output.write(userMessage.toByteArray())
                output.flush()
            }

            // Read response
            val response = process.inputStream.bufferedReader().use { it.readText() }

            // Read errors
            val errors = process.errorStream.bufferedReader().use { it.readText() }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw RuntimeException("Claude Code CLI error (exit $exitCode): $errors\nOutput: $response")
            }

            return response.trim()
        }
    }
}
