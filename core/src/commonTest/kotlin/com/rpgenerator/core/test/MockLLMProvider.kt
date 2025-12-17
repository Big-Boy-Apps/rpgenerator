package com.rpgenerator.core.test

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockLLMInterface(
    private val intentOverride: String? = null,
    private val malformed: Boolean = false
) : LLMInterface {
    override fun startAgent(systemPrompt: String): AgentStream {
        return MockAgentStream(intentOverride, malformed)
    }
}

class MockAgentStream(
    private val intentOverride: String? = null,
    private val malformed: Boolean = false
) : AgentStream {
    override suspend fun sendMessage(message: String): Flow<String> {
        return when {
            malformed && message.contains("Analyze this action") -> {
                flowOf("not valid json at all")
            }
            message.contains("Analyze this action") -> {
                val intent = intentOverride ?: detectIntent(message)
                flowOf("""{"intent": "$intent", "target": "test-target", "context": "Test context"}""")
            }
            message.contains("Narrate") -> {
                flowOf("Your blade strikes true, cutting deep into the goblin's flesh.")
            }
            message.contains("Generate a new location") -> {
                // Mock location generation response
                flowOf("""
                {
                    "name": "Hidden Cave",
                    "biome": "CAVE",
                    "description": "A dark cavern concealed behind thick vines. Water drips from stalactites overhead.",
                    "danger": 4,
                    "features": ["stalactites", "underground_stream", "bat_colony"],
                    "lore": "Local legends speak of treasures hidden in these caves by ancient explorers."
                }
                """.trimIndent())
            }
            message.contains("NPC Profile:") -> {
                // NPC dialogue response
                generateNPCDialogue(message)
            }
            else -> {
                flowOf("Unknown response")
            }
        }
    }

    private fun detectIntent(message: String): String {
        // Simple keyword matching to simulate what the real AI would do
        // Real SystemAgent uses LLM to analyze intent
        return when {
            message.contains("attack") || message.contains("fight") -> "COMBAT"
            message.contains("talk") || message.contains("speak") -> "NPC_DIALOGUE"
            message.contains("stats") || message.contains("status") -> "SYSTEM_QUERY"
            message.contains("look") || message.contains("explore") -> "EXPLORATION"
            else -> "EXPLORATION"
        }
    }

    private fun generateNPCDialogue(message: String): Flow<String> {
        // Extract NPC name and player input from the prompt
        val npcName = message.lines().find { it.startsWith("Name:") }
            ?.substringAfter("Name:")?.trim() ?: "NPC"

        val playerSays = message.lines().find { it.startsWith("The player says:") }
            ?.substringAfter("The player says:")?.trim()?.removeSurrounding("\"") ?: ""

        // Generate contextual response based on archetype and player input
        val response = when {
            message.contains("MERCHANT") -> {
                when {
                    playerSays.contains("buy", ignoreCase = true) ||
                    playerSays.contains("shop", ignoreCase = true) ->
                        "Welcome to my shop! I have fine wares for sale. What catches your eye?"
                    playerSays.contains("sell", ignoreCase = true) ->
                        "I might be interested in buying your goods. Show me what you have."
                    else ->
                        "Greetings, traveler. Looking to trade today?"
                }
            }
            message.contains("QUEST_GIVER") -> {
                when {
                    playerSays.contains("quest", ignoreCase = true) ||
                    playerSays.contains("help", ignoreCase = true) ->
                        "I'm glad you asked. There's a problem that needs solving, and you look capable enough."
                    else ->
                        "These are troubled times. We could use someone with your skills."
                }
            }
            message.contains("GUARD") -> {
                "State your business here, traveler."
            }
            message.contains("INNKEEPER") -> {
                "Welcome to my inn! Can I get you a room, or perhaps some food and drink?"
            }
            message.contains("BLACKSMITH") -> {
                when {
                    playerSays.contains("weapon", ignoreCase = true) ||
                    playerSays.contains("armor", ignoreCase = true) ->
                        "You've come to the right place. I craft the finest equipment in the region."
                    else ->
                        "Need something forged? I can make whatever you need."
                }
            }
            message.contains("ALCHEMIST") -> {
                "Ah, interested in the alchemical arts? I have potions and elixirs for various needs."
            }
            message.contains("SCHOLAR") -> {
                "Knowledge is the most valuable treasure. What would you like to learn?"
            }
            message.contains("WANDERER") -> {
                "The paths of fate are mysterious, traveler. Our meeting may not be coincidence."
            }
            else -> {
                "Hello there, traveler."
            }
        }

        return flowOf(response)
    }
}
