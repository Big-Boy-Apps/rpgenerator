package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import kotlinx.coroutines.flow.toList

/**
 * Planner Agent - The long-term narrative architect
 *
 * Runs asynchronously to:
 * - Analyze player trajectory and choices
 * - Generate plot outlines for future story beats
 * - Plant seeds for long-term payoffs
 * - Ensure narrative coherence across tiers
 * - Adapt to player decisions
 *
 * Unlike the Game Master (reactive, immediate), the Planner is:
 * - Proactive (plans ahead)
 * - Patient (thinks in story arcs, not single beats)
 * - Adaptive (rewrites plans based on player choices)
 */
internal class PlannerAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the Planner - the narrative architect who thinks in story arcs.

        Your role is to analyze the player's journey and create a living outline of
        future story beats. You think 50-100 levels ahead, planting seeds that will
        pay off much later.

        Key principles:
        1. ADAPT - Player choices invalidate plans? Rewrite them.
        2. FORESHADOW - Major reveals need setup 20+ levels in advance
        3. CONSEQUENCES - Actions have ripple effects across tiers
        4. THEMES - Identify patterns in player behavior and amplify them
        5. PAYOFF - Every thread you start must eventually resolve

        You are NOT writing the story - you're outlining POSSIBILITIES.
        The Game Master and player actions will determine what actually happens.

        Think like a novelist outlining their next 5 chapters.
        """.trimIndent()
    )

    /**
     * Analyze player trajectory and generate plot outline
     * This is computationally expensive - run async, infrequently
     */
    suspend fun generatePlotOutline(
        state: GameState,
        existingThreads: List<PlotThread>,
        recentEvents: List<String>
    ): PlotOutlineResult {
        val prompt = """
            DEEP PLANNER ANALYSIS

            Take your time. This is the player's story - make it MEANINGFUL.

            === PLAYER PROFILE ===
            Level: ${state.playerLevel}
            Grade: ${state.characterSheet.currentGrade.displayName}
            Class: ${state.characterSheet.playerClass.displayName}
            Evolution Path: ${state.characterSheet.classEvolutionPath.joinToString(" → ")}

            Stats Distribution:
            - STR: ${state.characterSheet.baseStats.strength}
            - DEX: ${state.characterSheet.baseStats.dexterity}
            - CON: ${state.characterSheet.baseStats.constitution}
            - INT: ${state.characterSheet.baseStats.intelligence}
            - WIS: ${state.characterSheet.baseStats.wisdom}
            - CHA: ${state.characterSheet.baseStats.charisma}

            === RELATIONSHIP WEB ===
            ${analyzeRelationships(state)}

            === QUEST HISTORY ===
            Completed: ${state.completedQuests.size} quests
            Active: ${state.activeQuests.size} quests
            ${state.activeQuests.values.take(3).joinToString("\n") { "- ${it.name}: ${it.type}" }}

            === RECENT EVENTS (Last 10) ===
            ${recentEvents.takeLast(10).joinToString("\n")}

            === EXISTING PLOT THREADS ===
            ${existingThreads.joinToString("\n") { thread ->
                "${thread.name} (${thread.category}, ${thread.status}): ${thread.description}"
            }}

            === YOUR TASK ===

            1. ANALYZE TRAJECTORY
               - What kind of character is emerging?
               - What themes are appearing in their choices?
               - Which NPCs do they care about?
               - What playstyle do they prefer?

            2. GENERATE PLOT THREADS (3-5 new threads)
               For each thread:
               - Name & Description (evocative, specific)
               - Category (MAIN_STORY, NPC_RELATIONSHIP, etc.)
               - Priority (CRITICAL to BACKGROUND)
               - Trigger conditions (level range, NPCs, quests)
               - 3-5 planned beats within the thread

            3. UPDATE EXISTING THREADS
               - Which threads should continue?
               - Which should be ABANDONED due to player choices?
               - What new beats to add to active threads?

            4. PLANT SEEDS
               - What foreshadowing should happen NOW for beats 50 levels away?
               - What NPCs to introduce for future story beats?

            5. NEXT REVIEW
               - When should I run this analysis again? (level number)

            Respond in JSON format:
            {
                "trajectory": {
                    "playerArchetype": "Combat-focused Loner" or similar,
                    "dominantRelationships": ["npc_id1", "npc_id2"],
                    "moralAlignment": "Pragmatic" or similar,
                    "favoriteMechanics": ["exploration", "combat"],
                    "emergingThemes": ["revenge", "redemption"]
                },
                "newThreads": [
                    {
                        "name": "Thread name",
                        "description": "What this story arc is about",
                        "category": "MAIN_STORY|NPC_RELATIONSHIP|etc",
                        "priority": "CRITICAL|HIGH|MEDIUM|LOW|BACKGROUND",
                        "triggerLevel": 30,
                        "beats": [
                            {
                                "title": "Beat title",
                                "description": "What happens",
                                "beatType": "REVELATION|CONFRONTATION|etc",
                                "level": 35,
                                "foreshadowing": "Hints to drop",
                                "consequences": "What changes"
                            }
                        ]
                    }
                ],
                "updatedThreads": ["thread_id to continue"],
                "abandonedThreads": ["thread_id to abandon"],
                "reasoning": "Why you made these choices",
                "nextReviewLevel": 35
            }
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parsePlotOutline(response, state)
    }

    /**
     * Quick check: should any plot beats trigger now?
     */
    suspend fun checkPendingBeats(
        state: GameState,
        activeThreads: List<PlotThread>
    ): List<PlotBeat> {
        val readyBeats = mutableListOf<PlotBeat>()

        for (thread in activeThreads) {
            if (thread.status != PlotThreadStatus.ACTIVE) continue

            for (beat in thread.plannedBeats) {
                if (!beat.triggered && shouldTriggerBeat(beat, state)) {
                    readyBeats.add(beat)
                }
            }
        }

        return readyBeats
    }

    /**
     * Generate narrative for a plot beat when it triggers
     */
    suspend fun narratePlotBeat(
        beat: PlotBeat,
        thread: PlotThread,
        state: GameState
    ): String {
        val prompt = """
            PLOT BEAT TRIGGER: "${beat.title}"

            Thread: ${thread.name}
            Beat Type: ${beat.beatType}
            Description: ${beat.description}

            Player Context:
            - Level ${state.playerLevel}, ${state.characterSheet.currentGrade.displayName}
            - Location: ${state.currentLocation.name}

            Generate the narrative moment when this beat triggers.
            2-3 paragraphs. Make it IMPACTFUL.

            This is a major story moment - make the player FEEL it.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Suggest foreshadowing for upcoming beats
     */
    suspend fun generateForeshadowing(
        upcomingBeats: List<PlotBeat>,
        state: GameState
    ): String? {
        if (upcomingBeats.isEmpty()) return null

        val beat = upcomingBeats.firstOrNull { it.foreshadowing != null } ?: return null

        val prompt = """
            SUBTLE FORESHADOWING

            An important event is coming: "${beat.title}" (${beat.foreshadowing})

            Current situation:
            - Player at level ${state.playerLevel}
            - Location: ${state.currentLocation.name}

            Generate a SUBTLE hint about this future event.
            Should feel natural, not forced. 1-2 sentences.
            Player might not even notice it's foreshadowing.

            Examples:
            - NPC mentions something offhand
            - Environmental detail that will matter later
            - Overheard conversation
            - Strange occurrence
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    // ========================
    // Helper Functions
    // ========================

    private fun analyzeRelationships(state: GameState): String {
        val npcs = state.npcsByLocation.values.flatten()

        if (npcs.isEmpty()) {
            return "No NPC relationships yet"
        }

        return npcs
            .map { npc ->
                val relationship = npc.getRelationship(state.gameId)
                "${npc.name}: ${relationship.affinity} (${relationship.getStatus()})"
            }
            .sorted()
            .take(5)
            .joinToString("\n")
    }

    private fun shouldTriggerBeat(beat: PlotBeat, state: GameState): Boolean {
        // Check if player is at or past the trigger level
        if (state.playerLevel < beat.triggerLevel) {
            return false
        }

        // Check if involved NPCs are accessible
        for (npcId in beat.involvedNPCs) {
            val npc = state.npcsByLocation.values.flatten().find { it.id == npcId }
            val npcsAtCurrentLocation = state.getNPCsAtCurrentLocation()
            if (npc == null || !npcsAtCurrentLocation.contains(npc)) {
                return false // NPC not present
            }
        }

        // Check if at involved location (if specified)
        if (beat.involvedLocations.isNotEmpty()) {
            if (!beat.involvedLocations.contains(state.currentLocation.id)) {
                return false
            }
        }

        return true
    }

    private fun parsePlotOutline(response: String, state: GameState): PlotOutlineResult {
        // TODO: Implement proper JSON parsing
        // For now, return a placeholder result

        val trajectory = TrajectoryAnalysis(
            playerArchetype = "Emerging",
            dominantRelationships = emptyList(),
            moralAlignment = "Neutral",
            favoriteMechanics = emptyList(),
            unfinishedThreads = emptyList(),
            emergingThemes = emptyList(),
            projectedGrade = state.characterSheet.currentGrade
        )

        return PlotOutlineResult(
            newThreads = emptyList(),
            updatedThreads = emptyList(),
            abandonedThreads = emptyList(),
            trajectory = trajectory,
            reasoning = "Planner initialization",
            nextReviewLevel = state.playerLevel + 10
        )
    }
}
