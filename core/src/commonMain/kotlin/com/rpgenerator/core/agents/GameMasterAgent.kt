package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.NPC
import com.rpgenerator.core.domain.NPCArchetype
import com.rpgenerator.core.domain.NPCPersonality
import com.rpgenerator.core.orchestration.*
import com.rpgenerator.core.story.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Game Master AI - the creative director who generates dynamic content.
 * Handles scene planning, NPC coordination, encounters, and narrative decisions.
 *
 * The GM's primary job is to create a SCENE PLAN that coordinates:
 * - What happens mechanically
 * - How NPCs react
 * - What narrative beats to hit
 * - What options the player has next
 *
 * The Narrator then renders this plan into cohesive prose.
 */
internal class GameMasterAgent(private val llm: LLMInterface) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val agentStream = llm.startAgent(
        """
        You are the GAME MASTER — the creative director of this LitRPG adventure.

        YOUR PRIMARY ROLE: Create SCENE PLANS that coordinate everything happening in a moment.

        When a player acts, you decide:
        1. WHAT HAPPENS - The mechanical outcome and its narrative wrapper
        2. HOW NPCs REACT - What each present NPC does, says, or feels
        3. ENVIRONMENTAL EFFECTS - How the world responds
        4. NARRATIVE BEATS - Foreshadowing, callbacks, tension, world-building to weave in
        5. PLAYER OPTIONS - What concrete actions are available next

        PRINCIPLES:
        - Everything connects. An NPC's reaction should reflect their personality and history.
        - Pacing matters. Not every moment is epic. Sometimes quiet beats hit harder.
        - Consequences ripple. Choices made now affect scenes later.
        - Specificity over generality. "The merchant winces" not "people react."

        You work WITH the existing world lore and story, not against it.
        You create content that feels organic and meaningful, not random.

        When you respond with JSON, be precise. The Narrator depends on your plan.
        """.trimIndent()
    )

    /**
     * Decide if a new NPC should appear in this situation
     */
    suspend fun shouldCreateNPC(
        playerInput: String,
        state: GameState,
        recentEvents: List<String>
    ): NPCCreationDecision {
        val prompt = """
            Player Level: ${state.playerLevel}
            Current Location: ${state.currentLocation.name}
            Player Input: "$playerInput"
            Recent Events: ${recentEvents.joinToString("; ")}

            Existing NPCs at location: ${getNPCNamesAtLocation(state)}

            Should a new NPC appear in response to this situation?

            Respond with JSON:
            {
                "shouldCreate": true/false,
                "reason": "why or why not",
                "npcRole": "merchant|quest_giver|rival|ally|mysterious_stranger|none",
                "suggestedName": "optional name suggestion",
                "contextualHints": "brief description of the situation"
            }
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCDecision(response, state.currentLocation.id)
    }

    /**
     * Create a full NPC based on the decision
     */
    suspend fun createNPC(template: NPCCreationTemplate): NPC {
        val prompt = NPCCreationHelper.getCreationPrompt(template)

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCDetails(response, template)
    }

    /**
     * Decide if a random encounter should occur
     */
    suspend fun shouldTriggerEncounter(
        state: GameState,
        recentEvents: List<String>
    ): EncounterDecision {
        val prompt = """
            Player Level: ${state.playerLevel}
            Current Location: ${state.currentLocation.name} (Danger: ${state.currentLocation.danger})
            Location Features: ${state.currentLocation.features.joinToString(", ")}
            Recent Events: ${recentEvents.joinToString("; ")}

            Should a random encounter occur?
            Consider: danger level, recent activity, story pacing, player level

            Respond with JSON:
            {
                "shouldTrigger": true/false,
                "encounterType": "combat|discovery|npc_interaction|story_beat|none",
                "description": "brief description",
                "difficulty": "trivial|easy|moderate|hard|deadly"
            }
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseEncounterDecision(response)
    }

    /**
     * Generate a quest on-demand
     */
    suspend fun generateQuest(
        requestingNPC: NPC?,
        state: GameState,
        questType: String
    ): GeneratedQuest? {
        val prompt = """
            Generate a ${questType} quest.

            Context:
            - Quest Giver: ${requestingNPC?.name ?: "The System"}
            - Player Level: ${state.playerLevel}
            - Location: ${state.currentLocation.name}
            - NPC Personality: ${requestingNPC?.personality?.traits?.joinToString() ?: "N/A"}
            - NPC Motivation: ${requestingNPC?.personality?.motivations?.joinToString() ?: "N/A"}

            Create a quest that:
            1. Fits the NPC's personality and motivations
            2. Is appropriate for player level
            3. Has meaningful rewards
            4. Connects to the larger world/story

            Format:
            Title: [quest title]
            Description: [2-3 sentences explaining the quest]
            Objectives: [numbered list of objectives]
            Reward: [what player gets for completing it]
            Failure Consequence: [what happens if they fail or refuse]
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseQuestDetails(response, requestingNPC)
    }

    /**
     * Plan the entire scene - what happens, how NPCs react, what the player can do next.
     * This is the heart of the coordinated path. The Narrator will render this plan.
     */
    suspend fun planScene(
        playerInput: String,
        state: GameState,
        recentEvents: List<String>,
        npcsAtLocation: List<NPC>
    ): ScenePlan {
        val npcDescriptions = npcsAtLocation.map { npc ->
            """
            - ${npc.name} (${npc.archetype}): ${npc.personality.traits.joinToString(", ")}
              Motivations: ${npc.personality.motivations.joinToString(", ")}
              Relationship: ${npc.getRelationship(state.gameId).getStatus()}
            """.trimIndent()
        }.joinToString("\n")

        // Build detailed quest context for the GM
        val questContext = buildQuestContextForPlanning(state)

        val prompt = """
            PLAN THIS SCENE. The Narrator will render your plan into prose.

            CONTEXT:
            System Type: ${state.systemType}
            Location: ${state.currentLocation.name}
            - Description: ${state.currentLocation.description}
            - Danger Level: ${state.currentLocation.danger}/5
            - Features: ${state.currentLocation.features.joinToString(", ")}

            Player: ${state.playerName} (Level ${state.playerLevel})
            Player Input: "$playerInput"

            Recent Events:
            ${recentEvents.takeLast(3).joinToString("\n")}

            NPCs Present:
            ${if (npcDescriptions.isNotEmpty()) npcDescriptions else "None"}

            === QUEST PROGRESSION (CRITICAL) ===
            $questContext

            IMPORTANT: Your scene plan MUST guide the player toward their NEXT INCOMPLETE objective.
            - If they're doing something unrelated, have NPCs redirect them
            - Suggested actions should include at least one that advances the quest
            - Don't let the player get stuck in loops doing the same thing repeatedly
            - Track what they've ALREADY completed and don't suggest repeating it

            ---

            Create a scene plan as JSON:
            {
                "primaryAction": {
                    "type": "COMBAT|EXPLORATION|DIALOGUE|SYSTEM_QUERY|QUEST_ACTION|MOVEMENT|INTERACTION",
                    "target": "target name or null",
                    "description": "what's happening mechanically",
                    "narrativeContext": "the story context - why this matters, how it feels"
                },
                "npcReactions": [
                    {
                        "npcName": "name",
                        "reaction": "what they do or feel",
                        "deliveryStyle": "shouts|whispers|gestures|mutters|etc",
                        "timing": "BEFORE|DURING|AFTER|NONE",
                        "dialogue": "what they say, or null"
                    }
                ],
                "environmentalEffects": ["effect 1", "effect 2"],
                "narrativeBeats": [
                    {
                        "type": "FORESHADOWING|CALLBACK|TENSION_BUILD|RELIEF|WORLD_BUILDING|CHARACTER_MOMENT",
                        "content": "the beat to include",
                        "prominence": "SUBTLE|MODERATE|PROMINENT"
                    }
                ],
                "suggestedActions": [
                    {
                        "action": "specific action the player can take",
                        "type": "action type",
                        "riskLevel": "SAFE|MODERATE|RISKY|DANGEROUS",
                        "context": "why this is available"
                    }
                ],
                "sceneTone": "TENSE|PEACEFUL|MYSTERIOUS|COMEDIC|TRAGIC|TRIUMPHANT|FOREBODING|FRANTIC",
                "triggeredEvents": [
                    {
                        "eventType": "ENCOUNTER|NPC_ARRIVAL|DISCOVERY|STORY_BEAT|ENVIRONMENTAL",
                        "description": "what happens",
                        "timing": "IMMEDIATE|DELAYED|SETUP"
                    }
                ]
            }

            Be specific. Be creative. Make every NPC reaction feel earned.
            Include 3-4 suggested actions that are CONCRETE and SPECIFIC to this scene.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseScenePlan(response, npcsAtLocation, state)
    }

    /**
     * Legacy method - kept for backwards compatibility but delegates to planScene
     */
    suspend fun coordinateResponse(
        playerInput: String,
        state: GameState,
        recentEvents: List<String>,
        npcsAtLocation: List<NPC>
    ): String {
        val plan = planScene(playerInput, state, recentEvents, npcsAtLocation)
        return plan.primaryAction.narrativeContext
    }

    /**
     * Adapt story based on player choice
     */
    suspend fun narrateChoice(
        choice: String,
        options: List<String>,
        state: GameState
    ): ChoiceOutcome {
        val prompt = """
            Player made a choice:
            Choice: "$choice"
            Available options were: ${options.joinToString(", ")}

            Current Story Act: ${MainStoryArc.getCurrentAct(state.playerLevel)}
            Player Level: ${state.playerLevel}
            Location: ${state.currentLocation.name}

            Generate:
            1. Immediate consequence (what happens right now)
            2. Long-term impact (how this affects future story)
            3. Narrative description (2-3 sentences describing the outcome)

            Format as JSON:
            {
                "immediate": "what happens now",
                "longTerm": "future implications",
                "narration": "narrative description",
                "relationshipChanges": {"npc_id": +5/-5, ...}
            }
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseChoiceOutcome(response)
    }

    // ========================
    // Helper Functions
    // ========================

    /**
     * Build detailed quest context for scene planning.
     * This gives the GM full visibility into what the player has done and needs to do next.
     */
    private fun buildQuestContextForPlanning(state: GameState): String {
        if (state.activeQuests.isEmpty()) {
            return "No active quests."
        }

        return state.activeQuests.values.joinToString("\n\n") { quest ->
            val completedObjs = quest.objectives.filter { it.isComplete() }
            val pendingObjs = quest.objectives.filter { !it.isComplete() }
            val nextObj = pendingObjs.firstOrNull()

            buildString {
                appendLine("QUEST: ${quest.name}")
                appendLine("Description: ${quest.description}")
                if (quest.giver != null) {
                    appendLine("Quest Giver: ${quest.giver}")
                }

                appendLine("\n✓ COMPLETED OBJECTIVES:")
                if (completedObjs.isEmpty()) {
                    appendLine("  (none yet)")
                } else {
                    completedObjs.forEach { obj ->
                        appendLine("  ✓ ${obj.description}")
                    }
                }

                appendLine("\n▶ NEXT OBJECTIVE (guide player here!):")
                if (nextObj != null) {
                    appendLine("  → ${nextObj.description}")
                    appendLine("    Type: ${nextObj.type}")
                    appendLine("    Target: ${nextObj.targetId}")
                } else {
                    appendLine("  All complete! Ready to turn in.")
                }

                if (pendingObjs.size > 1) {
                    appendLine("\n○ REMAINING AFTER THAT:")
                    pendingObjs.drop(1).forEach { obj ->
                        appendLine("  ○ ${obj.description}")
                    }
                }
            }
        }
    }

    private fun getNPCNamesAtLocation(state: GameState): String {
        // TODO: Get actual NPCs from NPCManager
        return "None (implement NPCManager integration)"
    }

    private fun parseNPCDecision(response: String, locationId: String): NPCCreationDecision {
        // Simple parsing - in production, use proper JSON parsing
        val shouldCreate = response.contains("\"shouldCreate\": true", ignoreCase = true)

        if (!shouldCreate) {
            return NPCCreationDecision(
                shouldCreate = false,
                reason = "Not appropriate for current situation",
                template = null
            )
        }

        // Extract role
        val role = when {
            response.contains("merchant") -> "merchant"
            response.contains("quest_giver") -> "quest_giver"
            response.contains("rival") -> "rival"
            response.contains("ally") -> "ally"
            else -> "mysterious_stranger"
        }

        val template = NPCCreationTemplate(
            role = role,
            locationId = locationId,
            contextualHints = "Player exploration",
            relationshipToPlayer = "neutral"
        )

        return NPCCreationDecision(
            shouldCreate = true,
            reason = "Enhances current situation",
            template = template
        )
    }

    private fun parseNPCDetails(response: String, template: NPCCreationTemplate): NPC {
        // Simple parsing - extract name, personality, backstory
        val lines = response.lines()

        val name = lines.find { it.startsWith("Name:") }
            ?.substringAfter("Name:")?.trim()
            ?: template.suggestedName ?: "Unknown Stranger"

        val personalityDesc = lines.find { it.startsWith("Personality:") }
            ?.substringAfter("Personality:")?.trim()
            ?: "A mysterious individual."

        val backstory = lines.find { it.startsWith("Backstory:") }
            ?.substringAfter("Backstory:")?.trim()
            ?: "Their past is unknown."

        val motivation = lines.find { it.startsWith("Motivation:") }
            ?.substringAfter("Motivation:")?.trim()
            ?: "Unknown motivations"

        // Map role to archetype
        val archetype = when (template.role) {
            "merchant" -> com.rpgenerator.core.domain.NPCArchetype.MERCHANT
            "quest_giver" -> com.rpgenerator.core.domain.NPCArchetype.QUEST_GIVER
            "rival", "ally" -> com.rpgenerator.core.domain.NPCArchetype.WANDERER
            else -> com.rpgenerator.core.domain.NPCArchetype.VILLAGER
        }

        // Create personality object
        val personality = com.rpgenerator.core.domain.NPCPersonality(
            traits = listOf(template.role, template.relationshipToPlayer),
            speechPattern = personalityDesc,
            motivations = listOf(motivation)
        )

        return NPC(
            id = NPCCreationHelper.generateNPCId(template.locationId, template.role),
            name = name,
            archetype = archetype,
            locationId = template.locationId,
            personality = personality,
            lore = backstory,
            greetingContext = template.contextualHints
        )
    }

    private fun parseEncounterDecision(response: String): EncounterDecision {
        val shouldTrigger = response.contains("\"shouldTrigger\": true", ignoreCase = true)

        val encounterType = when {
            response.contains("combat") -> "combat"
            response.contains("discovery") -> "discovery"
            response.contains("npc_interaction") -> "npc_interaction"
            response.contains("story_beat") -> "story_beat"
            else -> "none"
        }

        return EncounterDecision(
            shouldTrigger = shouldTrigger,
            encounterType = encounterType,
            description = "A random encounter occurs",
            difficulty = "moderate"
        )
    }

    private fun parseQuestDetails(response: String, questGiver: NPC?): GeneratedQuest? {
        val lines = response.lines()

        val title = lines.find { it.startsWith("Title:") }
            ?.substringAfter("Title:")?.trim()
            ?: return null

        val description = lines.find { it.startsWith("Description:") }
            ?.substringAfter("Description:")?.trim()
            ?: "No description"

        return GeneratedQuest(
            id = "quest_gen_${currentTimeMillis()}",
            title = title,
            description = description,
            questGiverId = questGiver?.id,
            objectives = listOf("Complete the quest"), // TODO: Parse objectives
            reward = "Experience and loot"
        )
    }

    private fun parseChoiceOutcome(response: String): ChoiceOutcome {
        return ChoiceOutcome(
            immediate = "Your choice has immediate effects.",
            longTerm = "This will impact your future.",
            narration = "The story continues based on your decision.",
            relationshipChanges = emptyMap()
        )
    }

    /**
     * Parse the LLM's JSON response into a ScenePlan
     */
    private fun parseScenePlan(
        response: String,
        npcsAtLocation: List<NPC>,
        state: GameState
    ): ScenePlan {
        // Try to extract JSON from the response
        val jsonString = extractJson(response)

        return try {
            val parsed = json.decodeFromString<ScenePlanJson>(jsonString)
            parsed.toScenePlan(npcsAtLocation)
        } catch (e: Exception) {
            // Fallback to a sensible default if parsing fails
            createFallbackScenePlan(response, state)
        }
    }

    private fun extractJson(response: String): String {
        // Find JSON object in response (between first { and last })
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            response.substring(start, end + 1)
        } else {
            "{}"
        }
    }

    private fun createFallbackScenePlan(response: String, state: GameState): ScenePlan {
        return ScenePlan(
            primaryAction = PlannedAction(
                type = ActionType.EXPLORATION,
                target = null,
                description = "Player action",
                narrativeContext = response.take(200)
            ),
            npcReactions = emptyList(),
            environmentalEffects = emptyList(),
            narrativeBeats = emptyList(),
            suggestedActions = listOf(
                SuggestedAction(
                    action = "Look around",
                    type = ActionType.EXPLORATION,
                    riskLevel = RiskLevel.SAFE,
                    context = "Survey your surroundings"
                ),
                SuggestedAction(
                    action = "Check your status",
                    type = ActionType.SYSTEM_QUERY,
                    riskLevel = RiskLevel.SAFE,
                    context = "Review your stats and inventory"
                )
            ),
            sceneTone = SceneTone.PEACEFUL,
            triggeredEvents = emptyList()
        )
    }
}

// ========================
// JSON Parsing Data Classes
// ========================

@Serializable
private data class ScenePlanJson(
    val primaryAction: PrimaryActionJson,
    val npcReactions: List<NPCReactionJson> = emptyList(),
    val environmentalEffects: List<String> = emptyList(),
    val narrativeBeats: List<NarrativeBeatJson> = emptyList(),
    val suggestedActions: List<SuggestedActionJson> = emptyList(),
    val sceneTone: String = "PEACEFUL",
    val triggeredEvents: List<TriggeredEventJson> = emptyList()
) {
    fun toScenePlan(npcsAtLocation: List<NPC>): ScenePlan {
        val npcMap = npcsAtLocation.associateBy { it.name.lowercase() }

        return ScenePlan(
            primaryAction = PlannedAction(
                type = parseActionType(primaryAction.type),
                target = primaryAction.target,
                description = primaryAction.description,
                narrativeContext = primaryAction.narrativeContext
            ),
            npcReactions = npcReactions.mapNotNull { reaction ->
                val npc = npcMap[reaction.npcName.lowercase()]
                if (npc != null) {
                    NPCReaction(
                        npc = npc,
                        reaction = reaction.reaction,
                        deliveryStyle = reaction.deliveryStyle,
                        timing = parseReactionTiming(reaction.timing),
                        dialogue = reaction.dialogue
                    )
                } else null
            },
            environmentalEffects = environmentalEffects,
            narrativeBeats = narrativeBeats.map { beat ->
                NarrativeBeat(
                    type = parseBeatType(beat.type),
                    content = beat.content,
                    prominence = parseProminence(beat.prominence)
                )
            },
            suggestedActions = suggestedActions.map { action ->
                SuggestedAction(
                    action = action.action,
                    type = parseActionType(action.type),
                    riskLevel = parseRiskLevel(action.riskLevel),
                    context = action.context
                )
            },
            sceneTone = parseSceneTone(sceneTone),
            triggeredEvents = triggeredEvents.map { event ->
                TriggeredEvent(
                    eventType = parseEventType(event.eventType),
                    description = event.description,
                    timing = parseEventTiming(event.timing)
                )
            }
        )
    }

    private fun parseActionType(type: String): ActionType = try {
        ActionType.valueOf(type.uppercase())
    } catch (e: Exception) {
        ActionType.EXPLORATION
    }

    private fun parseReactionTiming(timing: String): ReactionTiming = try {
        ReactionTiming.valueOf(timing.uppercase())
    } catch (e: Exception) {
        ReactionTiming.AFTER
    }

    private fun parseBeatType(type: String): BeatType = try {
        BeatType.valueOf(type.uppercase())
    } catch (e: Exception) {
        BeatType.WORLD_BUILDING
    }

    private fun parseProminence(prominence: String): Prominence = try {
        Prominence.valueOf(prominence.uppercase())
    } catch (e: Exception) {
        Prominence.MODERATE
    }

    private fun parseRiskLevel(risk: String): RiskLevel = try {
        RiskLevel.valueOf(risk.uppercase())
    } catch (e: Exception) {
        RiskLevel.MODERATE
    }

    private fun parseSceneTone(tone: String): SceneTone = try {
        SceneTone.valueOf(tone.uppercase())
    } catch (e: Exception) {
        SceneTone.PEACEFUL
    }

    private fun parseEventType(type: String): EventType = try {
        EventType.valueOf(type.uppercase())
    } catch (e: Exception) {
        EventType.DISCOVERY
    }

    private fun parseEventTiming(timing: String): EventTiming = try {
        EventTiming.valueOf(timing.uppercase())
    } catch (e: Exception) {
        EventTiming.IMMEDIATE
    }
}

@Serializable
private data class PrimaryActionJson(
    val type: String,
    val target: String? = null,
    val description: String,
    val narrativeContext: String
)

@Serializable
private data class NPCReactionJson(
    val npcName: String,
    val reaction: String,
    val deliveryStyle: String = "says",
    val timing: String = "AFTER",
    val dialogue: String? = null
)

@Serializable
private data class NarrativeBeatJson(
    val type: String,
    val content: String,
    val prominence: String = "MODERATE"
)

@Serializable
private data class SuggestedActionJson(
    val action: String,
    val type: String = "EXPLORATION",
    val riskLevel: String = "MODERATE",
    val context: String? = null
)

@Serializable
private data class TriggeredEventJson(
    val eventType: String,
    val description: String,
    val timing: String = "IMMEDIATE"
)

// ========================
// Data Classes
// ========================

data class NPCCreationDecision(
    val shouldCreate: Boolean,
    val reason: String,
    val template: NPCCreationTemplate?
)

data class EncounterDecision(
    val shouldTrigger: Boolean,
    val encounterType: String,
    val description: String,
    val difficulty: String
)

data class GeneratedQuest(
    val id: String,
    val title: String,
    val description: String,
    val questGiverId: String?,
    val objectives: List<String>,
    val reward: String
)

data class ChoiceOutcome(
    val immediate: String,
    val longTerm: String,
    val narration: String,
    val relationshipChanges: Map<String, Int>
)
