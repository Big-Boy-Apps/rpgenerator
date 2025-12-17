package com.rpgenerator.cli

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Simple mock LLM for demonstration purposes.
 * In production, this would connect to Claude, GPT-4, or another LLM API.
 */
class SimpleLLM : LLMInterface {

    override fun startAgent(systemPrompt: String): AgentStream {
        return SimpleAgentStream(systemPrompt)
    }

    private class SimpleAgentStream(private val systemPrompt: String) : AgentStream {
        override suspend fun sendMessage(message: String): Flow<String> = flow {
            // Simple mock responses based on system prompt type
            val response = when {
                systemPrompt.contains("Narrator") -> generateNarration(message)
                systemPrompt.contains("NPC") -> generateNPCDialogue(message)
                systemPrompt.contains("Location") -> generateLocationDescription(message)
                systemPrompt.contains("Intent") -> analyzeIntent(message)
                else -> "Mock response to: $message"
            }

            // Stream the response word by word for realistic streaming effect
            response.split(" ").forEach { word ->
                emit("$word ")
            }
        }

        private fun generateNarration(message: String): String {
            return when {
                message.contains("opening narration") || message.contains("Opening narration") -> {
                    "You stand at the threshold of a grand adventure. The world shimmers with newfound power as the System integrates with reality. Your journey begins now."
                }
                message.contains("attack") || message.contains("combat") -> {
                    "Your strike lands true! The enemy staggers backward, wounded but still dangerous."
                }
                message.contains("death") -> {
                    "Darkness claims you as your vision fades to black. Your adventure ends here... or does it?"
                }
                message.contains("respawn") -> {
                    "You gasp as life floods back into your body. You've returned, stronger than before."
                }
                else -> {
                    "The world around you pulses with mysterious energy. Ancient secrets await discovery."
                }
            }
        }

        private fun generateNPCDialogue(message: String): String {
            return "Greetings, traveler! I sense great potential within you. What brings you to these parts?"
        }

        private fun generateLocationDescription(message: String): String {
            return "This place thrums with arcane energy. Ancient runes cover the walls, and you sense powerful forces at work."
        }

        private fun analyzeIntent(message: String): String {
            return when {
                message.contains("attack") || message.contains("fight") -> "COMBAT"
                message.contains("talk") || message.contains("speak") -> "NPC_DIALOGUE"
                message.contains("go") || message.contains("move") -> "MOVEMENT"
                message.contains("look") || message.contains("examine") -> "EXPLORATION"
                else -> "EXPLORATION"
            }
        }
    }
}
