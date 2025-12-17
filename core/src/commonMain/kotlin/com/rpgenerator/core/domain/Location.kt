package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable

@Serializable
internal data class Location(
    val id: String,
    val name: String,
    val zoneId: String,
    val biome: Biome,
    val description: String,
    val danger: Int,
    val connections: List<String>,
    val features: List<String>,
    val lore: String
)

@Serializable
internal data class Zone(
    val id: String,
    val name: String,
    val recommendedLevel: Int,
    val description: String,
    val lore: String
)

@Serializable
internal enum class Biome {
    FOREST,
    MOUNTAINS,
    CAVE,
    DUNGEON,
    RUINS,
    SETTLEMENT,
    WASTELAND,
    JUNGLE,
    TUNDRA,
    DESERT,
    COSMIC_VOID,
    TUTORIAL_ZONE
}
