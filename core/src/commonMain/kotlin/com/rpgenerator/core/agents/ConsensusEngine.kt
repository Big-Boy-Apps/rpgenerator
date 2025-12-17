package com.rpgenerator.core.agents

import com.rpgenerator.core.domain.*
import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Consensus Engine - Resolves conflicts between competing agent proposals
 *
 * When multiple agents propose different plot threads or beats:
 * 1. Each agent rates proposals (confidence 0-1)
 * 2. Conflicts trigger discussion rounds
 * 3. Final decision made via weighted voting
 * 4. Minority opinions preserved as alternative branches
 */
internal class ConsensusEngine {

    /**
     * Resolve conflicts between agent proposals
     */
    fun resolveProposals(
        proposals: List<AgentProposal>,
        state: GameState
    ): ConsensusResult {
        // Group proposals by type
        val nodeProposals = proposals.flatMap { it.proposedNodes }
        val edgeProposals = proposals.flatMap { it.proposedEdges }

        // Identify conflicts
        val conflicts = identifyConflicts(nodeProposals, proposals)

        // If no conflicts, accept all proposals
        if (conflicts.isEmpty()) {
            return ConsensusResult(
                acceptedNodes = nodeProposals,
                acceptedEdges = edgeProposals,
                rejectedNodes = emptyList(),
                alternatives = emptyList(),
                conflicts = emptyList(),
                consensusType = ConsensusType.UNANIMOUS
            )
        }

        // Resolve each conflict via voting
        val resolutions = conflicts.map { conflict ->
            resolveConflict(conflict, proposals, state)
        }

        // Aggregate results
        val acceptedNodes = mutableListOf<PlotNode>()
        val rejectedNodes = mutableListOf<PlotNode>()
        val alternatives = mutableListOf<AlternativePath>()

        resolutions.forEach { resolution ->
            acceptedNodes.add(resolution.winner.node)
            rejectedNodes.addAll(resolution.rejected.map { it.node })

            // Preserve strong minority opinions as alternatives
            resolution.rejected
                .filter { it.confidence >= 0.7f }
                .forEach { rejected ->
                    alternatives.add(
                        AlternativePath(
                            nodes = listOf(rejected.node),
                            reason = "Alternative narrative path",
                            supportingAgents = rejected.supportingAgents,
                            averageConfidence = rejected.confidence
                        )
                    )
                }
        }

        return ConsensusResult(
            acceptedNodes = acceptedNodes + nodeProposals.filter { node ->
                !conflicts.any { conflict -> conflict.nodes.any { it.node.id == node.id } }
            },
            acceptedEdges = edgeProposals,
            rejectedNodes = rejectedNodes,
            alternatives = alternatives,
            conflicts = conflicts,
            consensusType = determineConsensusType(resolutions)
        )
    }

    /**
     * Identify conflicts between proposals
     */
    private fun identifyConflicts(
        nodes: List<PlotNode>,
        proposals: List<AgentProposal>
    ): List<NodeConflict> {
        val conflicts = mutableListOf<NodeConflict>()

        // Group nodes by position (same tier + similar sequence = potential conflict)
        val nodesByPosition = nodes.groupBy { it.position.tier }

        nodesByPosition.forEach { (tier, tierNodes) ->
            // Nodes in same tier with overlapping beat types might conflict
            tierNodes.forEachIndexed { i, node1 ->
                tierNodes.drop(i + 1).forEach { node2 ->
                    if (nodesConflict(node1, node2)) {
                        val ratings1 = getRatingsForNode(node1, proposals)
                        val ratings2 = getRatingsForNode(node2, proposals)

                        conflicts.add(
                            NodeConflict(
                                nodes = listOf(
                                    RatedNode(node1, ratings1.values.average().toFloat(), ratings1.keys.toList()),
                                    RatedNode(node2, ratings2.values.average().toFloat(), ratings2.keys.toList())
                                ),
                                conflictType = determineConflictType(node1, node2),
                                description = "Both nodes target similar narrative space in tier $tier"
                            )
                        )
                    }
                }
            }
        }

        return conflicts
    }

    /**
     * Check if two nodes conflict
     */
    private fun nodesConflict(node1: PlotNode, node2: PlotNode): Boolean {
        // Same position in graph = conflict
        if (node1.position == node2.position) return true

        // Similar beat types at similar times = might conflict
        if (node1.beat.beatType == node2.beat.beatType &&
            kotlin.math.abs(node1.beat.triggerLevel - node2.beat.triggerLevel) < 5) {
            return true
        }

        // Involve same NPCs = might conflict
        if (node1.beat.involvedNPCs.intersect(node2.beat.involvedNPCs).isNotEmpty()) {
            return true
        }

        return false
    }

    /**
     * Resolve a single conflict via voting
     */
    private fun resolveConflict(
        conflict: NodeConflict,
        proposals: List<AgentProposal>,
        state: GameState
    ): ConflictResolution {
        // Sort nodes by confidence rating
        val sortedNodes = conflict.nodes.sortedByDescending { it.confidence }

        // Winner is highest confidence
        val winner = sortedNodes.first()
        val rejected = sortedNodes.drop(1)

        return ConflictResolution(
            conflict = conflict,
            winner = winner,
            rejected = rejected,
            votingMethod = VotingMethod.CONFIDENCE_WEIGHTED,
            reasoning = "Selected node with highest average confidence (${winner.confidence})"
        )
    }

    /**
     * Get ratings for a specific node from all proposals
     */
    private fun getRatingsForNode(
        node: PlotNode,
        proposals: List<AgentProposal>
    ): Map<String, Float> {
        val ratings = mutableMapOf<String, Float>()

        proposals.forEach { proposal ->
            proposal.nodeRatings[node.id]?.let { rating ->
                ratings[proposal.agentId] = rating
            }
        }

        return ratings
    }

    /**
     * Determine type of consensus achieved
     */
    private fun determineConsensusType(resolutions: List<ConflictResolution>): ConsensusType {
        if (resolutions.isEmpty()) return ConsensusType.UNANIMOUS

        val avgMargin = resolutions.map { resolution ->
            if (resolution.rejected.isEmpty()) 1.0f
            else resolution.winner.confidence - resolution.rejected.first().confidence
        }.average().toFloat()

        return when {
            avgMargin > 0.5f -> ConsensusType.STRONG_MAJORITY
            avgMargin > 0.3f -> ConsensusType.MAJORITY
            avgMargin > 0.1f -> ConsensusType.WEAK_MAJORITY
            else -> ConsensusType.SPLIT
        }
    }

    /**
     * Determine the type of conflict
     */
    private fun determineConflictType(node1: PlotNode, node2: PlotNode): ConflictType {
        return when {
            node1.position == node2.position -> ConflictType.POSITION_OVERLAP
            node1.beat.beatType == node2.beat.beatType -> ConflictType.NARRATIVE_OVERLAP
            node1.beat.involvedNPCs.intersect(node2.beat.involvedNPCs).isNotEmpty() -> ConflictType.NPC_CONTENTION
            else -> ConflictType.THEMATIC_OVERLAP
        }
    }

    /**
     * Calculate priority score for a node based on multiple factors
     */
    fun calculateNodePriority(
        node: PlotNode,
        state: GameState,
        agentRatings: Map<String, Float>
    ): Float {
        var score = 0f

        // Agent confidence average
        score += agentRatings.values.average().toFloat() * 0.4f

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
        } * 0.3f

        // Level appropriateness (closer to current level = higher priority)
        val levelDiff = kotlin.math.abs(node.beat.triggerLevel - state.playerLevel)
        score += (1f - (levelDiff / 100f).coerceIn(0f, 1f)) * 0.2f

        // NPC relationship strength
        val npcStrength = node.beat.involvedNPCs
            .mapNotNull { npcId -> state.findNPC(npcId) }
            .map { npc -> npc.getRelationship(state.gameId).affinity / 100f }
            .maxOrNull() ?: 0f
        score += npcStrength * 0.1f

        return score.coerceIn(0f, 1f)
    }
}

/**
 * Proposal from a single planning agent
 */
@Serializable
internal data class AgentProposal(
    val agentId: String,
    val agentType: String, // "story", "character", "world", etc.
    val proposedNodes: List<PlotNode>,
    val proposedEdges: List<PlotEdge>,
    val nodeRatings: Map<String, Float>, // nodeId -> confidence (0-1)
    val reasoning: String,
    val timestamp: Long = currentTimeMillis()
)

/**
 * A node with its confidence rating
 */
@Serializable
internal data class RatedNode(
    val node: PlotNode,
    val confidence: Float, // 0-1
    val supportingAgents: List<String>
)

/**
 * Conflict between competing nodes
 */
@Serializable
internal data class NodeConflict(
    val nodes: List<RatedNode>,
    val conflictType: ConflictType,
    val description: String
)

/**
 * Types of conflicts
 */
@Serializable
internal enum class ConflictType {
    POSITION_OVERLAP,    // Same position in graph
    NARRATIVE_OVERLAP,   // Same beat type at similar time
    NPC_CONTENTION,      // Both need same NPC
    THEMATIC_OVERLAP     // Similar themes/topics
}

/**
 * Resolution of a conflict
 */
@Serializable
internal data class ConflictResolution(
    val conflict: NodeConflict,
    val winner: RatedNode,
    val rejected: List<RatedNode>,
    val votingMethod: VotingMethod,
    val reasoning: String
)

/**
 * Voting methods for conflict resolution
 */
@Serializable
internal enum class VotingMethod {
    CONFIDENCE_WEIGHTED,  // Use confidence scores
    AGENT_MAJORITY,       // Simple agent count
    PRIORITY_BASED,       // Use node priorities
    HYBRID                // Combination
}

/**
 * Result of consensus process
 */
@Serializable
internal data class ConsensusResult(
    val acceptedNodes: List<PlotNode>,
    val acceptedEdges: List<PlotEdge>,
    val rejectedNodes: List<PlotNode>,
    val alternatives: List<AlternativePath>,
    val conflicts: List<NodeConflict>,
    val consensusType: ConsensusType
)

/**
 * Alternative narrative path preserved from minority opinion
 */
@Serializable
internal data class AlternativePath(
    val nodes: List<PlotNode>,
    val reason: String,
    val supportingAgents: List<String>,
    val averageConfidence: Float
)

/**
 * Type of consensus achieved
 */
@Serializable
internal enum class ConsensusType {
    UNANIMOUS,       // All agents agree
    STRONG_MAJORITY, // Clear winner (>50% margin)
    MAJORITY,        // Moderate winner (>30% margin)
    WEAK_MAJORITY,   // Slight winner (>10% margin)
    SPLIT            // No clear consensus
}
