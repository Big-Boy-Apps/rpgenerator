package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Graph-based plot structure for branching narratives
 *
 * This system represents the narrative as a directed graph where:
 * - Nodes = Story beats (quests, encounters, revelations)
 * - Edges = Relationships between beats (dependencies, conflicts, alternatives)
 *
 * Supports:
 * - Multiple parallel story threads
 * - Branching paths based on player choices
 * - Conflict resolution (mutually exclusive beats)
 * - Dynamic replanning when player breaks expected flow
 */

/**
 * A node in the plot graph representing a discrete story beat
 */
@Serializable
internal data class PlotNode(
    val id: String,
    val beat: PlotBeat,
    val threadId: String, // Which plot thread this belongs to
    val position: GraphPosition, // Where in the graph structure
    val metadata: NodeMetadata = NodeMetadata()
) {
    fun isReadyToTrigger(state: GameState, graph: PlotGraph): Boolean {
        // Check if all prerequisite nodes are completed
        val prerequisites = graph.getIncomingDependencies(this.id)
        if (!prerequisites.all { it.completed }) {
            return false
        }

        // Check if any conflicting nodes are active or completed
        val conflicts = graph.getConflictingNodes(this.id)
        if (conflicts.any { it.triggered || it.completed }) {
            return false
        }

        // Check basic trigger conditions from beat
        return beat.triggerLevel <= state.playerLevel
    }

    val triggered: Boolean get() = metadata.triggered
    val completed: Boolean get() = metadata.completed
    val abandoned: Boolean get() = metadata.abandoned

    fun trigger(): PlotNode = copy(metadata = metadata.copy(triggered = true, triggeredAt = currentTimeMillis()))
    fun complete(): PlotNode = copy(metadata = metadata.copy(completed = true, completedAt = currentTimeMillis()))
    fun abandon(): PlotNode = copy(metadata = metadata.copy(abandoned = true, abandonedAt = currentTimeMillis()))
}

/**
 * Metadata about a node's lifecycle
 */
@Serializable
internal data class NodeMetadata(
    val triggered: Boolean = false,
    val completed: Boolean = false,
    val abandoned: Boolean = false,
    val triggeredAt: Long? = null,
    val completedAt: Long? = null,
    val abandonedAt: Long? = null,
    val playerChoiceMade: String? = null // What choice led to this path
)

/**
 * Position information for layout and visualization
 */
@Serializable
internal data class GraphPosition(
    val tier: Int, // Narrative tier (based on character grade/level range)
    val sequence: Int, // Order within tier
    val branch: Int = 0 // Which parallel branch (0 = main path)
)

/**
 * Edge representing relationship between nodes
 */
@Serializable
internal data class PlotEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val edgeType: EdgeType,
    val metadata: EdgeMetadata = EdgeMetadata()
) {
    fun isActive(): Boolean = !metadata.disabled

    fun disable(): PlotEdge = copy(metadata = metadata.copy(disabled = true))
    fun enable(): PlotEdge = copy(metadata = metadata.copy(disabled = false))
}

/**
 * Types of relationships between nodes
 */
@Serializable
internal enum class EdgeType {
    DEPENDENCY,     // toNode requires fromNode to complete first
    CONFLICT,       // toNode and fromNode are mutually exclusive
    ALTERNATIVE,    // toNode is an alternative path to fromNode
    ESCALATION,     // toNode escalates the situation from fromNode
    CONSEQUENCE,    // toNode is a consequence of fromNode
    FORESHADOWING,  // fromNode foreshadows toNode (soft connection)
    PARALLEL        // Both nodes can happen simultaneously
}

/**
 * Edge metadata
 */
@Serializable
internal data class EdgeMetadata(
    val weight: Float = 1.0f, // Importance/likelihood of this connection
    val disabled: Boolean = false, // Can be disabled if player invalidates this path
    val conditionDescription: String? = null // Human-readable explanation
)

/**
 * The complete plot graph
 */
@Serializable
internal data class PlotGraph(
    val gameId: String,
    val nodes: Map<String, PlotNode> = emptyMap(),
    val edges: Map<String, PlotEdge> = emptyMap(),
    val version: Int = 0, // Incremented each time graph is replanned
    val lastUpdated: Long = currentTimeMillis()
) {
    /**
     * Get all nodes that must complete before this node can trigger
     */
    fun getIncomingDependencies(nodeId: String): List<PlotNode> {
        return edges.values
            .filter { it.toNodeId == nodeId && it.edgeType == EdgeType.DEPENDENCY && it.isActive() }
            .mapNotNull { nodes[it.fromNodeId] }
    }

    /**
     * Get all nodes that this node blocks (due to conflicts)
     */
    fun getConflictingNodes(nodeId: String): List<PlotNode> {
        return edges.values
            .filter {
                (it.fromNodeId == nodeId || it.toNodeId == nodeId) &&
                it.edgeType == EdgeType.CONFLICT &&
                it.isActive()
            }
            .flatMap { edge ->
                listOfNotNull(
                    if (edge.fromNodeId == nodeId) nodes[edge.toNodeId] else null,
                    if (edge.toNodeId == nodeId) nodes[edge.fromNodeId] else null
                )
            }
    }

    /**
     * Get all nodes that can trigger now
     */
    fun getReadyNodes(state: GameState): List<PlotNode> {
        return nodes.values
            .filter { !it.triggered && !it.abandoned }
            .filter { it.isReadyToTrigger(state, this) }
            .sortedBy { it.position.sequence }
    }

    /**
     * Get active nodes (triggered but not completed)
     */
    fun getActiveNodes(): List<PlotNode> {
        return nodes.values
            .filter { it.triggered && !it.completed && !it.abandoned }
            .sortedBy { it.position.sequence }
    }

    /**
     * Get all possible next nodes from current position
     */
    fun getNextPossibleNodes(fromNodeId: String): List<PlotNode> {
        return edges.values
            .filter { it.fromNodeId == fromNodeId && it.isActive() }
            .filter { it.edgeType != EdgeType.CONFLICT } // Exclude conflicts
            .mapNotNull { nodes[it.toNodeId] }
            .filter { !it.abandoned }
    }

    /**
     * Find critical path through graph (highest priority nodes)
     */
    fun getCriticalPath(): List<PlotNode> {
        val criticalNodes = nodes.values
            .filter { !it.abandoned }
            .filter { it.beat.beatType in listOf(PlotBeatType.REVELATION, PlotBeatType.TRANSFORMATION, PlotBeatType.CONFRONTATION) }
            .sortedBy { it.position.sequence }

        // Build path following dependencies
        val path = mutableListOf<PlotNode>()
        var current: PlotNode? = criticalNodes.firstOrNull()

        while (current != null) {
            path.add(current)
            current = getNextPossibleNodes(current.id)
                .filter { it in criticalNodes }
                .minByOrNull { it.position.sequence }
        }

        return path
    }

    /**
     * Add a new node to the graph
     */
    fun addNode(node: PlotNode): PlotGraph {
        return copy(
            nodes = nodes + (node.id to node),
            version = version + 1,
            lastUpdated = currentTimeMillis()
        )
    }

    /**
     * Update an existing node
     */
    fun updateNode(nodeId: String, updater: (PlotNode) -> PlotNode): PlotGraph {
        val node = nodes[nodeId] ?: return this
        return copy(
            nodes = nodes + (nodeId to updater(node)),
            version = version + 1,
            lastUpdated = currentTimeMillis()
        )
    }

    /**
     * Add an edge between nodes
     */
    fun addEdge(edge: PlotEdge): PlotGraph {
        // Validate that both nodes exist
        if (!nodes.containsKey(edge.fromNodeId) || !nodes.containsKey(edge.toNodeId)) {
            return this
        }

        return copy(
            edges = edges + (edge.id to edge),
            version = version + 1,
            lastUpdated = currentTimeMillis()
        )
    }

    /**
     * Remove an edge
     */
    fun removeEdge(edgeId: String): PlotGraph {
        return copy(
            edges = edges - edgeId,
            version = version + 1,
            lastUpdated = currentTimeMillis()
        )
    }

    /**
     * Abandon a node and all its dependent nodes
     */
    fun abandonNode(nodeId: String): PlotGraph {
        val node = nodes[nodeId] ?: return this
        var graph = this.updateNode(nodeId) { it.abandon() }

        // Find all nodes that depend on this one
        val dependentNodeIds = edges.values
            .filter { it.fromNodeId == nodeId && it.edgeType == EdgeType.DEPENDENCY }
            .map { it.toNodeId }

        // Recursively abandon dependent nodes
        dependentNodeIds.forEach { dependentId ->
            graph = graph.abandonNode(dependentId)
        }

        return graph
    }

    /**
     * Prune abandoned paths from graph
     */
    fun pruneAbandoned(): PlotGraph {
        val activeNodeIds = nodes.values
            .filter { !it.abandoned }
            .map { it.id }
            .toSet()

        val activeEdges = edges.values
            .filter { it.fromNodeId in activeNodeIds && it.toNodeId in activeNodeIds }
            .associateBy { it.id }

        return copy(
            edges = activeEdges,
            version = version + 1,
            lastUpdated = currentTimeMillis()
        )
    }

    /**
     * Get statistics about the graph
     */
    fun getStats(): GraphStats {
        val nodeList = nodes.values
        return GraphStats(
            totalNodes = nodeList.size,
            triggeredNodes = nodeList.count { it.triggered },
            completedNodes = nodeList.count { it.completed },
            abandonedNodes = nodeList.count { it.abandoned },
            readyNodes = nodeList.count { !it.triggered && !it.abandoned },
            totalEdges = edges.size,
            activeEdges = edges.values.count { it.isActive() }
        )
    }
}

/**
 * Statistics about the plot graph
 */
@Serializable
internal data class GraphStats(
    val totalNodes: Int,
    val triggeredNodes: Int,
    val completedNodes: Int,
    val abandonedNodes: Int,
    val readyNodes: Int,
    val totalEdges: Int,
    val activeEdges: Int
) {
    val completionPercent: Float = if (totalNodes > 0) completedNodes.toFloat() / totalNodes else 0f
    val progressPercent: Float = if (totalNodes > 0) (triggeredNodes + completedNodes).toFloat() / totalNodes else 0f
}

/**
 * Helper to build plot graphs fluently
 */
internal class PlotGraphBuilder(private val gameId: String) {
    private val nodes = mutableMapOf<String, PlotNode>()
    private val edges = mutableMapOf<String, PlotEdge>()

    fun node(id: String, beat: PlotBeat, threadId: String, position: GraphPosition): PlotGraphBuilder {
        nodes[id] = PlotNode(id, beat, threadId, position)
        return this
    }

    fun dependency(fromId: String, toId: String): PlotGraphBuilder {
        val edgeId = "dep_${fromId}_${toId}"
        edges[edgeId] = PlotEdge(edgeId, fromId, toId, EdgeType.DEPENDENCY)
        return this
    }

    fun conflict(nodeId1: String, nodeId2: String): PlotGraphBuilder {
        val edgeId = "conflict_${nodeId1}_${nodeId2}"
        edges[edgeId] = PlotEdge(edgeId, nodeId1, nodeId2, EdgeType.CONFLICT)
        return this
    }

    fun alternative(nodeId1: String, nodeId2: String): PlotGraphBuilder {
        val edgeId = "alt_${nodeId1}_${nodeId2}"
        edges[edgeId] = PlotEdge(edgeId, nodeId1, nodeId2, EdgeType.ALTERNATIVE)
        return this
    }

    fun build(): PlotGraph {
        return PlotGraph(gameId, nodes, edges)
    }
}
