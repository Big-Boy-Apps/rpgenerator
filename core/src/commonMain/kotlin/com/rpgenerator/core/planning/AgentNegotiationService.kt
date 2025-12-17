package com.rpgenerator.core.planning

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.GameState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Multi-round LLM-based agent negotiation service.
 *
 * Instead of simple voting, agents engage in actual discussion where they:
 * - See each other's reasoning
 * - Respond to specific arguments
 * - Build consensus through deliberation
 * - Use context-aware priorities
 */
internal class AgentNegotiationService(
    private val llm: LLMInterface,
    private val maxRounds: Int = 3,
    private val participatingAgents: List<NegotiatingAgent> = listOf(
        NegotiatingAgent.STORY_ARCHITECT,
        NegotiatingAgent.CHARACTER_DIRECTOR,
        NegotiatingAgent.WORLD_KEEPER
    )
) {

    /**
     * Resolve a conflict through multi-round discussion.
     */
    suspend fun resolveConflict(
        conflict: PlotConflict,
        gameState: GameState
    ): ConsensusResult = coroutineScope {
        val startTime = currentTimeMillis()
        val rounds = mutableListOf<DiscussionRound>()

        // Calculate context factors once at the start
        val contextFactors = calculateContextFactors(conflict, gameState)

        repeat(maxRounds) { roundNum ->
            println("[AgentNegotiation] Starting round ${roundNum + 1}/$maxRounds")

            // Each agent generates argument in parallel
            val arguments = participatingAgents.map { agent ->
                async {
                    agent.type to generateArgument(
                        agent = agent,
                        conflict = conflict,
                        priorRounds = rounds,
                        contextFactors = contextFactors,
                        gameState = gameState
                    )
                }
            }.map { it.await() }.toMap()

            // Create round with all arguments
            val round = DiscussionRound(
                roundNumber = roundNum + 1,
                conflictId = conflict.id,
                arguments = arguments,
                contextFactors = contextFactors
            )
            rounds.add(round)

            // Check if consensus reached
            val consensus = checkConsensus(round, conflict, contextFactors)
            if (consensus != null) {
                println("[AgentNegotiation] Consensus reached in round ${roundNum + 1}: ${consensus.consensusType}")
                return@coroutineScope consensus.copy(
                    roundsUsed = roundNum + 1
                )
            }
        }

        // No consensus after max rounds - use fallback resolution
        println("[AgentNegotiation] No consensus after $maxRounds rounds, using fallback")
        return@coroutineScope fallbackResolution(rounds.last(), conflict, contextFactors)
    }

    /**
     * Generate an agent's argument for the current round.
     */
    private suspend fun generateArgument(
        agent: NegotiatingAgent,
        conflict: PlotConflict,
        priorRounds: List<DiscussionRound>,
        contextFactors: ContextFactors,
        gameState: GameState
    ): AgentArgument {
        val prompt = buildArgumentPrompt(
            agent = agent,
            conflict = conflict,
            priorRounds = priorRounds,
            contextFactors = contextFactors,
            gameState = gameState
        )

        // Create ephemeral agent stream for this negotiation
        val agentStream = llm.startAgent(agent.systemPrompt)

        // Collect the full response from the flow
        val responseBuilder = StringBuilder()
        agentStream.sendMessage(prompt).collect { chunk ->
            responseBuilder.append(chunk)
        }
        val response = responseBuilder.toString()

        // Parse LLM response into structured argument
        return parseArgumentResponse(response, agent.type)
    }

    /**
     * Build the prompt for an agent's argument.
     */
    private fun buildArgumentPrompt(
        agent: NegotiatingAgent,
        conflict: PlotConflict,
        priorRounds: List<DiscussionRound>,
        contextFactors: ContextFactors,
        gameState: GameState
    ): String {
        val roundNum = priorRounds.size + 1

        return """
            You are participating in Round $roundNum of a planning discussion with other AI agents.

            === CONFLICT ===
            Type: ${conflict.conflictType}
            ${conflict.description}

            === PROPOSALS UNDER CONSIDERATION ===
            ${conflict.proposals.joinToString("\n\n") { proposal ->
                """
                [${proposal.id}] ${proposal.title}
                Proposed by: ${proposal.proposedBy}
                Description: ${proposal.description}
                Impact: ${proposal.expectedImpact}
                Risk Level: ${proposal.riskLevel}
                Required: ${proposal.requiredResources.joinToString(", ")}
                """.trimIndent()
            }}

            === GAME CONTEXT ===
            Player Level: ${gameState.playerLevel}
            Player Name: ${gameState.playerName}
            Current Location: ${gameState.currentLocation.name}

            Player Behavior Profile:
            ${formatBehaviorProfile(conflict.gameContext.playerBehaviorProfile)}

            Recent Events:
            ${conflict.gameContext.recentEvents.joinToString("\n") { "- $it" }}

            Active Plot Threads: ${conflict.gameContext.activeThreads.joinToString(", ")}

            === CONTEXT ANALYSIS ===
            ${formatContextFactors(contextFactors)}

            ${if (priorRounds.isNotEmpty()) formatPriorDiscussion(priorRounds) else ""}

            === YOUR TASK ===
            Provide your position on these proposals. Consider:
            1. Which proposal best fits THIS specific player's behavior and preferences?
            2. Which creates the most engaging narrative moment RIGHT NOW?
            3. Which aligns with the world themes and tone?
            4. What are the risks and resource requirements?

            ${if (priorRounds.isNotEmpty())
                "5. Respond to other agents' arguments if you disagree or want to build on their points."
            else ""}

            Respond in this format:
            POSITION: [STRONG_SUPPORT/SUPPORT/NEUTRAL/OPPOSE/STRONG_OPPOSE/PROPOSE_MODIFY]
            SUPPORTED_PROPOSAL: [proposal_id or NONE]
            CONFIDENCE: [0.0-1.0]
            REBUTTAL_TO: [comma-separated agent types you're responding to, or NONE]

            REASONING:
            [Your detailed argument - be specific about WHY this choice fits this player in this situation]

            KEY_POINTS:
            - [Main point 1]
            - [Main point 2]
            - [Main point 3]

            CONTEXTUAL_JUSTIFICATION:
            [Explain how this fits the player's style, current game state, and narrative momentum]
        """.trimIndent()
    }

    /**
     * Format behavior profile for prompt.
     */
    private fun formatBehaviorProfile(profile: PlayerBehaviorProfile): String {
        return """
            - Combat Preference: ${formatScore(profile.combatPreference)} (0=avoids, 1=seeks)
            - Exploration Style: ${formatScore(profile.explorationStyle)} (0=linear, 1=open)
            - Social Engagement: ${formatScore(profile.socialEngagement)} (0=skips, 1=deep)
            - Moral Alignment: ${profile.moralAlignment}
            - Pace Preference: ${formatScore(profile.pacePreference)} (0=slow, 1=fast)
            - Creativity: ${formatScore(profile.creativityInSolutions)} (0=conventional, 1=creative)
        """.trimIndent()
    }

    /**
     * Format context factors for prompt.
     */
    private fun formatContextFactors(factors: ContextFactors): String {
        return """
            Player Behavior Match: ${formatScore(factors.playerBehaviorMatch)}
            Narrative Cohesion: ${formatScore(factors.narrativeCohesion)}
            Timing Appropriate: ${formatScore(factors.timingAppropriate)}
            Resource Availability: ${formatScore(factors.resourceAvailability)}
            Player Engagement Potential: ${formatScore(factors.playerEngagementPotential)}
            Long-term Impact: ${formatScore(factors.longTermImpact)}
            Thematic Alignment: ${formatScore(factors.thematicAlignment)}
            Risk Level: ${formatScore(factors.riskLevel)}
        """.trimIndent()
    }

    /**
     * Format prior discussion rounds for context.
     */
    private fun formatPriorDiscussion(priorRounds: List<DiscussionRound>): String {
        return """

            === PRIOR DISCUSSION (${priorRounds.size} round(s)) ===
            ${priorRounds.joinToString("\n\n") { round ->
                """
                Round ${round.roundNumber}:
                ${round.arguments.entries.joinToString("\n") { (agent, arg) ->
                    """
                    ${agent} - ${arg.position} (confidence: ${arg.confidence})
                    Supports: ${arg.supportedProposalId ?: "None"}
                    Reasoning: ${arg.reasoning.take(200)}${if (arg.reasoning.length > 200) "..." else ""}
                    """.trimIndent()
                }}
                """.trimIndent()
            }}
        """.trimIndent()
    }

    /**
     * Format score as percentage.
     */
    private fun formatScore(score: Double): String {
        val percentage = (score * 100 * 10).toLong() / 10.0
        return "${percentage}%"
    }

    /**
     * Parse LLM response into structured argument.
     */
    private fun parseArgumentResponse(response: String, agentType: String): AgentArgument {
        // Simple parsing - look for key sections
        val lines = response.lines()

        val position = lines.firstOrNull { it.startsWith("POSITION:") }
            ?.substringAfter("POSITION:")?.trim()
            ?.let { ArgumentPosition.valueOf(it) }
            ?: ArgumentPosition.NEUTRAL

        val supportedProposal = lines.firstOrNull { it.startsWith("SUPPORTED_PROPOSAL:") }
            ?.substringAfter("SUPPORTED_PROPOSAL:")?.trim()
            ?.takeIf { it != "NONE" }

        val confidence = lines.firstOrNull { it.startsWith("CONFIDENCE:") }
            ?.substringAfter("CONFIDENCE:")?.trim()
            ?.toDoubleOrNull()
            ?: 0.5

        val rebuttalTo = lines.firstOrNull { it.startsWith("REBUTTAL_TO:") }
            ?.substringAfter("REBUTTAL_TO:")?.trim()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it != "NONE" }
            ?: emptyList()

        // Extract reasoning section
        val reasoningStart = response.indexOf("REASONING:")
        val keyPointsStart = response.indexOf("KEY_POINTS:")
        val reasoning = if (reasoningStart >= 0 && keyPointsStart >= 0) {
            response.substring(reasoningStart + "REASONING:".length, keyPointsStart).trim()
        } else {
            response.substringAfter("REASONING:").substringBefore("KEY_POINTS:").trim()
        }

        // Extract key points
        val keyPoints = response.substringAfter("KEY_POINTS:")
            .substringBefore("CONTEXTUAL_JUSTIFICATION:")
            .lines()
            .filter { it.trim().startsWith("-") }
            .map { it.trim().removePrefix("-").trim() }

        // Extract contextual justification
        val contextualJustification = response.substringAfter("CONTEXTUAL_JUSTIFICATION:")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: reasoning

        return AgentArgument(
            agentType = agentType,
            position = position,
            supportedProposalId = supportedProposal,
            reasoning = reasoning,
            confidence = confidence,
            rebuttalTo = rebuttalTo,
            keyPoints = keyPoints,
            contextualJustification = contextualJustification
        )
    }

    /**
     * Check if consensus has been reached.
     */
    private fun checkConsensus(
        round: DiscussionRound,
        conflict: PlotConflict,
        contextFactors: ContextFactors
    ): ConsensusResult? {
        // Count votes for each proposal
        val votes = mutableMapOf<String, MutableList<AgentArgument>>()
        round.arguments.values.forEach { arg ->
            arg.supportedProposalId?.let { proposalId ->
                votes.getOrPut(proposalId) { mutableListOf() }.add(arg)
            }
        }

        if (votes.isEmpty()) return null

        // Find proposal with most support
        val (winningProposalId, supporters) = votes.maxByOrNull { it.value.size } ?: return null
        val totalAgents = round.arguments.size
        val supportPercentage = supporters.size.toDouble() / totalAgents

        // Determine consensus type
        val consensusType = when {
            supportPercentage == 1.0 -> ConsensusType.UNANIMOUS
            supportPercentage >= 0.75 -> ConsensusType.STRONG_MAJORITY
            supportPercentage >= 0.5 -> ConsensusType.MAJORITY
            else -> return null // No consensus yet
        }

        // Check average confidence
        val avgConfidence = supporters.map { it.confidence }.average()
        if (avgConfidence < 0.6) return null // Not confident enough

        val winningProposal = conflict.proposals.first { it.id == winningProposalId }

        // Collect minority opinions
        val minorityOpinions = round.arguments.values
            .filter { it.supportedProposalId != winningProposalId }
            .filter { it.position in listOf(ArgumentPosition.OPPOSE, ArgumentPosition.STRONG_OPPOSE) }

        return ConsensusResult(
            conflictId = conflict.id,
            chosenProposal = winningProposal,
            roundsUsed = round.roundNumber,
            finalArguments = round.arguments,
            consensusType = consensusType,
            contextFactors = contextFactors,
            minorityOpinions = minorityOpinions,
            implementationNotes = buildImplementationNotes(winningProposal, supporters, minorityOpinions)
        )
    }

    /**
     * Fallback resolution using weighted scoring when no consensus reached.
     */
    private fun fallbackResolution(
        finalRound: DiscussionRound,
        conflict: PlotConflict,
        contextFactors: ContextFactors
    ): ConsensusResult {
        // Score each proposal based on arguments and context
        val scores = conflict.proposals.associateWith { proposal ->
            val supportingArgs = finalRound.arguments.values
                .filter { it.supportedProposalId == proposal.id }

            if (supportingArgs.isEmpty()) {
                0.0
            } else {
                val avgConfidence = supportingArgs.map { it.confidence }.average()
                val supportCount = supportingArgs.size.toDouble()
                val contextScore = contextFactors.calculatePriority()

                // Weighted score: confidence + support count + context
                (avgConfidence * 0.4) + (supportCount / participatingAgents.size * 0.3) + (contextScore * 0.3)
            }
        }

        val winningEntry = scores.maxByOrNull { it.value }
        val winningProposal = winningEntry?.key ?: conflict.proposals.first()

        val minorityOpinions = finalRound.arguments.values
            .filter { it.supportedProposalId != winningProposal.id }

        return ConsensusResult(
            conflictId = conflict.id,
            chosenProposal = winningProposal,
            roundsUsed = finalRound.roundNumber,
            finalArguments = finalRound.arguments,
            consensusType = ConsensusType.FALLBACK,
            contextFactors = contextFactors,
            minorityOpinions = minorityOpinions,
            implementationNotes = "Fallback resolution - no strong consensus. " +
                    buildImplementationNotes(winningProposal, emptyList(), minorityOpinions)
        )
    }

    /**
     * Build implementation notes from discussion.
     */
    private fun buildImplementationNotes(
        proposal: ConflictProposal,
        supporters: List<AgentArgument>,
        opposers: List<AgentArgument>
    ): String {
        return buildString {
            appendLine("Implementation: ${proposal.title}")
            appendLine()

            if (supporters.isNotEmpty()) {
                appendLine("Supporting arguments:")
                supporters.forEach { arg ->
                    appendLine("- ${arg.agentType}: ${arg.keyPoints.firstOrNull() ?: arg.reasoning.take(100)}")
                }
                appendLine()
            }

            if (opposers.isNotEmpty()) {
                appendLine("Concerns raised (consider addressing):")
                opposers.forEach { arg ->
                    appendLine("- ${arg.agentType}: ${arg.keyPoints.firstOrNull() ?: arg.reasoning.take(100)}")
                }
            }
        }.trim()
    }

    /**
     * Calculate context factors for the conflict.
     */
    private fun calculateContextFactors(
        conflict: PlotConflict,
        gameState: GameState
    ): ContextFactors {
        val context = conflict.gameContext
        val profile = context.playerBehaviorProfile

        // Analyze each factor based on game state
        // These would be more sophisticated in production

        return ContextFactors(
            playerBehaviorMatch = calculateBehaviorMatch(conflict, profile),
            narrativeCohesion = calculateNarrativeFit(conflict, context),
            timingAppropriate = calculateTimingScore(conflict, gameState),
            resourceAvailability = calculateResourceScore(conflict, context),
            riskLevel = conflict.proposals.map { it.riskLevel }.average(),
            playerEngagementPotential = calculateEngagementScore(conflict, profile),
            longTermImpact = conflict.proposals.any { it.expectedImpact.contains("major", ignoreCase = true) }.let { if (it) 0.8 else 0.5 },
            thematicAlignment = calculateThematicAlignment(conflict, gameState)
        )
    }

    private fun calculateBehaviorMatch(conflict: PlotConflict, profile: PlayerBehaviorProfile): Double {
        // Simple heuristic - would be more sophisticated in production
        return when (conflict.conflictType) {
            ConflictCategory.PLOT_DIRECTION -> (profile.explorationStyle + profile.creativityInSolutions) / 2.0
            ConflictCategory.NPC_INTRODUCTION -> profile.socialEngagement
            ConflictCategory.PACING_DECISION -> profile.pacePreference
            ConflictCategory.TONE_ADJUSTMENT -> 0.7 // Neutral
            ConflictCategory.RESOURCE_PRIORITY -> (profile.explorationStyle + profile.socialEngagement) / 2.0
        }
    }

    private fun calculateNarrativeFit(conflict: PlotConflict, context: ConflictContext): Double {
        // Check if proposals align with active threads
        val hasActiveThreads = context.activeThreads.isNotEmpty()
        return if (hasActiveThreads) 0.8 else 0.6
    }

    private fun calculateTimingScore(conflict: PlotConflict, gameState: GameState): Double {
        // Check if player level is appropriate for proposals
        val avgProposalRisk = conflict.proposals.map { it.riskLevel }.average()
        val levelFactor = gameState.playerLevel / 10.0
        return minOf(1.0, levelFactor * (1.0 - avgProposalRisk * 0.3))
    }

    private fun calculateResourceScore(conflict: PlotConflict, context: ConflictContext): Double {
        // Check if required NPCs/resources are available
        val totalRequired = conflict.proposals.sumOf { it.requiredResources.size }
        val available = context.availableNPCs.size
        return if (totalRequired == 0) 1.0 else minOf(1.0, available.toDouble() / totalRequired)
    }

    private fun calculateEngagementScore(conflict: PlotConflict, profile: PlayerBehaviorProfile): Double {
        // Estimate player engagement based on their preferences
        return (profile.explorationStyle + profile.creativityInSolutions + profile.socialEngagement) / 3.0
    }

    private fun calculateThematicAlignment(conflict: PlotConflict, gameState: GameState): Double {
        // Check if conflict aligns with world themes
        val themes = gameState.worldSettings.themes
        return if (themes.isNotEmpty()) 0.75 else 0.5
    }
}

/**
 * Agents that participate in negotiations.
 */
enum class NegotiatingAgent(val type: String, val systemPrompt: String) {
    STORY_ARCHITECT(
        type = "STORY_ARCHITECT",
        systemPrompt = """
            You are the Story Architect agent.

            Your priority is compelling narrative structure and dramatic pacing.
            You care about:
            - Plot coherence and momentum
            - Dramatic tension and payoffs
            - Character arcs and development
            - Narrative surprises and twists

            When evaluating proposals, focus on story quality and player engagement.
        """.trimIndent()
    ),

    CHARACTER_DIRECTOR(
        type = "CHARACTER_DIRECTOR",
        systemPrompt = """
            You are the Character Director agent.

            Your priority is believable NPCs and meaningful relationships.
            You care about:
            - NPC personality consistency
            - Player-NPC relationship development
            - Emotional resonance
            - Character motivations

            When evaluating proposals, focus on NPC quality and interpersonal dynamics.
        """.trimIndent()
    ),

    WORLD_KEEPER(
        type = "WORLD_KEEPER",
        systemPrompt = """
            You are the World Keeper agent.

            Your priority is world consistency and thematic coherence.
            You care about:
            - Lore consistency
            - World rules and mechanics
            - Thematic alignment
            - Setting atmosphere

            When evaluating proposals, focus on world-building and internal consistency.
        """.trimIndent()
    )
}
