package org.bigboyapps.rngenerator.llm

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Simple mock LLM for demonstration and testing.
 * Provides realistic-looking responses without needing an API key.
 */
class SimpleLLM : LLMInterface {

    override fun startAgent(systemPrompt: String): AgentStream {
        return SimpleAgentStream(systemPrompt)
    }

    private class SimpleAgentStream(private val systemPrompt: String) : AgentStream {
        private var turnCount = 0

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            turnCount++

            // Small delay to simulate API latency
            delay(100)

            val response = when {
                systemPrompt.contains("Narrator", ignoreCase = true) -> generateNarration(message)
                systemPrompt.contains("NPC", ignoreCase = true) -> generateNPCDialogue(message)
                systemPrompt.contains("Location", ignoreCase = true) -> generateLocationDescription(message)
                systemPrompt.contains("Intent", ignoreCase = true) -> analyzeIntent(message)
                systemPrompt.contains("Game Master", ignoreCase = true) -> generateGameMasterResponse(message)
                systemPrompt.contains("backstor", ignoreCase = true) -> generateBackstory(message)
                systemPrompt.contains("stat", ignoreCase = true) -> generateStats(message)
                systemPrompt.contains("playstyle", ignoreCase = true) -> expandPlaystyle(message)
                else -> generateGenericResponse(message)
            }

            // Stream word by word for realistic effect
            val words = response.split(" ")
            words.forEachIndexed { index, word ->
                delay(20) // Simulate streaming
                if (index < words.size - 1) {
                    emit("$word ")
                } else {
                    emit(word)
                }
            }
        }

        private fun generateNarration(message: String): String {
            return when {
                message.isEmpty() || message.contains("opening", ignoreCase = true) -> {
                    """
                    The world flickers. Reality warps. And then... everything changes.

                    You feel a surge of energy course through your body as ancient power integrates with your very being. The System has awakened, and with it, your potential.

                    A translucent blue panel materializes before your eyes:

                    [SYSTEM INTEGRATION COMPLETE]
                    [Welcome, Adventurer]
                    [Your journey begins now]

                    You find yourself at the edge of a mystical forest. The trees shimmer with an otherworldly glow, and distant sounds hint at both danger and opportunity ahead.

                    > Explore the forest path
                    > Examine your new abilities
                    > Look for other survivors
                    """.trimIndent()
                }
                message.contains("attack", ignoreCase = true) || message.contains("fight", ignoreCase = true) -> {
                    """
                    You ready yourself for combat! Your newly awakened abilities surge through your veins.

                    With a swift motion, you strike! Your attack connects with satisfying impact.

                    [COMBAT LOG]
                    - You deal 12 damage!
                    - Enemy health: 23/35

                    The creature staggers but remains standing, its eyes gleaming with malice.

                    > Continue attacking
                    > Use a skill
                    > Attempt to flee
                    """.trimIndent()
                }
                message.contains("explore", ignoreCase = true) || message.contains("look", ignoreCase = true) -> {
                    """
                    You carefully survey your surroundings.

                    The area reveals itself to you in new detail. Your enhanced perception picks up subtle details you would have missed before: faint tracks in the dirt, the distant rustle of movement, and a faint magical signature emanating from somewhere nearby.

                    [PERCEPTION CHECK: SUCCESS]
                    You notice a hidden path leading deeper into the woods.

                    > Follow the hidden path
                    > Continue on the main road
                    > Investigate the magical signature
                    """.trimIndent()
                }
                message.contains("skill", ignoreCase = true) || message.contains("abilities", ignoreCase = true) -> {
                    """
                    You focus inward, sensing your newfound powers.

                    [SKILLS AVAILABLE]
                    - Power Strike (Lv.1): Deal 150% damage. Cost: 10 MP
                    - Quick Step (Lv.1): Increase evasion for 3 turns. Cost: 5 MP
                    - Inspect (Lv.1): Reveal enemy stats. Cost: 3 MP

                    Your mana pool glows with potential. What would you like to do?

                    > Use Power Strike
                    > Use Quick Step
                    > Use Inspect
                    > Return to exploration
                    """.trimIndent()
                }
                else -> {
                    """
                    You take action, moving forward with purpose.

                    The System acknowledges your choice, and reality shifts subtly around you. Every step feels meaningful, every decision weighted with possibility.

                    Something stirs in the distance. Adventure awaits.

                    > Continue forward
                    > Prepare for whatever comes
                    > Check your status
                    """.trimIndent()
                }
            }
        }

        private fun generateNPCDialogue(message: String): String {
            return """
            "Ah, a newcomer to the System! I can see the potential in your eyes."

            The figure before you adjusts their weathered cloak, revealing a knowing smile.

            "These lands have changed since the Integration. Many are lost, but you... you seem different. Perhaps you're the one the prophecies spoke of?"

            > "What prophecies?"
            > "Who are you?"
            > "I need information about this area."
            """.trimIndent()
        }

        private fun generateLocationDescription(message: String): String {
            return """
            This ancient clearing pulses with residual System energy. Runes etched into stone monoliths glow faintly, their meanings lost to time but their power still potent.

            To the north, a dark forest looms. To the east, the ruins of what was once a great city. To the south, the road back to safety.

            The air itself feels charged with possibility.
            """.trimIndent()
        }

        private fun analyzeIntent(message: String): String {
            return when {
                message.contains("attack", ignoreCase = true) ||
                message.contains("fight", ignoreCase = true) ||
                message.contains("kill", ignoreCase = true) -> "COMBAT"

                message.contains("talk", ignoreCase = true) ||
                message.contains("speak", ignoreCase = true) ||
                message.contains("ask", ignoreCase = true) -> "NPC_DIALOGUE"

                message.contains("go", ignoreCase = true) ||
                message.contains("move", ignoreCase = true) ||
                message.contains("walk", ignoreCase = true) ||
                message.contains("travel", ignoreCase = true) -> "MOVEMENT"

                message.contains("look", ignoreCase = true) ||
                message.contains("examine", ignoreCase = true) ||
                message.contains("inspect", ignoreCase = true) ||
                message.contains("search", ignoreCase = true) -> "EXPLORATION"

                message.contains("use", ignoreCase = true) ||
                message.contains("skill", ignoreCase = true) ||
                message.contains("ability", ignoreCase = true) -> "SKILL_USE"

                else -> "EXPLORATION"
            }
        }

        private fun generateGameMasterResponse(message: String): String {
            return """
            The story continues to unfold. Your actions have consequences that ripple through this new reality.

            The System watches, records, and adapts. Every choice matters.
            """.trimIndent()
        }

        private fun generateBackstory(message: String): String {
            return """
            I'm Alex Chen, thirty-two years old, with dark hair starting to show a few grays and tired eyes that have seen too many late nights. I work as a software developer in Seattle, spending most of my days debugging code and drinking too much coffee. On weekends, I like to escape to the mountains for hiking, or stay in and lose myself in fantasy novels. My sister calls me every Sunday, and I always look forward to hearing about her kids. I've been meaning to learn guitar for years, and maybe one day I'll actually do it.
            """.trimIndent()
        }

        private fun generateStats(message: String): String {
            return "10,12,11,14,13,10"
        }

        private fun expandPlaystyle(message: String): String {
            return """
            The player wants an immersive adventure experience with meaningful choices. Present engaging scenarios that challenge both combat skills and decision-making. Balance action with story moments, and reward exploration and creativity.
            """.trimIndent()
        }

        private fun generateGenericResponse(message: String): String {
            return """
            The System processes your request...

            Acknowledged. Your journey continues with new possibilities unfolding before you.
            """.trimIndent()
        }
    }
}
