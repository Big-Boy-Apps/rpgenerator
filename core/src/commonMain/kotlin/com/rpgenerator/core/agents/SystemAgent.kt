package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.orchestration.Intent
import com.rpgenerator.core.orchestration.SystemResponse
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json

/**
 * System Agent - The authoritative voice of the System
 *
 * Handles:
 * - Tier/Grade progression (E → D → C → B → A → S)
 * - Class evolution choices
 * - Skill evolution paths
 * - System notifications (blue boxes)
 * - Rule enforcement
 */
internal class SystemAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the System - the omniscient AI that governs this LitRPG multiverse integration.

        Your voice is authoritative, impersonal, and clinical. You are not cruel or kind -
        you simply ARE. You enforce the rules of progression with absolute consistency.

        When presenting choices to users, be clear about:
        - What each option grants
        - Why it matters
        - The long-term implications

        You speak in System notifications - concise, informative, occasionally cryptic.

        Analyze player actions and determine their intent.

        Respond in JSON format:
        {
            "intent": "COMBAT" | "NPC_DIALOGUE" | "EXPLORATION" | "SYSTEM_QUERY" | "QUEST_ACTION",
            "target": "optional-target-name",
            "context": "brief context for narrator"
        }
        """.trimIndent()
    )

    suspend fun process(input: String, state: GameState): SystemResponse {
        val prompt = """
            Player level: ${state.playerLevel}
            Location: ${state.currentLocation.name} - ${state.currentLocation.description}
            Danger level: ${state.currentLocation.danger}
            Features: ${state.currentLocation.features.joinToString(", ")}

            Player input: "$input"

            Analyze this action and respond with JSON.
        """.trimIndent()

        val responseText = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseResponse(responseText)
    }

    /**
     * Generate personalized advancement paths based on player playstyle
     * Takes time to create meaningful, interesting options
     */
    suspend fun generateAdvancementPaths(
        state: GameState,
        newGrade: Grade,
        baseOptions: List<ClassEvolution>
    ): List<ClassEvolution> {
        val prompt = """
            ADVANCEMENT PATH GENERATION

            The player is advancing to ${newGrade.displayName}.
            Take your time analyzing their journey to create PERFECT evolution options.

            Player Profile:
            - Level: ${state.playerLevel}
            - Current Class: ${state.characterSheet.playerClass.displayName}
            - Combat Style: [Analyze from stats and skills]
            - Base Stats: STR ${state.characterSheet.baseStats.strength},
                         DEX ${state.characterSheet.baseStats.dexterity},
                         CON ${state.characterSheet.baseStats.constitution},
                         INT ${state.characterSheet.baseStats.intelligence},
                         WIS ${state.characterSheet.baseStats.wisdom},
                         CHA ${state.characterSheet.baseStats.charisma}

            Quest History: ${state.completedQuests.size} quests completed
            NPCs Befriended: ${state.npcsByLocation.values.flatten().count { it.getRelationship(state.gameId).affinity > 50 }} strong relationships

            Base Evolution Options:
            ${baseOptions.joinToString("\n") { "- ${it.name}: ${it.description}" }}

            Your Task:
            Generate 3-5 personalized evolution paths that:
            1. Reflect how the player has ACTUALLY played
            2. Offer meaningful strategic choices (not just "better numbers")
            3. Create interesting synergies with their current build
            4. Include at least one unexpected/creative option
            5. Have long-term implications for future tiers

            For each path, provide:
            - Name (evocative, specific)
            - Description (what it does, 1-2 sentences)
            - Playstyle implications
            - Stat bonuses (if any)

            Format as JSON array:
            [
                {
                    "name": "Path name",
                    "description": "What this path represents",
                    "implications": "How this changes gameplay",
                    "statBonuses": {"strength": 0, "dexterity": 0, ...}
                }
            ]

            Think deeply. These choices should feel EARNED and MEANINGFUL.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        // Parse and return the generated paths
        // For now, return base options + customize them
        return baseOptions.map { evolution ->
            // TODO: Actually parse the AI response and create custom evolutions
            evolution
        }
    }

    /**
     * Present tier-up choices to the player
     */
    suspend fun presentTierUp(tierUpEvent: TierUpEvent, state: GameState): String {
        val prompt = """
            The player has achieved ${tierUpEvent.newGrade.displayName}!

            Previous Grade: ${tierUpEvent.oldGrade.displayName}
            New Grade: ${tierUpEvent.newGrade.displayName}
            Player Level: ${state.playerLevel}
            Current Class: ${state.characterSheet.playerClass.displayName}

            Evolution Options Available:
            ${tierUpEvent.classEvolutionOptions.joinToString("\n") { "- ${it.name}: ${it.description}" }}

            Stat Points Awarded: ${tierUpEvent.statPointsAwarded}
            Skill Slot Unlocked: ${tierUpEvent.skillSlotUnlocked}

            Generate a System notification that:
            1. Announces the grade advancement with authority
            2. Explains what ${tierUpEvent.newGrade.displayName} means
            3. Presents the evolution choices clearly
            4. Maintains the System's impersonal, omniscient tone

            Use the classic "blue box" format with borders.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate flavor text for a class evolution choice
     */
    suspend fun describeEvolution(
        evolution: ClassEvolution,
        currentClass: PlayerClass,
        grade: Grade
    ): String {
        val prompt = """
            The player is choosing the "${evolution.name}" evolution path.

            Current Class: ${currentClass.displayName}
            New Grade: ${grade.displayName}
            Evolution: ${evolution.name}
            Base Description: ${evolution.description}

            Generate a System message that:
            1. Confirms the choice
            2. Describes what this evolution path means
            3. Hints at future power (but stays mysterious)
            4. Maintains the System's tone

            2-3 sentences max. The System is concise.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate a System notification for significant events
     */
    suspend fun generateSystemNotification(
        event: String,
        context: Map<String, Any>
    ): String {
        val prompt = """
            Generate a System notification for: $event

            Context: ${context.entries.joinToString(", ") { "${it.key}: ${it.value}" }}

            The System speaks in:
            - Short, declarative sentences
            - Clinical, impersonal tone
            - Occasionally cryptic hints
            - Blue box format

            1-2 sentences only.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    private fun parseResponse(text: String): SystemResponse {
        return try {
            Json.decodeFromString<SystemResponse>(text.trim())
        } catch (e: Exception) {
            SystemResponse(
                intent = Intent.EXPLORATION,
                context = "Unable to parse system response"
            )
        }
    }
}
