package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * Metadata about a saved game.
 * Returned by RPGClient.getGames()
 */
@Serializable
data class GameInfo(
    val id: String,
    val playerName: String,
    val systemType: SystemType,
    val difficulty: Difficulty,
    val level: Int,
    val playtime: Long, // seconds
    val lastPlayedAt: Long, // unix timestamp
    val createdAt: Long // unix timestamp
)
