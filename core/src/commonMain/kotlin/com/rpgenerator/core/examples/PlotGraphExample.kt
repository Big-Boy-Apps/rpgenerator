package com.rpgenerator.core.examples

import com.rpgenerator.core.agents.*
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.persistence.PlotGraphRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rpgenerator.core.util.randomUUID

/**
 * Example demonstrating the complete plot graph system integration
 *
 * Shows:
 * 1. Initial plot graph generation
 * 2. GameMaster querying for narrative hints
 * 3. Triggering and completing plot beats
 * 4. Handling player deviations
 * 5. Emergency replanning
 * 6. Background incremental planning
 */

/**
 * Example: GameMaster using plot graph during turn processing
 */
internal suspend fun exampleGameMasterTurn(
    state: GameState,
    playerAction: String,
    plotIntegration: GameMasterPlotIntegration,
    gameMaster: Any // GameMasterAgent
): String {
    // 1. Check for plot beat triggers
    when (val trigger = plotIntegration.checkForPlotTriggers(state)) {
        is PlotTriggerResult.BeatReady -> {
            // Major plot beat is ready!
            val result = plotIntegration.triggerPlotBeat(state, trigger.node.id)
            if (result is PlotBeatTriggerResult.Success) {
                // Generate special narrative for this beat
                return generatePlotBeatNarrative(trigger.node.beat, state)
            }
        }
        else -> {
            // No special beats, continue normal gameplay
        }
    }

    // 2. Get narrative hints for context
    val hints = plotIntegration.getNarrativeHints(state)

    // 3. Check for player deviation
    val deviation = plotIntegration.detectDeviation(state, playerAction, "")
    if (deviation is DeviationDetection.Detected) {
        if (deviation.severity == DeviationSeverity.MAJOR) {
            // Emergency replan
            plotIntegration.requestReplan(
                state = state,
                reason = deviation.reason,
                invalidatedNodes = deviation.invalidatedNodes
            )
        }
    }

    // 4. Generate narrative with hints
    return generateNarrativeWithHints(playerAction, hints, state)
}

/**
 * Example: Initial plot graph generation
 */
internal suspend fun exampleInitialPlanning(
    state: GameState,
    coordinator: PlanningCoordinator,
    repository: PlotGraphRepository
): PlotGraph {
    // Generate initial plot graph
    val result = coordinator.generatePlotGraph(
        state = state,
        existingGraph = null,
        recentEvents = emptyList(),
        mode = PlanningMode.FULL
    )

    // Save to database
    repository.savePlotGraph(result.graph)

    val sessionId = randomUUID()
    repository.savePlanningSession(sessionId, state.gameId, result)

    // Log the planning results
    println("Planning completed:")
    println("- Nodes created: ${result.graph.nodes.size}")
    println("- Edges created: ${result.graph.edges.size}")
    println("- Consensus: ${result.consensus.consensusType}")
    println("- Conflicts resolved: ${result.consensus.conflicts.size}")
    println("- Next replan at level: ${result.nextReplanLevel}")

    return result.graph
}

/**
 * Example: Background planning (non-blocking)
 */
internal suspend fun exampleBackgroundPlanning(
    state: GameState,
    plotIntegration: GameMasterPlotIntegration,
    recentEvents: List<String>
) {
    plotIntegration.planInBackground(state, recentEvents).collect { progress ->
        when (progress) {
            is PlanningProgress.Starting -> {
                println("Background planning started...")
            }
            is PlanningProgress.Analyzing -> {
                println("  ${progress.message}")
            }
            is PlanningProgress.Building -> {
                println("  ${progress.message}")
            }
            is PlanningProgress.Complete -> {
                println("Background planning complete!")
                println("  New nodes: ${progress.result.graph.nodes.size}")
            }
        }
    }
}

/**
 * Example: Completing a quest and checking for plot progression
 */
internal suspend fun exampleQuestCompletion(
    state: GameState,
    questId: String,
    plotIntegration: GameMasterPlotIntegration
): String {
    // Complete the quest
    val updatedState = state.completeQuest(questId)

    // Check if this completes any plot beats
    val hints = plotIntegration.getNarrativeHints(updatedState)

    // Look for active beats that involve this quest
    val relatedBeats = hints.activeBeats.filter { beat ->
        // Check if beat description mentions the quest
        beat.description.contains(questId, ignoreCase = true)
    }

    return if (relatedBeats.isNotEmpty()) {
        // This quest was part of a larger plot thread
        val beat = relatedBeats.first()

        // Mark the beat as completed
        // (In real code, would need to find the node ID)
        "Quest completed! This was part of a larger story: ${beat.title}"
    } else {
        "Quest completed!"
    }
}

/**
 * Example: Building a custom plot graph manually
 */
internal fun exampleManualGraphConstruction(gameId: String): PlotGraph {
    val builder = PlotGraphBuilder(gameId)

    // Create some plot beats
    val beat1 = PlotBeat(
        id = "beat_1",
        title = "The Mysterious Stranger",
        description = "A hooded figure appears in the tavern",
        beatType = PlotBeatType.REVELATION,
        triggerLevel = 5,
        involvedNPCs = listOf("npc_stranger"),
        foreshadowing = "Strange symbols appear on walls"
    )

    val beat2 = PlotBeat(
        id = "beat_2",
        title = "The Dark Prophecy",
        description = "The stranger reveals a terrible truth",
        beatType = PlotBeatType.REVELATION,
        triggerLevel = 10,
        involvedNPCs = listOf("npc_stranger")
    )

    val beat3 = PlotBeat(
        id = "beat_3",
        title = "Betrayal at Dawn",
        description = "The stranger's true allegiance is revealed",
        beatType = PlotBeatType.BETRAYAL,
        triggerLevel = 15,
        involvedNPCs = listOf("npc_stranger", "npc_mentor")
    )

    // Build the graph
    return builder
        .node("node_1", beat1, "thread_main", GraphPosition(tier = 1, sequence = 1))
        .node("node_2", beat2, "thread_main", GraphPosition(tier = 1, sequence = 2))
        .node("node_3", beat3, "thread_main", GraphPosition(tier = 1, sequence = 3))
        .dependency("node_1", "node_2") // Beat 2 depends on beat 1
        .dependency("node_2", "node_3") // Beat 3 depends on beat 2
        .build()
}

/**
 * Example: Querying plot graph for ready content
 */
internal suspend fun exampleQueryReadyContent(
    state: GameState,
    repository: PlotGraphRepository
): List<PlotBeat> {
    val graph = repository.loadPlotGraph(state.gameId) ?: return emptyList()

    // Get all ready nodes
    val readyNodes = graph.getReadyNodes(state)

    // Filter by player's current location
    val relevantNodes = readyNodes.filter { node ->
        node.beat.involvedLocations.isEmpty() ||
        node.beat.involvedLocations.contains(state.currentLocation.id)
    }

    // Sort by priority
    val sortedNodes = relevantNodes.sortedByDescending { node ->
        when (node.beat.beatType) {
            PlotBeatType.REVELATION -> 5
            PlotBeatType.CONFRONTATION -> 4
            PlotBeatType.TRANSFORMATION -> 3
            PlotBeatType.CHOICE -> 2
            else -> 1
        }
    }

    return sortedNodes.map { it.beat }
}

/**
 * Example: Handling alternative paths
 */
internal suspend fun exampleAlternativePaths(
    state: GameState,
    playerChoice: String,
    graph: PlotGraph
): PlotGraph {
    // Player made a choice that creates a branching path
    val currentNode = graph.getActiveNodes().firstOrNull()
        ?: return graph

    // Get alternative paths
    val alternatives = graph.getNextPossibleNodes(currentNode.id)

    // Player chose a specific path based on their choice
    val chosenPath = when (playerChoice.lowercase()) {
        "trust" -> alternatives.firstOrNull { it.beat.beatType == PlotBeatType.REUNION }
        "betray" -> alternatives.firstOrNull { it.beat.beatType == PlotBeatType.BETRAYAL }
        else -> alternatives.firstOrNull()
    }

    // If chosen path conflicts with others, abandon the alternatives
    var updatedGraph = graph
    if (chosenPath != null) {
        val conflictingNodes = graph.getConflictingNodes(chosenPath.id)
        conflictingNodes.forEach { conflictNode ->
            updatedGraph = updatedGraph.abandonNode(conflictNode.id)
        }
    }

    return updatedGraph.pruneAbandoned()
}

/**
 * Example: Visualizing plot graph stats
 */
internal fun exampleGraphVisualization(graph: PlotGraph) {
    val stats = graph.getStats()

    println("=== Plot Graph Statistics ===")
    println("Total Nodes: ${stats.totalNodes}")
    println("Progress: ${(stats.progressPercent * 100).toInt()}%")
    println("  - Triggered: ${stats.triggeredNodes}")
    println("  - Completed: ${stats.completedNodes}")
    println("  - Abandoned: ${stats.abandonedNodes}")
    println("  - Ready: ${stats.readyNodes}")
    println()
    println("Total Edges: ${stats.totalEdges}")
    println("Active Edges: ${stats.activeEdges}")
    println()

    // Critical path analysis
    val criticalPath = graph.getCriticalPath()
    println("Critical Path (${criticalPath.size} beats):")
    criticalPath.forEach { node ->
        val status = when {
            node.completed -> "[DONE]"
            node.triggered -> "[ACTIVE]"
            node.abandoned -> "[ABANDONED]"
            else -> "[PENDING]"
        }
        println("  $status ${node.beat.title} (Level ${node.beat.triggerLevel})")
    }
}

// ========================
// Helper Functions
// ========================

private fun generatePlotBeatNarrative(beat: PlotBeat, state: GameState): String {
    return """
        === MAJOR PLOT DEVELOPMENT ===

        ${beat.title}

        ${beat.description}

        The wheels of fate are turning. This moment will shape your journey ahead.
    """.trimIndent()
}

private fun generateNarrativeWithHints(
    playerAction: String,
    hints: NarrativeHints,
    state: GameState
): String {
    val baseNarrative = "You ${playerAction.lowercase()}..."

    val foreshadowing = if (hints.foreshadowingOpportunities.isNotEmpty()) {
        "\n\n(Subtle hint: ${hints.foreshadowingOpportunities.random()})"
    } else ""

    val direction = if (hints.narrativeDirection.isNotEmpty()) {
        "\n\n[${hints.narrativeDirection}]"
    } else ""

    return baseNarrative + foreshadowing + direction
}

/**
 * Example: Complete integration in GameMasterAgent
 */
internal class ExampleGameMasterWithPlotGraph(
    private val llm: LLMInterface,
    private val plotIntegration: GameMasterPlotIntegration
) {
    suspend fun processPlayerAction(
        state: GameState,
        playerAction: String
    ): String {
        // 1. Check for scheduled planning
        if (plotIntegration.shouldReplan(state)) {
            // Trigger background planning (non-blocking)
            // This runs async and doesn't block the player
            CoroutineScope(Dispatchers.Default).launch {
                plotIntegration.planInBackground(state, emptyList()).collect()
            }
        }

        // 2. Check for plot beat triggers
        val triggerResult = plotIntegration.checkForPlotTriggers(state)
        if (triggerResult is PlotTriggerResult.BeatReady) {
            // Important plot moment - prioritize this
            plotIntegration.triggerPlotBeat(state, triggerResult.node.id)
            return generatePlotBeatNarrative(triggerResult.node.beat, state)
        }

        // 3. Get narrative hints for normal gameplay
        val hints = plotIntegration.getNarrativeHints(state)

        // 4. Generate narrative with plot context
        val narrative = generateNormalNarrative(playerAction, hints, state)

        // 5. Check for deviations after action
        val deviation = plotIntegration.detectDeviation(state, playerAction, narrative)
        if (deviation is DeviationDetection.Detected &&
            deviation.severity == DeviationSeverity.MAJOR) {
            // Schedule emergency replan
            CoroutineScope(Dispatchers.Default).launch {
                plotIntegration.requestReplan(state, deviation.reason, deviation.invalidatedNodes)
            }
        }

        return narrative
    }

    private fun generateNormalNarrative(
        playerAction: String,
        hints: NarrativeHints,
        state: GameState
    ): String {
        // Use hints to inform narrative generation
        val suggestedNPCs = hints.suggestedNPCs.joinToString()
        val foreshadowing = hints.foreshadowingOpportunities.firstOrNull()

        return "Narrative with context about upcoming events..."
    }
}
