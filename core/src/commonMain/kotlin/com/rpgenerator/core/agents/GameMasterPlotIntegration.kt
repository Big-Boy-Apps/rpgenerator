package com.rpgenerator.core.agents

import com.rpgenerator.core.domain.*
import com.rpgenerator.core.persistence.PlotGraphRepository
import com.rpgenerator.core.util.randomUUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Integration between GameMasterAgent and the plot graph system
 *
 * Responsibilities:
 * - Query plot graph for narrative direction
 * - Trigger plot beats when conditions are met
 * - Mark beats as completed
 * - Detect when player deviates from planned path
 * - Request emergency replanning
 */
internal class GameMasterPlotIntegration(
    private val plotGraphRepository: PlotGraphRepository,
    private val planningCoordinator: PlanningCoordinator
) {

    /**
     * Check if any plot beats should trigger now
     * Called during GameMaster's narrative generation
     */
    suspend fun checkForPlotTriggers(state: GameState): PlotTriggerResult {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: return PlotTriggerResult.NoGraph

        // Get ready nodes
        val readyNodes = graph.getReadyNodes(state)

        if (readyNodes.isEmpty()) {
            return PlotTriggerResult.NoReadyBeats
        }

        // Find highest priority ready node
        val priorityNode = readyNodes
            .sortedByDescending { node ->
                calculateNodePriority(node, state, graph)
            }
            .firstOrNull()

        return if (priorityNode != null) {
            PlotTriggerResult.BeatReady(priorityNode)
        } else {
            PlotTriggerResult.NoReadyBeats
        }
    }

    /**
     * Get narrative hints for current situation
     * Helps GameMaster understand what's coming
     */
    suspend fun getNarrativeHints(state: GameState): NarrativeHints {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: return NarrativeHints.empty()

        val activeNodes = graph.getActiveNodes()
        val readyNodes = graph.getReadyNodes(state)
        val criticalPath = graph.getCriticalPath()

        // Find upcoming important beats
        val upcomingBeats = readyNodes
            .filter { it.beat.triggerLevel <= state.playerLevel + 5 }
            .take(3)

        // Foreshadowing opportunities
        val foreshadowing = criticalPath
            .filter { !it.triggered }
            .filter { it.beat.foreshadowing != null }
            .take(2)

        return NarrativeHints(
            activeBeats = activeNodes.map { it.beat },
            upcomingBeats = upcomingBeats.map { it.beat },
            foreshadowingOpportunities = foreshadowing.mapNotNull { it.beat.foreshadowing },
            suggestedNPCs = upcomingBeats.flatMap { it.beat.involvedNPCs }.distinct(),
            suggestedLocations = upcomingBeats.flatMap { it.beat.involvedLocations }.distinct(),
            narrativeDirection = determineNarrativeDirection(activeNodes, upcomingBeats)
        )
    }

    /**
     * Trigger a plot beat
     */
    suspend fun triggerPlotBeat(
        state: GameState,
        nodeId: String
    ): PlotBeatTriggerResult {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: return PlotBeatTriggerResult.GraphNotFound

        val node = graph.nodes[nodeId]
            ?: return PlotBeatTriggerResult.NodeNotFound

        if (node.triggered) {
            return PlotBeatTriggerResult.AlreadyTriggered
        }

        // Update node to triggered
        val updatedNode = node.trigger()
        val updatedGraph = graph.updateNode(nodeId) { updatedNode }

        // Save updated graph
        plotGraphRepository.savePlotGraph(updatedGraph)

        // Update node status in DB
        plotGraphRepository.updateNodeStatus(
            gameId = state.gameId,
            nodeId = nodeId,
            triggered = true,
            completed = false,
            abandoned = false,
            updatedNode = updatedNode
        )

        return PlotBeatTriggerResult.Success(updatedNode.beat)
    }

    /**
     * Mark a plot beat as completed
     */
    suspend fun completePlotBeat(
        state: GameState,
        nodeId: String
    ): PlotBeatCompletionResult {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: return PlotBeatCompletionResult.GraphNotFound

        val node = graph.nodes[nodeId]
            ?: return PlotBeatCompletionResult.NodeNotFound

        // Update node to completed
        val updatedNode = node.complete()
        val updatedGraph = graph.updateNode(nodeId) { updatedNode }

        // Save updated graph
        plotGraphRepository.savePlotGraph(updatedGraph)

        // Update node status in DB
        plotGraphRepository.updateNodeStatus(
            gameId = state.gameId,
            nodeId = nodeId,
            triggered = true,
            completed = true,
            abandoned = false,
            updatedNode = updatedNode
        )

        // Get next possible beats
        val nextBeats = updatedGraph.getNextPossibleNodes(nodeId)

        return PlotBeatCompletionResult.Success(nextBeats.map { it.beat })
    }

    /**
     * Detect if player has deviated from planned path
     */
    suspend fun detectDeviation(
        state: GameState,
        playerAction: String,
        context: String
    ): DeviationDetection {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: return DeviationDetection.NoGraph

        val activeNodes = graph.getActiveNodes()

        // Check if player action conflicts with active beats
        // This is a simplified version - real implementation would use LLM
        val conflicts = activeNodes.filter { node ->
            // Check if NPC involved in beat is now unavailable
            node.beat.involvedNPCs.any { npcId ->
                val npc = state.findNPC(npcId)
                npc == null || context.contains("killed") || context.contains("enemy")
            }
        }

        return if (conflicts.isNotEmpty()) {
            DeviationDetection.Detected(
                invalidatedNodes = conflicts.map { it.id },
                reason = "Player action conflicts with planned beats",
                severity = DeviationSeverity.MODERATE
            )
        } else {
            DeviationDetection.OnTrack
        }
    }

    /**
     * Request emergency replanning
     */
    suspend fun requestReplan(
        state: GameState,
        reason: String,
        invalidatedNodes: List<String>
    ): PlanningResult {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: throw IllegalStateException("No graph to replan from")

        return planningCoordinator.replanFromPlayerAction(
            state = state,
            existingGraph = graph,
            playerAction = reason,
            invalidatedNodes = invalidatedNodes
        )
    }

    /**
     * Check if replanning should be triggered
     */
    suspend fun shouldReplan(state: GameState): Boolean {
        val graph = plotGraphRepository.loadPlotGraph(state.gameId)
            ?: return true // No graph = need initial plan

        val lastSession = plotGraphRepository.getLastCompletedSession(state.gameId)
        val lastReplanLevel = lastSession?.playerLevel ?: 0

        return planningCoordinator.shouldReplan(state, graph, lastReplanLevel)
    }

    /**
     * Stream incremental planning updates
     * For background planning without blocking gameplay
     */
    fun planInBackground(
        state: GameState,
        recentEvents: List<String>
    ): Flow<PlanningProgress> = flow {
        emit(PlanningProgress.Starting)

        val existingGraph = plotGraphRepository.loadPlotGraph(state.gameId)

        emit(PlanningProgress.Analyzing("Analyzing player trajectory..."))

        val result = planningCoordinator.updatePlotGraph(
            state = state,
            existingGraph = existingGraph ?: PlotGraph(state.gameId),
            recentEvents = recentEvents
        )

        emit(PlanningProgress.Building("Building plot graph..."))

        // Save result
        plotGraphRepository.savePlotGraph(result.graph)

        val sessionId = randomUUID()
        plotGraphRepository.savePlanningSession(sessionId, state.gameId, result)

        emit(PlanningProgress.Complete(result))
    }

    // ========================
    // Helper Functions
    // ========================

    private fun calculateNodePriority(node: PlotNode, state: GameState, graph: PlotGraph): Float {
        var score = 0f

        // Beat type importance
        score += when (node.beat.beatType) {
            PlotBeatType.REVELATION -> 0.9f
            PlotBeatType.TRANSFORMATION -> 0.85f
            PlotBeatType.CONFRONTATION -> 0.8f
            PlotBeatType.CHOICE -> 0.75f
            PlotBeatType.BETRAYAL -> 0.7f
            PlotBeatType.LOSS -> 0.65f
            PlotBeatType.VICTORY -> 0.6f
            PlotBeatType.REUNION -> 0.5f
            PlotBeatType.ESCALATION -> 0.55f
        } * 0.4f

        // Readiness (at exact trigger level = higher priority)
        val levelDiff = kotlin.math.abs(node.beat.triggerLevel - state.playerLevel)
        score += (1f - (levelDiff / 5f).coerceIn(0f, 1f)) * 0.3f

        // NPC availability
        val npcsAvailable = node.beat.involvedNPCs.count { npcId ->
            state.getNPCsAtCurrentLocation().any { it.id == npcId }
        }.toFloat() / node.beat.involvedNPCs.size.coerceAtLeast(1)
        score += npcsAvailable * 0.2f

        // Critical path membership
        if (graph.getCriticalPath().contains(node)) {
            score += 0.1f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun determineNarrativeDirection(
        activeNodes: List<PlotNode>,
        upcomingNodes: List<PlotNode>
    ): String {
        val allNodes = activeNodes + upcomingNodes

        val beatTypes = allNodes.groupBy { it.beat.beatType }
        val dominantType = beatTypes.maxByOrNull { it.value.size }?.key

        return when (dominantType) {
            PlotBeatType.REVELATION -> "Mysteries are unfolding - truth is near"
            PlotBeatType.CONFRONTATION -> "Conflict is escalating - prepare for battle"
            PlotBeatType.BETRAYAL -> "Trust is fragile - watch for deception"
            PlotBeatType.TRANSFORMATION -> "Change is coming - evolution awaits"
            PlotBeatType.CHOICE -> "Decisions ahead - paths will diverge"
            else -> "The story unfolds naturally"
        }
    }
}

// ========================
// Result Types
// ========================

internal sealed class PlotTriggerResult {
    object NoGraph : PlotTriggerResult()
    object NoReadyBeats : PlotTriggerResult()
    data class BeatReady(val node: PlotNode) : PlotTriggerResult()
}

internal data class NarrativeHints(
    val activeBeats: List<PlotBeat>,
    val upcomingBeats: List<PlotBeat>,
    val foreshadowingOpportunities: List<String>,
    val suggestedNPCs: List<String>,
    val suggestedLocations: List<String>,
    val narrativeDirection: String
) {
    companion object {
        fun empty() = NarrativeHints(
            activeBeats = emptyList(),
            upcomingBeats = emptyList(),
            foreshadowingOpportunities = emptyList(),
            suggestedNPCs = emptyList(),
            suggestedLocations = emptyList(),
            narrativeDirection = "Exploring freely"
        )
    }
}

internal sealed class PlotBeatTriggerResult {
    object GraphNotFound : PlotBeatTriggerResult()
    object NodeNotFound : PlotBeatTriggerResult()
    object AlreadyTriggered : PlotBeatTriggerResult()
    data class Success(val beat: PlotBeat) : PlotBeatTriggerResult()
}

internal sealed class PlotBeatCompletionResult {
    object GraphNotFound : PlotBeatCompletionResult()
    object NodeNotFound : PlotBeatCompletionResult()
    data class Success(val nextBeats: List<PlotBeat>) : PlotBeatCompletionResult()
}

internal sealed class DeviationDetection {
    object NoGraph : DeviationDetection()
    object OnTrack : DeviationDetection()
    data class Detected(
        val invalidatedNodes: List<String>,
        val reason: String,
        val severity: DeviationSeverity
    ) : DeviationDetection()
}

internal enum class DeviationSeverity {
    MINOR,      // Can work around it
    MODERATE,   // Need to adjust some nodes
    MAJOR       // Full replan required
}

internal sealed class PlanningProgress {
    object Starting : PlanningProgress()
    data class Analyzing(val message: String) : PlanningProgress()
    data class Building(val message: String) : PlanningProgress()
    data class Complete(val result: PlanningResult) : PlanningProgress()
}
