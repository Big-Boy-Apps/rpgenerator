package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Plot Outline System - Future story beats planned by the Planner Agent
 *
 * The Planner runs asynchronously in the background, analyzing:
 * - Player choices and trajectory
 * - NPC relationships
 * - Completed quests and consequences
 * - Current grade/tier progression
 *
 * It generates a living outline of future events that can:
 * - Adapt to player decisions
 * - Plant seeds for long-term payoffs
 * - Create coherent narrative arcs
 * - Ensure meaningful consequences
 */

/**
 * A plot thread - an ongoing narrative arc
 */
@Serializable
internal data class PlotThread(
    val id: String,
    val name: String,
    val description: String,
    val category: PlotCategory,
    val priority: PlotPriority,
    val triggerConditions: PlotTrigger,
    val plannedBeats: List<PlotBeat>,
    val status: PlotThreadStatus,
    val createdAtLevel: Int,
    val lastUpdated: Long = currentTimeMillis()
) {
    fun isReadyToTrigger(state: GameState): Boolean {
        return triggerConditions.isSatisfied(state) && status == PlotThreadStatus.PENDING
    }

    fun activate(): PlotThread = copy(status = PlotThreadStatus.ACTIVE)
    fun complete(): PlotThread = copy(status = PlotThreadStatus.COMPLETED)
    fun abandon(): PlotThread = copy(status = PlotThreadStatus.ABANDONED)
}

/**
 * Individual story beat within a thread
 */
@Serializable
internal data class PlotBeat(
    val id: String,
    val title: String,
    val description: String,
    val beatType: PlotBeatType,
    val triggerLevel: Int, // When this beat should occur
    val involvedNPCs: List<String> = emptyList(),
    val involvedLocations: List<String> = emptyList(),
    val foreshadowing: String? = null, // Hints to drop before this beat
    val consequences: String? = null, // What changes after this beat
    val triggered: Boolean = false
)

/**
 * Categories of plot threads
 */
@Serializable
internal enum class PlotCategory {
    MAIN_STORY,          // Core narrative progression
    NPC_RELATIONSHIP,    // Personal stories with NPCs
    FACTION_CONFLICT,    // Larger world conflicts
    TIER_EVOLUTION,      // Grade/class advancement stories
    MYSTERY,             // Mysteries to unravel
    REVENGE,             // Consequences of past actions
    REDEMPTION,          // Paths to redeem failures
    WORLD_EVENT          // Major world-changing events
}

/**
 * Priority levels for competing plot threads
 */
@Serializable
internal enum class PlotPriority {
    CRITICAL,   // Must happen (main story progression)
    HIGH,       // Should happen soon (important character arcs)
    MEDIUM,     // Can wait (side stories)
    LOW,        // Optional content (flavor)
    BACKGROUND  // World-building, passive elements
}

/**
 * Status of a plot thread
 */
@Serializable
internal enum class PlotThreadStatus {
    PENDING,     // Waiting for trigger conditions
    ACTIVE,      // Currently unfolding
    COMPLETED,   // Resolved
    ABANDONED    // Player choices made this irrelevant
}

/**
 * Types of story beats
 */
@Serializable
internal enum class PlotBeatType {
    REVELATION,      // Major truth revealed
    CONFRONTATION,   // Face a challenge or enemy
    CHOICE,          // Significant decision point
    LOSS,            // Something taken away
    VICTORY,         // Major achievement
    BETRAYAL,        // Trust broken
    REUNION,         // Return of someone/something
    TRANSFORMATION,  // Character/world changes
    ESCALATION       // Stakes raised
}

/**
 * Conditions that trigger plot threads
 */
@Serializable
internal data class PlotTrigger(
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
    val gradeRequired: Grade? = null,
    val npcAffinityRequired: Map<String, Int> = emptyMap(), // npcId -> min affinity
    val questsCompleted: List<String> = emptyList(),
    val locationsVisited: List<String> = emptyList(),
    val customCondition: String? = null // For AI to evaluate
) {
    fun isSatisfied(state: GameState): Boolean {
        // Check level range
        if (minLevel != null && state.playerLevel < minLevel) {
            return false
        }
        if (maxLevel != null && state.playerLevel > maxLevel) {
            return false
        }

        // Check grade
        if (gradeRequired != null && state.characterSheet.currentGrade.ordinal < gradeRequired.ordinal) {
            return false
        }

        // Check NPC relationships
        for ((npcId, minAffinity) in npcAffinityRequired) {
            val npc = state.npcsByLocation.values.flatten().find { it.id == npcId } ?: return false
            if (npc.getRelationship(state.gameId).affinity < minAffinity) {
                return false
            }
        }

        // Check completed quests
        if (!questsCompleted.all { state.completedQuests.contains(it) }) {
            return false
        }

        // Check visited locations
        if (!locationsVisited.all { state.discoveredTemplateLocations.contains(it) }) {
            return false
        }

        // Custom conditions evaluated by AI (for complex logic)
        // Would need to be evaluated by PlannerAgent

        return true
    }
}

/**
 * Planner's analysis of current trajectory
 */
@Serializable
internal data class TrajectoryAnalysis(
    val playerArchetype: String,      // "Combat-focused", "Diplomatic", "Explorer", etc.
    val dominantRelationships: List<String>, // NPCs player has invested in
    val moralAlignment: String,       // "Ruthless", "Pragmatic", "Heroic", etc.
    val favoriteMechanics: List<String>, // What player enjoys most
    val unfinishedThreads: List<String>, // Plot threads that need resolution
    val emergingThemes: List<String>, // Patterns in player choices
    val projectedGrade: Grade,        // Where player is heading
    val timestamp: Long = currentTimeMillis()
)

/**
 * Result of planner generating plot outline
 */
@Serializable
internal data class PlotOutlineResult(
    val newThreads: List<PlotThread>,
    val updatedThreads: List<PlotThread>,
    val abandonedThreads: List<String>, // Thread IDs to abandon
    val trajectory: TrajectoryAnalysis,
    val reasoning: String, // Why planner made these choices
    val nextReviewLevel: Int // When to run planner again
)
