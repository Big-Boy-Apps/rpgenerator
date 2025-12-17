package com.rpgenerator.core.debug

import kotlinx.serialization.Serializable

/**
 * Complete debug view of game state for visualization.
 *
 * This provides a comprehensive snapshot of:
 * - Player progress and stats
 * - Plot progression and planning
 * - Agent activity and memory
 * - NPCs and relationships
 * - Quests and locations
 * - Recent events
 */
@Serializable
data class GameDebugView(
    val gameId: String,
    val playerName: String,
    val timestamp: Long,

    // Player overview
    val player: PlayerDebugInfo,

    // Plot system
    val plot: PlotDebugInfo,

    // Agent system
    val agents: AgentDebugInfo,

    // World state
    val world: WorldDebugInfo,

    // Recent activity
    val recentActivity: RecentActivityDebugInfo
)

@Serializable
data class PlayerDebugInfo(
    val level: Int,
    val grade: String,
    val xp: Int,
    val xpToNextLevel: Int,
    val playerClass: String,
    val classEvolutionPath: List<String>,
    val stats: Map<String, Int>,
    val hp: String, // "current/max"
    val mana: String, // "current/max"
    val energy: String, // "current/max"
    val deathCount: Int,
    val playtime: Long, // seconds
    val unspentStatPoints: Int,
    val activeStatusEffects: Int,
    val inventoryItemCount: Int,
    val equippedItems: Map<String, String?>, // slot -> item name
    val skillCount: Int
)

@Serializable
data class PlotDebugInfo(
    val graphVersion: Int,
    val totalNodes: Int,
    val triggeredNodes: Int,
    val completedNodes: Int,
    val abandonedNodes: Int,
    val readyNodes: Int, // Ready to trigger
    val activeThreads: List<PlotThreadSummary>,
    val nodesByTier: Map<Int, Int>, // tier -> count
    val nodesByBeatType: Map<String, Int>, // beat type -> count
    val criticalPathLength: Int,
    val playerProgressPercent: Double, // completed / total
    val planningSessions: Int,
    val lastReplanLevel: Int?,
    val nextReplanLevel: Int?
)

@Serializable
data class PlotThreadSummary(
    val id: String,
    val name: String,
    val category: String,
    val priority: String,
    val status: String,
    val beatCount: Int,
    val triggeredBeats: Int,
    val completedBeats: Int
)

@Serializable
data class AgentDebugInfo(
    val agentMemories: List<AgentMemorySummary>,
    val totalActions: Int,
    val actionsByType: Map<String, Int>,
    val actionsByAgent: Map<String, Int>,
    val recentActions: List<AgentActionSummary>,
    val consolidations: Int,
    val discussions: Int,
    val discussionsByConsensusType: Map<String, Int>,
    val averageDiscussionRounds: Double
)

@Serializable
data class AgentMemorySummary(
    val agentId: String,
    val messageCount: Int,
    val tokenEstimate: Int,
    val consolidatedContextSize: Int,
    val lastConsolidated: Long?,
    val lastUpdated: Long
)

@Serializable
data class AgentActionSummary(
    val agentId: String,
    val actionType: String,
    val reasoning: String,
    val playerLevel: Int,
    val timestamp: Long
)

@Serializable
data class WorldDebugInfo(
    val currentLocation: LocationSummary,
    val discoveredLocations: Int,
    val customLocations: Int,
    val totalNPCs: Int,
    val npcsByLocation: Map<String, Int>,
    val npcRelationships: List<NPCRelationshipSummary>,
    val merchants: Int,
    val activeQuests: Int,
    val completedQuests: Int,
    val questsByType: Map<String, Int>
)

@Serializable
data class LocationSummary(
    val name: String,
    val type: String, // template or custom
    val npcCount: Int,
    val questCount: Int
)

@Serializable
data class NPCRelationshipSummary(
    val npcId: String,
    val npcName: String,
    val affinity: Int,
    val status: String,
    val conversationCount: Int,
    val hasShop: Boolean,
    val activeQuests: Int
)

@Serializable
data class RecentActivityDebugInfo(
    val recentEvents: List<EventSummary>,
    val eventCountByCategory: Map<String, Int>,
    val eventCountByImportance: Map<String, Int>,
    val recentPlotBeats: List<PlotBeatTrigger>,
    val recentNPCInteractions: List<NPCInteraction>
)

@Serializable
data class EventSummary(
    val eventType: String,
    val category: String,
    val importance: String,
    val timestamp: Long,
    val involvedEntities: Map<String, String?> // npcId, locationId, questId, itemId
)

@Serializable
data class PlotBeatTrigger(
    val beatTitle: String,
    val beatType: String,
    val threadName: String,
    val triggerLevel: Int,
    val timestamp: Long
)

@Serializable
data class NPCInteraction(
    val npcName: String,
    val interactionType: String, // conversation, quest, trade
    val affinityChange: Int,
    val timestamp: Long
)

/**
 * Formatted text output for terminal display.
 */
data class FormattedDebugView(
    val sections: List<DebugSection>
)

data class DebugSection(
    val title: String,
    val content: List<String>,
    val subsections: List<DebugSection> = emptyList()
)
