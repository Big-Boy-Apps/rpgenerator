package com.rpgenerator.core.skill

import kotlinx.serialization.Serializable

/**
 * Skill rarity tiers - affects XP requirements, power scaling, and visual presentation.
 */
@Serializable
internal enum class SkillRarity(
    val displayName: String,
    val colorCode: String,  // For terminal display
    val xpMultiplier: Double,  // Higher rarity = more XP needed per level
    val powerMultiplier: Double,  // Higher rarity = stronger base effects
    val symbol: String  // Short display symbol
) {
    COMMON(
        displayName = "Common",
        colorCode = "white",
        xpMultiplier = 1.0,
        powerMultiplier = 1.0,
        symbol = "C"
    ),
    UNCOMMON(
        displayName = "Uncommon",
        colorCode = "green",
        xpMultiplier = 1.2,
        powerMultiplier = 1.15,
        symbol = "U"
    ),
    RARE(
        displayName = "Rare",
        colorCode = "blue",
        xpMultiplier = 1.5,
        powerMultiplier = 1.35,
        symbol = "R"
    ),
    EPIC(
        displayName = "Epic",
        colorCode = "magenta",
        xpMultiplier = 2.0,
        powerMultiplier = 1.6,
        symbol = "E"
    ),
    LEGENDARY(
        displayName = "Legendary",
        colorCode = "yellow",
        xpMultiplier = 3.0,
        powerMultiplier = 2.0,
        symbol = "L"
    ),
    MYTHIC(
        displayName = "Mythic",
        colorCode = "red",
        xpMultiplier = 5.0,
        powerMultiplier = 2.5,
        symbol = "M"
    );

    /**
     * Calculate XP needed for a given skill level.
     * Base: 100 XP per level, scaled by rarity.
     */
    fun xpForLevel(level: Int): Long = (level * 100L * xpMultiplier).toLong()

    /**
     * Apply power multiplier to a base value.
     */
    fun scalePower(baseValue: Int): Int = (baseValue * powerMultiplier).toInt()
}
