package com.rpgenerator.core.planning

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.util.randomUUID
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Example usage of the multi-round agent discussion system.
 *
 * This demonstrates how to:
 * 1. Create a conflict when agents have competing proposals
 * 2. Run multi-round LLM-based negotiation
 * 3. Persist the full discussion to database
 * 4. Extract the consensus decision
 */
internal class DiscussionExample(
    private val llm: LLMInterface,
    private val repository: DiscussionRepository
) {

    /**
     * Example: Resolve a plot direction conflict.
     *
     * Scenario: Three agents propose different story beats for level 5,
     * and we need to decide which one to use through discussion.
     */
    suspend fun examplePlotDirectionConflict(
        gameState: GameState,
        planningSessionId: String
    ): ConsensusResult {
        // Create competing proposals
        val proposal1 = ConflictProposal(
            id = "reveal_faction_traitor",
            proposedBy = "STORY_ARCHITECT",
            title = "Reveal Faction Traitor",
            description = "Reveal that a trusted faction leader is actually working against the player. Creates betrayal and drama.",
            expectedImpact = "Major trust breach, faction split, emotional impact on player",
            requiredResources = listOf("Faction Leader NPC", "Betrayal cutscene"),
            riskLevel = 0.7 // High risk - might alienate player
        )

        val proposal2 = ConflictProposal(
            id = "introduce_rival_adventurer",
            proposedBy = "CHARACTER_DIRECTOR",
            title = "Introduce Rival Adventurer",
            description = "Add a charismatic rival who competes with the player for achievements and glory.",
            expectedImpact = "Ongoing rivalry subplot, motivation through competition, recurring character",
            requiredResources = listOf("Rival NPC", "Competition framework"),
            riskLevel = 0.4 // Moderate risk - rivalry might feel forced
        )

        val proposal3 = ConflictProposal(
            id = "unlock_ancient_dungeon",
            proposedBy = "WORLD_KEEPER",
            title = "Unlock Ancient Dungeon",
            description = "Open a mysterious dungeon that has been sealed for centuries, full of lore and treasures.",
            expectedImpact = "Exploration content, world lore reveal, challenging combat",
            requiredResources = listOf("Dungeon location", "Boss encounter", "Lore fragments"),
            riskLevel = 0.3 // Lower risk - dungeons are generally well-received
        )

        // Create the conflict
        val conflict = PlotConflict(
            id = randomUUID(),
            conflictType = ConflictCategory.PLOT_DIRECTION,
            description = "Three competing story beats proposed for player level 5. " +
                    "We need to choose which direction will best engage this specific player.",
            proposals = listOf(proposal1, proposal2, proposal3),
            gameContext = ConflictContext(
                playerLevel = gameState.playerLevel,
                playerBehaviorProfile = analyzePlayerBehavior(gameState),
                recentEvents = getRecentEvents(gameState),
                activeThreads = gameState.activeQuests.keys.toList(),
                availableNPCs = gameState.getNPCsAtCurrentLocation().map { it.name },
                locationContext = gameState.currentLocation.name
            )
        )

        // Save conflict to database
        repository.createConflict(conflict, planningSessionId, gameState.gameId)

        // Create negotiation service
        val negotiationService = AgentNegotiationService(llm, maxRounds = 3)

        // Run multi-round discussion
        val startTime = currentTimeMillis()
        val consensus = negotiationService.resolveConflict(conflict, gameState)
        val duration = currentTimeMillis() - startTime

        // Save each round to database (negotiation service would do this internally in production)
        // For now, we just save the final consensus
        repository.saveConsensus(consensus, conflict.id, gameState.gameId)

        // Create and save discussion history
        val history = DiscussionHistory(
            conflictId = conflict.id,
            conflict = conflict,
            rounds = emptyList(), // Would include all rounds in production
            finalResult = consensus,
            totalDuration = duration
        )
        repository.saveHistory(history, gameState.gameId)

        println("""
            |
            |=== DISCUSSION RESULT ===
            |Conflict: ${conflict.description}
            |Consensus Type: ${consensus.consensusType}
            |Rounds Used: ${consensus.roundsUsed}
            |Chosen: ${consensus.chosenProposal.title}
            |
            |Implementation Notes:
            |${consensus.implementationNotes}
            |
            |${if (consensus.minorityOpinions.isNotEmpty()) {
                """
                |Minority Opinions (preserved for context):
                |${consensus.minorityOpinions.joinToString("\n") {
                    "- ${it.agentType}: ${it.reasoning.take(100)}..."
                }}
                """.trimMargin()
            } else ""}
            |======================
        """.trimMargin())

        return consensus
    }

    /**
     * Example: Resolve NPC personality conflict.
     *
     * Scenario: Creating a new quest giver, but agents disagree on personality/tone.
     */
    suspend fun exampleNPCPersonalityConflict(
        gameState: GameState,
        planningSessionId: String,
        npcRole: String
    ): ConsensusResult {
        val proposals = listOf(
            ConflictProposal(
                id = "gruff_veteran",
                proposedBy = "CHARACTER_DIRECTOR",
                title = "Gruff Veteran Mentor",
                description = "Hardened warrior who's seen too much. Cynical but ultimately caring. Tests the player harshly.",
                expectedImpact = "Tough-love mentorship, harsh but fair feedback, emotional growth",
                requiredResources = listOf("Combat mentor archetype"),
                riskLevel = 0.5
            ),
            ConflictProposal(
                id = "mysterious_scholar",
                proposedBy = "STORY_ARCHITECT",
                title = "Mysterious Scholar",
                description = "Enigmatic academic who speaks in riddles and half-truths. Knows more than they let on.",
                expectedImpact = "Mystery subplot, lore reveals through cryptic hints, frustration turned to revelation",
                requiredResources = listOf("Riddle generator", "Lore database"),
                riskLevel = 0.6 // Risk of frustrating player
            ),
            ConflictProposal(
                id = "cheerful_optimist",
                proposedBy = "WORLD_KEEPER",
                title = "Cheerful Optimist",
                description = "Upbeat and encouraging NPC who sees the best in everyone. Provides emotional support.",
                expectedImpact = "Tonal contrast to dark world, emotional safe harbor, loyalty through kindness",
                requiredResources = listOf("Positive dialogue patterns"),
                riskLevel = 0.3
            )
        )

        val conflict = PlotConflict(
            id = randomUUID(),
            conflictType = ConflictCategory.NPC_INTRODUCTION,
            description = "Choosing personality for new $npcRole NPC. Need to match player preferences and current tone.",
            proposals = proposals,
            gameContext = ConflictContext(
                playerLevel = gameState.playerLevel,
                playerBehaviorProfile = analyzePlayerBehavior(gameState),
                recentEvents = getRecentEvents(gameState),
                activeThreads = gameState.activeQuests.keys.toList(),
                availableNPCs = gameState.getNPCsAtCurrentLocation().map { it.name },
                locationContext = gameState.currentLocation.name
            )
        )

        repository.createConflict(conflict, planningSessionId, gameState.gameId)

        val negotiationService = AgentNegotiationService(llm, maxRounds = 2)
        val consensus = negotiationService.resolveConflict(conflict, gameState)

        repository.saveConsensus(consensus, conflict.id, gameState.gameId)

        return consensus
    }

    /**
     * Analyze player behavior from game state.
     * In production, this would use actual behavioral analysis.
     */
    private fun analyzePlayerBehavior(gameState: GameState): PlayerBehaviorProfile {
        // Simplified - would analyze actual player actions
        return PlayerBehaviorProfile(
            combatPreference = 0.6,
            explorationStyle = 0.7,
            socialEngagement = 0.5,
            moralAlignment = "PRAGMATIC",
            pacePreference = 0.6,
            creativityInSolutions = 0.7
        )
    }

    /**
     * Get recent major events.
     */
    private fun getRecentEvents(gameState: GameState): List<String> {
        // Simplified - would pull from event log
        return listOf(
            "Completed tutorial",
            "Defeated first boss",
            "Joined ${gameState.worldSettings.worldName} faction",
            "Reached level ${gameState.playerLevel}"
        )
    }
}

/**
 * Example showing how to query discussion history for analysis.
 */
internal class DiscussionAnalysisExample(private val repository: DiscussionRepository) {

    /**
     * Analyze how a specific agent votes across all discussions.
     */
    fun analyzeAgentVotingPatterns(gameId: String, agentType: String) {
        val arguments = repository.getArgumentsByAgent(gameId, agentType, limit = 100)

        val positionBreakdown = arguments
            .groupBy { it.position }
            .mapValues { it.value.size }

        val avgConfidence = arguments.map { it.confidence }.average()

        val commonThemes = arguments
            .flatMap { it.keyPoints }
            .groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)

        println("""
            |
            |=== AGENT VOTING ANALYSIS: $agentType ===
            |Total Arguments: ${arguments.size}
            |Average Confidence: ${((avgConfidence * 100).toLong() / 100.0)}
            |
            |Position Breakdown:
            |${positionBreakdown.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }}
            |
            |Common Themes:
            |${commonThemes.joinToString("\n") { "  ${it.first} (${it.second}x)" }}
            |======================
        """.trimMargin())
    }

    /**
     * Get overall discussion statistics.
     */
    fun printGameStats(gameId: String) {
        val stats = repository.getDiscussionStats(gameId)

        println("""
            |
            |=== DISCUSSION STATISTICS ===
            |Total Conflicts: ${stats.totalConflicts}
            |Resolved: ${stats.resolvedConflicts}
            |Active: ${stats.activeConflicts}
            |Avg Rounds: ${((stats.averageRoundsToConsensus * 10).toLong() / 10.0)}
            |
            |Consensus Types:
            |${stats.consensusTypeBreakdown.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }}
            |======================
        """.trimMargin())
    }

    /**
     * Retrieve and display a specific discussion.
     */
    fun displayDiscussion(conflictId: String) {
        val history = repository.getHistory(conflictId) ?: run {
            println("Discussion not found: $conflictId")
            return
        }

        println("""
            |
            |=== DISCUSSION HISTORY ===
            |Conflict: ${history.conflict.description}
            |Type: ${history.conflict.conflictType}
            |Duration: ${history.totalDuration}ms
            |
            |Proposals:
            |${history.conflict.proposals.joinToString("\n") {
                "  [${it.id}] ${it.title} - ${it.description}"
            }}
            |
            |Rounds: ${history.rounds.size}
            |
            |Final Decision:
            |${history.finalResult.chosenProposal.title}
            |Consensus: ${history.finalResult.consensusType}
            |
            |${history.finalResult.implementationNotes}
            |======================
        """.trimMargin())
    }
}
