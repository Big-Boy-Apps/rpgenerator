package com.rpgenerator.core.persistence

import com.rpgenerator.core.api.*
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.NPC
import com.rpgenerator.core.domain.Quest
import com.rpgenerator.core.domain.QuestProgressStatus
import com.rpgenerator.core.domain.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Repository for persisting and loading game data.
 */
internal class GameRepository(
    private val database: GameDatabase
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Get all saved games.
     */
    suspend fun getAllGames(): List<GameInfo> = withContext(Dispatchers.Default) {
        database.gameQueries.selectAll().executeAsList().map { game ->
            GameInfo(
                id = game.id,
                playerName = game.playerName,
                systemType = SystemType.valueOf(game.systemType),
                difficulty = Difficulty.valueOf(game.difficulty),
                level = game.level.toInt(),
                playtime = game.playtime,
                lastPlayedAt = game.lastPlayedAt,
                createdAt = game.createdAt
            )
        }
    }

    /**
     * Get game metadata by ID.
     */
    suspend fun getGame(gameId: String): GameInfo? = withContext(Dispatchers.Default) {
        database.gameQueries.selectById(gameId).executeAsOneOrNull()?.let { game ->
            GameInfo(
                id = game.id,
                playerName = game.playerName,
                systemType = SystemType.valueOf(game.systemType),
                difficulty = Difficulty.valueOf(game.difficulty),
                level = game.level.toInt(),
                playtime = game.playtime,
                lastPlayedAt = game.lastPlayedAt,
                createdAt = game.createdAt
            )
        }
    }

    /**
     * Load game state from database.
     * Loads the base state from JSON, then populates NPCs, quests, and custom locations from DB.
     */
    suspend fun loadGameState(gameId: String): GameState? = withContext(Dispatchers.Default) {
        val baseState = database.gameQueries.selectState(gameId).executeAsOneOrNull()?.let { state ->
            json.decodeFromString<GameState>(state.stateJson)
        } ?: return@withContext null

        // Load NPCs from database and group by location
        val npcs = getAllNPCs(gameId)
        val npcsByLocation = npcs.groupBy { it.locationId }

        // Load quests from database and partition by status
        val quests = getAllQuests(gameId)
        val activeQuests = quests
            .filter { it.status == QuestProgressStatus.IN_PROGRESS || it.status == QuestProgressStatus.NOT_STARTED }
            .associateBy { it.id }
        val completedQuests = quests
            .filter { it.status == QuestProgressStatus.COMPLETED }
            .map { it.id }
            .toSet()

        // Load custom locations from database
        val customLocations = getAllCustomLocations(gameId).associateBy { it.id }

        // Update state with loaded data
        baseState.copy(
            npcsByLocation = npcsByLocation,
            activeQuests = activeQuests,
            completedQuests = completedQuests,
            customLocations = customLocations
        )
    }

    /**
     * Save a new game.
     */
    suspend fun createGame(
        gameId: String,
        playerName: String,
        config: GameConfig,
        initialState: GameState
    ) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis() / 1000

        database.transaction {
            // Insert game metadata
            database.gameQueries.insert(
                id = gameId,
                playerName = playerName,
                systemType = config.systemType.name,
                difficulty = config.difficulty.name,
                level = 1L,
                playtime = 0L,
                lastPlayedAt = now,
                createdAt = now
            )

            // Insert initial game state
            database.gameQueries.insertState(
                gameId = gameId,
                stateJson = json.encodeToString(initialState)
            )
        }
    }

    /**
     * Save game state and update metadata.
     * Persists NPCs, quests, and custom locations separately from the main state.
     */
    suspend fun saveGame(
        gameId: String,
        state: GameState,
        playtime: Long
    ) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis() / 1000

        database.transaction {
            // Update metadata
            database.gameQueries.updateMetadata(
                level = state.playerLevel.toLong(),
                playtime = playtime,
                lastPlayedAt = now,
                id = gameId
            )

            // Update game state (without NPCs/quests/customLocations which are persisted separately)
            database.gameQueries.insertState(
                gameId = gameId,
                stateJson = json.encodeToString(state)
            )
        }

        // Save all NPCs (outside transaction since saveNPC uses withContext)
        state.npcsByLocation.values.flatten().forEach { npc ->
            saveNPC(gameId, npc)
        }

        // Save all quests
        state.activeQuests.values.forEach { quest ->
            saveQuest(gameId, quest)
        }
        state.completedQuests.forEach { questId ->
            // Load the quest from DB to get full data, or skip if not found
            val quest = getQuest(gameId, questId)
            if (quest != null) {
                saveQuest(gameId, quest.copy(status = QuestProgressStatus.COMPLETED))
            }
        }

        // Save all custom locations
        state.customLocations.values.forEach { location ->
            saveCustomLocation(gameId, location)
        }
    }

    /**
     * Delete a game and all associated data.
     */
    suspend fun deleteGame(gameId: String) = withContext(Dispatchers.Default) {
        database.transaction {
            database.gameQueries.deleteEvents(gameId)
            database.gameQueries.deleteNPCsByGame(gameId)
            database.gameQueries.deleteQuestsByGame(gameId)
            database.gameQueries.deleteCustomLocationsByGame(gameId)
            database.gameQueries.deleteState(gameId)
            database.gameQueries.delete(gameId)
        }
    }

    /**
     * Log a game event.
     */
    suspend fun logEvent(gameId: String, event: GameEvent) = withContext(Dispatchers.Default) {
        val eventType = event::class.simpleName ?: "Unknown"
        val eventJson = json.encodeToString(event)
        val timestamp = currentTimeMillis() / 1000

        // Infer metadata from event
        val metadata = com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, gameId)

        database.gameQueries.insertEvent(
            gameId = gameId,
            eventType = eventType,
            eventJson = eventJson,
            timestamp = timestamp,
            category = metadata.category.name,
            importance = metadata.importance.name,
            searchableText = metadata.searchableText,
            npcId = metadata.npcId,
            locationId = metadata.locationId,
            questId = metadata.questId,
            itemId = metadata.itemId
        )
    }

    /**
     * Get recent game events.
     */
    suspend fun getRecentEvents(gameId: String, limit: Long = 10): List<GameEvent> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectRecentEvents(gameId, limit)
                .executeAsList()
                .mapNotNull { event ->
                    try {
                        json.decodeFromString<GameEvent>(event.eventJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    // ========== NPC Persistence ==========

    /**
     * Save an NPC to the database.
     */
    suspend fun saveNPC(gameId: String, npc: NPC) = withContext(Dispatchers.Default) {
        val npcJson = json.encodeToString(npc)
        database.gameQueries.insertNPC(
            id = npc.id,
            gameId = gameId,
            locationId = npc.locationId,
            npcJson = npcJson
        )
    }

    /**
     * Get an NPC by ID.
     */
    suspend fun getNPC(gameId: String, npcId: String): NPC? = withContext(Dispatchers.Default) {
        database.gameQueries.selectNPCById(gameId, npcId)
            .executeAsOneOrNull()
            ?.let { row ->
                try {
                    json.decodeFromString<NPC>(row.npcJson)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get all NPCs for a game.
     */
    suspend fun getAllNPCs(gameId: String): List<NPC> = withContext(Dispatchers.Default) {
        database.gameQueries.selectNPCsByGame(gameId)
            .executeAsList()
            .mapNotNull { row ->
                try {
                    json.decodeFromString<NPC>(row.npcJson)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get NPCs at a specific location.
     */
    suspend fun getNPCsAtLocation(gameId: String, locationId: String): List<NPC> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectNPCsByLocation(gameId, locationId)
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        json.decodeFromString<NPC>(row.npcJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Delete an NPC.
     */
    suspend fun deleteNPC(gameId: String, npcId: String) = withContext(Dispatchers.Default) {
        database.gameQueries.deleteNPC(gameId, npcId)
    }

    // ========== Quest Persistence ==========

    /**
     * Save a quest to the database.
     */
    suspend fun saveQuest(gameId: String, quest: Quest) = withContext(Dispatchers.Default) {
        val questJson = json.encodeToString(quest)
        database.gameQueries.insertQuest(
            id = quest.id,
            gameId = gameId,
            questJson = questJson,
            status = quest.status.name
        )
    }

    /**
     * Get a quest by ID.
     */
    suspend fun getQuest(gameId: String, questId: String): Quest? = withContext(Dispatchers.Default) {
        database.gameQueries.selectQuestById(gameId, questId)
            .executeAsOneOrNull()
            ?.let { row ->
                try {
                    json.decodeFromString<Quest>(row.questJson)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get all quests for a game.
     */
    suspend fun getAllQuests(gameId: String): List<Quest> = withContext(Dispatchers.Default) {
        database.gameQueries.selectQuestsByGame(gameId)
            .executeAsList()
            .mapNotNull { row ->
                try {
                    json.decodeFromString<Quest>(row.questJson)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Get quests by status.
     */
    suspend fun getQuestsByStatus(gameId: String, status: QuestProgressStatus): List<Quest> =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectQuestsByStatus(gameId, status.name)
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        json.decodeFromString<Quest>(row.questJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Delete a quest.
     */
    suspend fun deleteQuest(gameId: String, questId: String) = withContext(Dispatchers.Default) {
        database.gameQueries.deleteQuest(gameId, questId)
    }

    // ========== Custom Location Persistence ==========

    /**
     * Save a custom location to the database.
     */
    suspend fun saveCustomLocation(gameId: String, location: Location) = withContext(Dispatchers.Default) {
        val locationJson = json.encodeToString(location)
        database.gameQueries.insertCustomLocation(
            id = location.id,
            gameId = gameId,
            locationJson = locationJson
        )
    }

    /**
     * Get a custom location by ID.
     */
    suspend fun getCustomLocation(gameId: String, locationId: String): Location? =
        withContext(Dispatchers.Default) {
            database.gameQueries.selectCustomLocationById(gameId, locationId)
                .executeAsOneOrNull()
                ?.let { row ->
                    try {
                        json.decodeFromString<Location>(row.locationJson)
                    } catch (e: Exception) {
                        null
                    }
                }
        }

    /**
     * Get all custom locations for a game.
     */
    suspend fun getAllCustomLocations(gameId: String): List<Location> = withContext(Dispatchers.Default) {
        database.gameQueries.selectCustomLocationsByGame(gameId)
            .executeAsList()
            .mapNotNull { row ->
                try {
                    json.decodeFromString<Location>(row.locationJson)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Delete a custom location.
     */
    suspend fun deleteCustomLocation(gameId: String, locationId: String) =
        withContext(Dispatchers.Default) {
            database.gameQueries.deleteCustomLocation(gameId, locationId)
        }
}
