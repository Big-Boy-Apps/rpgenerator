package com.rpgenerator.core.planning

import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Multi-round agent discussion system for conflict resolution.
 *
 * Instead of simple weighted voting, agents engage in LLM-based negotiation
 * over multiple rounds, building consensus through discussion.
 */

/**
 * A conflict that requires agent discussion to resolve.
 */
@Serializable
data class PlotConflict(
    val id: String,
    val conflictType: ConflictCategory,
    val description: String,
    val proposals: List<ConflictProposal>,
    val gameContext: ConflictContext
)

@Serializable
enum class ConflictCategory {
    PLOT_DIRECTION,      // Multiple story paths proposed
    NPC_INTRODUCTION,    // Conflicting NPC personalities/roles
    PACING_DECISION,     // When to trigger major events
    TONE_ADJUSTMENT,     // Dark vs hopeful, serious vs comedic
    RESOURCE_PRIORITY    // Which NPCs/locations to develop
}

/**
 * A proposal being considered in the conflict.
 */
@Serializable
data class ConflictProposal(
    val id: String,
    val proposedBy: String,           // Agent type that proposed
    val title: String,
    val description: String,
    val expectedImpact: String,       // What this would do to the game
    val requiredResources: List<String>, // NPCs, locations, items needed
    val riskLevel: Double             // 0.0-1.0, how dangerous is this choice
)

/**
 * Game context for making informed decisions.
 */
@Serializable
data class ConflictContext(
    val playerLevel: Int,
    val playerBehaviorProfile: PlayerBehaviorProfile,
    val recentEvents: List<String>,   // Last 5 major events
    val activeThreads: List<String>,  // Current plot threads
    val availableNPCs: List<String>,  // NPCs that could be used
    val locationContext: String       // Where is the player now
)

/**
 * Player behavior analysis for context-aware decisions.
 */
@Serializable
data class PlayerBehaviorProfile(
    val combatPreference: Double,     // 0.0=avoids, 1.0=seeks combat
    val explorationStyle: Double,     // 0.0=linear, 1.0=open world
    val socialEngagement: Double,     // 0.0=skips dialogue, 1.0=deep conversations
    val moralAlignment: String,       // HEROIC, PRAGMATIC, CHAOTIC, VILLAINOUS
    val pacePreference: Double,       // 0.0=slow/methodical, 1.0=fast/action
    val creativityInSolutions: Double // 0.0=by-the-book, 1.0=creative problem solving
)

/**
 * A single round of discussion between agents.
 */
@Serializable
data class DiscussionRound(
    val roundNumber: Int,
    val conflictId: String,
    val arguments: Map<String, AgentArgument>, // AgentType -> Argument
    val contextFactors: ContextFactors,
    val consensusReached: Boolean = false,
    val chosenProposal: String? = null,        // Proposal ID if consensus reached
    val timestamp: Long = currentTimeMillis()
)

/**
 * An agent's argument in a discussion round.
 */
@Serializable
data class AgentArgument(
    val agentType: String,
    val position: ArgumentPosition,
    val supportedProposalId: String?,  // Which proposal they support (if any)
    val reasoning: String,              // LLM-generated argument
    val confidence: Double,             // 0.0-1.0
    val rebuttalTo: List<String> = emptyList(), // Agent types they're responding to
    val keyPoints: List<String> = emptyList(),  // Main arguments (for summaries)
    val contextualJustification: String = ""    // Why THIS player, in THIS situation
)

@Serializable
enum class ArgumentPosition {
    STRONG_SUPPORT,   // Confident this is the right choice
    SUPPORT,          // Generally agree
    NEUTRAL,          // No strong opinion
    OPPOSE,           // Disagree with this direction
    STRONG_OPPOSE,    // This would be harmful
    PROPOSE_MODIFY    // Support with modifications
}

/**
 * Context-aware priority factors for decision making.
 * Not just "confidence scores" - actual game state analysis.
 */
@Serializable
data class ContextFactors(
    val playerBehaviorMatch: Double,    // How well does this fit player's style (0.0-1.0)
    val narrativeCohesion: Double,      // Does it fit existing story (0.0-1.0)
    val timingAppropriate: Double,      // Is this the right moment (0.0-1.0)
    val resourceAvailability: Double,   // Do we have NPCs/locations needed (0.0-1.0)
    val riskLevel: Double,              // How dangerous is this choice (0.0=safe, 1.0=risky)
    val playerEngagementPotential: Double, // Will this excite the player (0.0-1.0)
    val longTermImpact: Double,         // Affects future story significantly (0.0-1.0)
    val thematicAlignment: Double       // Matches world themes (0.0-1.0)
) {
    /**
     * Calculate weighted priority score for a proposal.
     * Higher = more contextually appropriate.
     */
    fun calculatePriority(
        playerBehaviorWeight: Double = 0.25,
        narrativeWeight: Double = 0.20,
        timingWeight: Double = 0.15,
        resourceWeight: Double = 0.10,
        engagementWeight: Double = 0.15,
        impactWeight: Double = 0.10,
        thematicWeight: Double = 0.05
    ): Double {
        return (playerBehaviorMatch * playerBehaviorWeight +
                narrativeCohesion * narrativeWeight +
                timingAppropriate * timingWeight +
                resourceAvailability * resourceWeight +
                playerEngagementPotential * engagementWeight +
                longTermImpact * impactWeight +
                thematicAlignment * thematicWeight) *
                (1.0 - (riskLevel * 0.2)) // Slightly penalize high-risk choices
    }
}

/**
 * Result of multi-round consensus discussion.
 */
@Serializable
data class ConsensusResult(
    val conflictId: String,
    val chosenProposal: ConflictProposal,
    val roundsUsed: Int,
    val finalArguments: Map<String, AgentArgument>,
    val consensusType: ConsensusType,
    val contextFactors: ContextFactors,
    val minorityOpinions: List<AgentArgument> = emptyList(), // Preserve dissenting views
    val implementationNotes: String = "",    // How to execute this decision
    val timestamp: Long = currentTimeMillis()
)

@Serializable
enum class ConsensusType {
    UNANIMOUS,        // All agents agreed
    STRONG_MAJORITY,  // >75% agreement
    MAJORITY,         // >50% agreement
    FALLBACK          // No consensus, used weighted scoring
}

/**
 * Tracks full discussion history for a conflict.
 */
@Serializable
data class DiscussionHistory(
    val conflictId: String,
    val conflict: PlotConflict,
    val rounds: List<DiscussionRound>,
    val finalResult: ConsensusResult,
    val totalDuration: Long // milliseconds
)
