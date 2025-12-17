package com.rpgenerator.core.persistence

import com.rpgenerator.core.agents.*
import com.rpgenerator.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Repository for persisting plot graph and planning data
 */
internal class PlotGraphRepository(
    private val database: GameDatabase
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // ========== PlotGraph Persistence ==========

    /**
     * Save complete plot graph
     */
    suspend fun savePlotGraph(graph: com.rpgenerator.core.domain.PlotGraph) = withContext(Dispatchers.Default) {
        val graphJson = json.encodeToString(graph)

        database.transaction {
            // Save main graph
            database.gameQueries.insertPlotGraph(
                gameId = graph.gameId,
                graphJson = graphJson,
                version = graph.version.toLong(),
                lastUpdated = graph.lastUpdated
            )

            // Save individual nodes (denormalized for queries)
            graph.nodes.values.forEach { node ->
                database.gameQueries.insertPlotNode(
                    id = node.id,
                    gameId = graph.gameId,
                    threadId = node.threadId,
                    nodeJson = json.encodeToString(node),
                    tier = node.position.tier.toLong(),
                    sequence = node.position.sequence.toLong(),
                    branch = node.position.branch.toLong(),
                    beatType = node.beat.beatType.name,
                    triggerLevel = node.beat.triggerLevel.toLong(),
                    triggered = if (node.triggered) 1L else 0L,
                    completed = if (node.completed) 1L else 0L,
                    abandoned = if (node.abandoned) 1L else 0L,
                    createdAt = currentTimeMillis()
                )
            }

            // Save edges
            graph.edges.values.forEach { edge ->
                database.gameQueries.insertPlotEdge(
                    id = edge.id,
                    gameId = graph.gameId,
                    fromNodeId = edge.fromNodeId,
                    toNodeId = edge.toNodeId,
                    edgeType = edge.edgeType.name,
                    weight = edge.metadata.weight.toDouble(),
                    disabled = if (edge.metadata.disabled) 1L else 0L
                )
            }
        }
    }

    /**
     * Load complete plot graph
     */
    suspend fun loadPlotGraph(gameId: String): com.rpgenerator.core.domain.PlotGraph? = withContext(Dispatchers.Default) {
        database.gameQueries.selectPlotGraph(gameId)
            .executeAsOneOrNull()
            ?.let { row ->
                try {
                    json.decodeFromString<com.rpgenerator.core.domain.PlotGraph>(row.graphJson)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get ready plot nodes (can trigger now)
     */
    suspend fun getReadyNodes(gameId: String, currentLevel: Int): List<PlotNode> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectReadyPlotNodes(gameId, currentLevel.toLong())
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        json.decodeFromString<PlotNode>(row.nodeJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Get active plot nodes (triggered but not completed)
     */
    suspend fun getActiveNodes(gameId: String): List<PlotNode> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectActivePlotNodes(gameId)
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        json.decodeFromString<PlotNode>(row.nodeJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Update plot node status
     */
    suspend fun updateNodeStatus(
        gameId: String,
        nodeId: String,
        triggered: Boolean,
        completed: Boolean,
        abandoned: Boolean,
        updatedNode: com.rpgenerator.core.domain.PlotNode
    ) = withContext(Dispatchers.Default) {
        database.gameQueries.updatePlotNodeStatus(
            triggered = if (triggered) 1L else 0L,
            completed = if (completed) 1L else 0L,
            abandoned = if (abandoned) 1L else 0L,
            nodeJson = json.encodeToString(updatedNode),
            gameId = gameId,
            id = nodeId
        )
    }

    /**
     * Delete plot graph
     */
    suspend fun deletePlotGraph(gameId: String) = withContext(Dispatchers.Default) {
        database.transaction {
            database.gameQueries.deletePlotEdgesByGame(gameId)
            database.gameQueries.deletePlotNodesByGame(gameId)
            database.gameQueries.deletePlotGraph(gameId)
        }
    }

    // ========== Planning Session Persistence ==========

    /**
     * Save planning session
     */
    suspend fun savePlanningSession(
        sessionId: String,
        gameId: String,
        result: PlanningResult
    ) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()

        database.transaction {
            // Save session
            database.gameQueries.insertPlanningSession(
                id = sessionId,
                gameId = gameId,
                mode = result.mode.name,
                triggerReason = result.triggerReason,
                playerLevel = result.graph.nodes.values.firstOrNull()?.beat?.triggerLevel?.toLong() ?: 0L,
                graphVersion = result.graph.version.toLong(),
                nextReplanLevel = result.nextReplanLevel.toLong(),
                startedAt = now,
                completedAt = now
            )

            // Save agent proposals
            result.proposals.forEach { proposal ->
                database.gameQueries.insertAgentProposal(
                    gameId = gameId,
                    agentId = proposal.agentId,
                    agentType = proposal.agentType,
                    proposalJson = json.encodeToString(proposal),
                    planningSessionId = sessionId,
                    timestamp = proposal.timestamp
                )
            }

            // Save consensus result
            database.gameQueries.insertConsensusResult(
                gameId = gameId,
                planningSessionId = sessionId,
                consensusJson = json.encodeToString(result.consensus),
                consensusType = result.consensus.consensusType.name,
                conflictCount = result.consensus.conflicts.size.toLong(),
                timestamp = now
            )
        }
    }

    /**
     * Get last completed planning session
     */
    suspend fun getLastCompletedSession(gameId: String): PlanningSessionInfo? =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectLastCompletedSession(gameId)
                .executeAsOneOrNull()
                ?.let { row ->
                    PlanningSessionInfo(
                        id = row.id,
                        gameId = row.gameId,
                        mode = PlanningMode.valueOf(row.mode),
                        triggerReason = row.triggerReason,
                        playerLevel = row.playerLevel.toInt(),
                        graphVersion = row.graphVersion.toInt(),
                        nextReplanLevel = row.nextReplanLevel.toInt(),
                        startedAt = row.startedAt,
                        completedAt = row.completedAt
                    )
                }
        }

    /**
     * Get agent proposals for a session
     */
    suspend fun getProposalsForSession(sessionId: String): List<AgentProposal> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectProposalsBySession(sessionId)
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        json.decodeFromString<AgentProposal>(row.proposalJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Get consensus result for a session
     */
    suspend fun getConsensusForSession(sessionId: String): ConsensusResult? =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectConsensusResultBySession(sessionId)
                .executeAsOneOrNull()
                ?.let { row ->
                    try {
                        json.decodeFromString<ConsensusResult>(row.consensusJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Delete all planning data for a game
     */
    suspend fun deletePlanningData(gameId: String) = withContext(Dispatchers.Default) {
        database.transaction {
            database.gameQueries.deleteConsensusResultsByGame(gameId)
            database.gameQueries.deleteProposalsByGame(gameId)
            database.gameQueries.deletePlanningSessionsByGame(gameId)
        }
    }
}

/**
 * Planning session info
 */
internal data class PlanningSessionInfo(
    val id: String,
    val gameId: String,
    val mode: PlanningMode,
    val triggerReason: String,
    val playerLevel: Int,
    val graphVersion: Int,
    val nextReplanLevel: Int,
    val startedAt: Long,
    val completedAt: Long?
)
