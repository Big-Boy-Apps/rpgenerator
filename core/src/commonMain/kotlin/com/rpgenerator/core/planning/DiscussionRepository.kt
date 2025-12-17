package com.rpgenerator.core.planning

import com.rpgenerator.core.persistence.GameDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.randomUUID
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Repository for persisting multi-round agent discussion data.
 *
 * Stores:
 * - Conflicts requiring discussion
 * - Individual rounds of discussion
 * - Agent arguments within rounds
 * - Final consensus results
 * - Complete discussion histories
 */
internal class DiscussionRepository(private val db: GameDatabase) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // ========================
    // Conflict Operations
    // ========================

    /**
     * Create a new conflict requiring discussion.
     */
    fun createConflict(
        conflict: PlotConflict,
        planningSessionId: String,
        gameId: String
    ) {
        db.gameQueries.insertConflict(
            id = conflict.id,
            gameId = gameId,
            planningSessionId = planningSessionId,
            conflictType = conflict.conflictType.name,
            conflictJson = json.encodeToString(conflict),
            status = "ACTIVE",
            createdAt = currentTimeMillis(),
            resolvedAt = null
        )
    }

    /**
     * Get conflict by ID.
     */
    fun getConflict(conflictId: String): PlotConflict? {
        val row = db.gameQueries.selectConflictById(conflictId).executeAsOneOrNull()
            ?: return null

        return json.decodeFromString(row.conflictJson)
    }

    /**
     * Get all active conflicts for a game.
     */
    fun getActiveConflicts(gameId: String): List<PlotConflict> {
        return db.gameQueries.selectActiveConflicts(gameId)
            .executeAsList()
            .map { json.decodeFromString(it.conflictJson) }
    }

    /**
     * Mark conflict as resolved.
     */
    fun resolveConflict(conflictId: String) {
        db.gameQueries.updateConflictStatus(
            status = "RESOLVED",
            resolvedAt = currentTimeMillis(),
            id = conflictId
        )
    }

    // ========================
    // Discussion Round Operations
    // ========================

    /**
     * Save a discussion round.
     */
    fun saveRound(
        round: DiscussionRound,
        conflictId: String,
        gameId: String
    ) {
        val roundId = randomUUID()

        db.gameQueries.insertRound(
            id = roundId,
            conflictId = conflictId,
            gameId = gameId,
            roundNumber = round.roundNumber.toLong(),
            roundJson = json.encodeToString(round),
            contextFactorsJson = json.encodeToString(round.contextFactors),
            consensusReached = if (round.consensusReached) 1L else 0L,
            chosenProposalId = round.chosenProposal,
            timestamp = round.timestamp
        )

        // Save individual arguments
        round.arguments.forEach { (_, argument) ->
            saveArgument(argument, roundId, conflictId, gameId)
        }
    }

    /**
     * Get all rounds for a conflict.
     */
    fun getRounds(conflictId: String): List<DiscussionRound> {
        return db.gameQueries.selectRoundsByConflict(conflictId)
            .executeAsList()
            .map { json.decodeFromString(it.roundJson) }
    }

    /**
     * Get the latest round for a conflict.
     */
    fun getLatestRound(conflictId: String): DiscussionRound? {
        val row = db.gameQueries.selectLatestRound(conflictId).executeAsOneOrNull()
            ?: return null

        return json.decodeFromString(row.roundJson)
    }

    // ========================
    // Agent Argument Operations
    // ========================

    /**
     * Save an agent argument.
     */
    private fun saveArgument(
        argument: AgentArgument,
        roundId: String,
        conflictId: String,
        gameId: String
    ) {
        db.gameQueries.insertArgument(
            id = randomUUID(),
            roundId = roundId,
            conflictId = conflictId,
            gameId = gameId,
            agentType = argument.agentType,
            position = argument.position.name,
            supportedProposalId = argument.supportedProposalId,
            reasoning = argument.reasoning,
            confidence = argument.confidence,
            keyPoints = json.encodeToString(argument.keyPoints),
            contextualJustification = argument.contextualJustification,
            rebuttalTo = argument.rebuttalTo.joinToString(","),
            timestamp = currentTimeMillis()
        )
    }

    /**
     * Get all arguments for a specific round.
     */
    fun getArgumentsByRound(roundId: String): List<AgentArgument> {
        return db.gameQueries.selectArgumentsByRound(roundId)
            .executeAsList()
            .map { row ->
                AgentArgument(
                    agentType = row.agentType,
                    position = ArgumentPosition.valueOf(row.position),
                    supportedProposalId = row.supportedProposalId,
                    reasoning = row.reasoning,
                    confidence = row.confidence,
                    rebuttalTo = row.rebuttalTo?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                    keyPoints = json.decodeFromString(row.keyPoints),
                    contextualJustification = row.contextualJustification
                )
            }
    }

    /**
     * Get all arguments by a specific agent type across all discussions.
     */
    fun getArgumentsByAgent(gameId: String, agentType: String, limit: Long = 20): List<AgentArgument> {
        return db.gameQueries.selectArgumentsByAgent(gameId, agentType, limit)
            .executeAsList()
            .map { row ->
                AgentArgument(
                    agentType = row.agentType,
                    position = ArgumentPosition.valueOf(row.position),
                    supportedProposalId = row.supportedProposalId,
                    reasoning = row.reasoning,
                    confidence = row.confidence,
                    rebuttalTo = row.rebuttalTo?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                    keyPoints = json.decodeFromString(row.keyPoints),
                    contextualJustification = row.contextualJustification
                )
            }
    }

    // ========================
    // Consensus Operations
    // ========================

    /**
     * Save consensus result.
     */
    fun saveConsensus(
        consensus: ConsensusResult,
        conflictId: String,
        gameId: String
    ) {
        db.gameQueries.insertConsensus(
            id = randomUUID(),
            conflictId = conflictId,
            gameId = gameId,
            consensusJson = json.encodeToString(consensus),
            consensusType = consensus.consensusType.name,
            chosenProposalId = consensus.chosenProposal.id,
            roundsUsed = consensus.roundsUsed.toLong(),
            implementationNotes = consensus.implementationNotes,
            minorityOpinions = json.encodeToString(consensus.minorityOpinions),
            timestamp = consensus.timestamp
        )

        // Mark conflict as resolved
        resolveConflict(conflictId)
    }

    /**
     * Get consensus result for a conflict.
     */
    fun getConsensus(conflictId: String): ConsensusResult? {
        val row = db.gameQueries.selectConsensusByConflict(conflictId).executeAsOneOrNull()
            ?: return null

        return json.decodeFromString(row.consensusJson)
    }

    /**
     * Get recent consensus results.
     */
    fun getRecentConsensuses(gameId: String, limit: Long = 10): List<ConsensusResult> {
        return db.gameQueries.selectRecentConsensuses(gameId, limit)
            .executeAsList()
            .map { json.decodeFromString(it.consensusJson) }
    }

    // ========================
    // Discussion History Operations
    // ========================

    /**
     * Save complete discussion history.
     */
    fun saveHistory(
        history: DiscussionHistory,
        gameId: String
    ) {
        db.gameQueries.insertHistory(
            id = randomUUID(),
            conflictId = history.conflictId,
            gameId = gameId,
            historyJson = json.encodeToString(history),
            totalDuration = history.totalDuration,
            timestamp = currentTimeMillis()
        )
    }

    /**
     * Get discussion history for a conflict.
     */
    fun getHistory(conflictId: String): DiscussionHistory? {
        val row = db.gameQueries.selectHistoryByConflict(conflictId).executeAsOneOrNull()
            ?: return null

        return json.decodeFromString(row.historyJson)
    }

    /**
     * Get recent discussion histories.
     */
    fun getRecentHistories(gameId: String, limit: Long = 10): List<DiscussionHistory> {
        return db.gameQueries.selectRecentHistories(gameId, limit)
            .executeAsList()
            .map { json.decodeFromString(it.historyJson) }
    }

    // ========================
    // Analysis Queries
    // ========================

    /**
     * Get discussion statistics for a game.
     */
    fun getDiscussionStats(gameId: String): DiscussionStats {
        val conflicts = db.gameQueries.selectConflictsByGame(gameId).executeAsList()
        val consensuses = db.gameQueries.selectRecentConsensuses(gameId, Long.MAX_VALUE).executeAsList()

        val totalConflicts = conflicts.size
        val resolvedConflicts = conflicts.count { it.status == "RESOLVED" }
        val avgRounds = consensuses.map { it.roundsUsed }.average().let { if (it.isNaN()) 0.0 else it }

        val consensusTypeBreakdown = consensuses
            .groupBy { it.consensusType }
            .mapValues { it.value.size }

        return DiscussionStats(
            totalConflicts = totalConflicts,
            resolvedConflicts = resolvedConflicts,
            activeConflicts = totalConflicts - resolvedConflicts,
            averageRoundsToConsensus = avgRounds,
            consensusTypeBreakdown = consensusTypeBreakdown
        )
    }
}

/**
 * Statistics about agent discussions.
 */
data class DiscussionStats(
    val totalConflicts: Int,
    val resolvedConflicts: Int,
    val activeConflicts: Int,
    val averageRoundsToConsensus: Double,
    val consensusTypeBreakdown: Map<String, Int>
)
