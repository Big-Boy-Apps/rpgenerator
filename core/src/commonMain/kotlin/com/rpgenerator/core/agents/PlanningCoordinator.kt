package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.randomUUID

/**
 * Planning Coordinator - Orchestrates multiple planning agents
 *
 * Responsibilities:
 * - Manages multiple specialized planning agents (story, character, world)
 * - Runs agents in parallel to generate proposals
 * - Aggregates proposals into coherent plot graph
 * - Uses ConsensusEngine to resolve conflicts
 * - Triggers replanning when player breaks expected flow
 * - Manages incremental updates vs full replans
 */
internal class PlanningCoordinator(
    private val llm: LLMInterface,
    private val consensusEngine: ConsensusEngine = ConsensusEngine()
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // Specialized planning agents
    private val storyAgent = createStoryPlannerAgent()
    private val characterAgent = createCharacterPlannerAgent()
    private val worldAgent = createWorldPlannerAgent()

    /**
     * Generate a complete plot graph using multi-agent planning
     */
    suspend fun generatePlotGraph(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>,
        mode: PlanningMode = PlanningMode.FULL
    ): PlanningResult = coroutineScope {
        // Run all agents in parallel
        val storyProposalDeferred = async { generateStoryProposal(state, existingGraph, recentEvents) }
        val characterProposalDeferred = async { generateCharacterProposal(state, existingGraph, recentEvents) }
        val worldProposalDeferred = async { generateWorldProposal(state, existingGraph, recentEvents) }

        // Collect all proposals
        val proposals = listOfNotNull(
            storyProposalDeferred.await(),
            characterProposalDeferred.await(),
            worldProposalDeferred.await()
        )

        // Resolve conflicts via consensus
        val consensus = consensusEngine.resolveProposals(proposals, state)

        // Build new graph from accepted proposals
        val newGraph = buildGraphFromConsensus(
            consensus = consensus,
            existingGraph = existingGraph,
            gameId = state.gameId,
            mode = mode
        )

        PlanningResult(
            graph = newGraph,
            proposals = proposals,
            consensus = consensus,
            mode = mode,
            triggerReason = determineTriggerReason(state, existingGraph),
            nextReplanLevel = calculateNextReplanLevel(state, newGraph)
        )
    }

    /**
     * Incremental update - only plan new content, preserve existing
     */
    suspend fun updatePlotGraph(
        state: GameState,
        existingGraph: PlotGraph,
        recentEvents: List<String>
    ): PlanningResult {
        return generatePlotGraph(state, existingGraph, recentEvents, PlanningMode.INCREMENTAL)
    }

    /**
     * Emergency replan - player broke the expected flow
     */
    suspend fun replanFromPlayerAction(
        state: GameState,
        existingGraph: PlotGraph,
        playerAction: String,
        invalidatedNodes: List<String>
    ): PlanningResult {
        // Abandon invalidated nodes
        var updatedGraph = existingGraph
        invalidatedNodes.forEach { nodeId ->
            updatedGraph = updatedGraph.abandonNode(nodeId)
        }

        // Generate new paths around the break
        val result = generatePlotGraph(
            state = state,
            existingGraph = updatedGraph.pruneAbandoned(),
            recentEvents = listOf("PLAYER_DEVIATION: $playerAction"),
            mode = PlanningMode.ADAPTIVE
        )

        return result.copy(
            triggerReason = "Player action invalidated planned path: $playerAction"
        )
    }

    /**
     * Check if replanning should be triggered
     */
    fun shouldReplan(state: GameState, graph: PlotGraph, lastReplanLevel: Int): Boolean {
        // Level-based trigger
        if (state.playerLevel >= lastReplanLevel + 10) {
            return true
        }

        // Running out of ready content
        val readyNodes = graph.getReadyNodes(state)
        if (readyNodes.size < 3) {
            return true
        }

        // Graph completion threshold
        val stats = graph.getStats()
        if (stats.progressPercent > 0.7f) {
            return true
        }

        return false
    }

    // ========================
    // Agent Proposal Generation
    // ========================

    /**
     * Story-focused planning agent
     * Focuses on: Main plot, revelations, escalations
     */
    private suspend fun generateStoryProposal(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>
    ): AgentProposal? {
        val prompt = buildStoryPrompt(state, existingGraph, recentEvents)
        val response = storyAgent.sendMessage(prompt).toList().joinToString("")

        return parseAgentProposal(response, "story_agent", state)
    }

    /**
     * Character-focused planning agent
     * Focuses on: NPC arcs, relationships, betrayals
     */
    private suspend fun generateCharacterProposal(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>
    ): AgentProposal? {
        val prompt = buildCharacterPrompt(state, existingGraph, recentEvents)
        val response = characterAgent.sendMessage(prompt).toList().joinToString("")

        return parseAgentProposal(response, "character_agent", state)
    }

    /**
     * World-focused planning agent
     * Focuses on: World events, factions, mysteries
     */
    private suspend fun generateWorldProposal(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>
    ): AgentProposal? {
        val prompt = buildWorldPrompt(state, existingGraph, recentEvents)
        val response = worldAgent.sendMessage(prompt).toList().joinToString("")

        return parseAgentProposal(response, "world_agent", state)
    }

    // ========================
    // Prompt Building
    // ========================

    private fun buildStoryPrompt(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>
    ): String {
        return """
            STORY PLANNING AGENT

            You are the Story Planner. Focus on the MAIN NARRATIVE arc.

            === CONTEXT ===
            Player Level: ${state.playerLevel}
            Grade: ${state.characterSheet.currentGrade.displayName}

            Recent Events:
            ${recentEvents.takeLast(5).joinToString("\n")}

            Existing Graph Stats:
            ${existingGraph?.getStats()?.let { "Nodes: ${it.totalNodes}, Completed: ${it.completedNodes}, Progress: ${(it.progressPercent * 100).toInt()}%" } ?: "No existing graph"}

            === YOUR TASK ===

            Propose 2-4 story beats focused on:
            - Main plot progression
            - Major revelations
            - Confrontations with antagonists
            - Escalating stakes
            - Transformative moments

            For each beat:
            1. Create compelling narrative moment
            2. Position in appropriate tier (based on level)
            3. Rate your confidence (0-1) in this beat
            4. Identify dependencies (what must happen first)
            5. Identify conflicts (what this prevents)

            Respond in JSON:
            {
                "beats": [
                    {
                        "title": "Beat title",
                        "description": "What happens",
                        "beatType": "REVELATION|CONFRONTATION|etc",
                        "triggerLevel": 35,
                        "tier": 2,
                        "sequence": 5,
                        "involvedNPCs": ["npc_id"],
                        "confidence": 0.85,
                        "dependencies": ["beat_id"],
                        "conflicts": ["beat_id"]
                    }
                ],
                "reasoning": "Why these beats?"
            }
        """.trimIndent()
    }

    private fun buildCharacterPrompt(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>
    ): String {
        val npcs = state.npcsByLocation.values.flatten()
        val relationships = npcs.map { npc ->
            "${npc.name}: ${npc.getRelationship(state.gameId).affinity}"
        }.take(5).joinToString(", ")

        return """
            CHARACTER PLANNING AGENT

            You are the Character Planner. Focus on NPC RELATIONSHIPS and personal arcs.

            === CONTEXT ===
            Player Level: ${state.playerLevel}

            Key Relationships:
            $relationships

            === YOUR TASK ===

            Propose 2-4 character-focused beats:
            - NPC personal arcs
            - Relationship developments
            - Betrayals or reunions
            - Character transformations

            Focus on emotional impact and character growth.

            Respond in JSON (same format as story agent).
        """.trimIndent()
    }

    private fun buildWorldPrompt(
        state: GameState,
        existingGraph: PlotGraph?,
        recentEvents: List<String>
    ): String {
        return """
            WORLD PLANNING AGENT

            You are the World Planner. Focus on WORLD EVENTS and mysteries.

            === CONTEXT ===
            World: ${state.worldSettings.worldName}
            Themes: ${state.worldSettings.themes.joinToString()}

            === YOUR TASK ===

            Propose 2-4 world-focused beats:
            - Faction conflicts
            - World-changing events
            - Mysteries revealed
            - New areas unlocked

            Think BIG PICTURE and long-term consequences.

            Respond in JSON (same format as story agent).
        """.trimIndent()
    }

    // ========================
    // Parsing & Graph Building
    // ========================

    private fun parseAgentProposal(
        response: String,
        agentId: String,
        state: GameState
    ): AgentProposal? {
        // TODO: Implement proper JSON parsing
        // For now, return placeholder
        return null
    }

    private fun buildGraphFromConsensus(
        consensus: ConsensusResult,
        existingGraph: PlotGraph?,
        gameId: String,
        mode: PlanningMode
    ): PlotGraph {
        val builder = PlotGraphBuilder(gameId)

        // Start with existing graph in incremental mode
        var graph = if (mode == PlanningMode.INCREMENTAL && existingGraph != null) {
            existingGraph
        } else {
            PlotGraph(gameId)
        }

        // Add all accepted nodes
        consensus.acceptedNodes.forEach { node ->
            graph = graph.addNode(node)
        }

        // Add all accepted edges
        consensus.acceptedEdges.forEach { edge ->
            graph = graph.addEdge(edge)
        }

        // Store alternatives as parallel branches
        consensus.alternatives.forEach { alternative ->
            alternative.nodes.forEach { node ->
                val alternativeNode = node.copy(
                    position = node.position.copy(branch = 1) // Mark as alternative
                )
                graph = graph.addNode(alternativeNode)
            }
        }

        return graph
    }

    // ========================
    // Helper Functions
    // ========================

    private fun determineTriggerReason(state: GameState, existingGraph: PlotGraph?): String {
        if (existingGraph == null) return "Initial planning"

        val stats = existingGraph.getStats()
        return when {
            stats.readyNodes < 3 -> "Running low on ready content"
            stats.progressPercent > 0.7f -> "Graph mostly complete"
            else -> "Scheduled replan"
        }
    }

    private fun calculateNextReplanLevel(state: GameState, graph: PlotGraph): Int {
        // Replan every 10-15 levels, or when running out of content
        val readyNodes = graph.nodes.values.count { !it.triggered && !it.abandoned }
        val levelsPerNode = 5 // Rough estimate

        val contentLevels = readyNodes * levelsPerNode
        val nextReplan = state.playerLevel + contentLevels.coerceIn(10, 15)

        return nextReplan
    }

    private fun createStoryPlannerAgent() = llm.startAgent(
        """
        You are the Story Planner agent.

        Focus on MAIN NARRATIVE progression:
        - Plot revelations
        - Confrontations
        - Escalating stakes
        - Major transformations

        Think in story arcs. Build toward climactic moments.
        """.trimIndent()
    )

    private fun createCharacterPlannerAgent() = llm.startAgent(
        """
        You are the Character Planner agent.

        Focus on NPC RELATIONSHIPS and personal arcs:
        - Character development
        - Relationship milestones
        - Betrayals and reunions
        - Emotional moments

        Make the player CARE about the characters.
        """.trimIndent()
    )

    private fun createWorldPlannerAgent() = llm.startAgent(
        """
        You are the World Planner agent.

        Focus on WORLD-SCALE events:
        - Faction conflicts
        - World mysteries
        - Environmental changes
        - New locations and secrets

        Think about the bigger picture beyond the player.
        """.trimIndent()
    )
}

/**
 * Planning mode
 */
@kotlinx.serialization.Serializable
internal enum class PlanningMode {
    FULL,         // Complete replan from scratch
    INCREMENTAL,  // Add to existing graph
    ADAPTIVE      // Replan after player deviation
}

/**
 * Result of planning process
 */
@kotlinx.serialization.Serializable
internal data class PlanningResult(
    val graph: PlotGraph,
    val proposals: List<AgentProposal>,
    val consensus: ConsensusResult,
    val mode: PlanningMode,
    val triggerReason: String,
    val nextReplanLevel: Int
)
