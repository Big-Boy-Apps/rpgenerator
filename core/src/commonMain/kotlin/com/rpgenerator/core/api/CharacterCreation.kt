package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * Character creation options for starting a new game.
 * Can be provided in GameConfig.playerPreferences as JSON, or passed separately.
 */
@Serializable
data class CharacterCreationOptions(
    /** Character name */
    val name: String = "Adventurer",

    /** Character backstory/description (optional) */
    val backstory: String? = null,

    /** Stat allocation method */
    val statAllocation: StatAllocation = StatAllocation.BALANCED,

    /** Custom stat values (if using CUSTOM allocation) */
    val customStats: CustomStats? = null,

    /** Starting class/archetype (system-dependent, optional) */
    val startingClass: String? = null,

    /** Character appearance/description for roleplay */
    val appearance: String? = null,

    /** Starting equipment preferences */
    val equipmentPreference: EquipmentPreference = EquipmentPreference.BALANCED
)

/**
 * Method for allocating starting stats.
 */
@Serializable
enum class StatAllocation {
    /** Balanced stats (10 in everything) */
    BALANCED,

    /** Focus on physical combat (STR, DEX, CON) */
    WARRIOR,

    /** Focus on magic (INT, WIS) */
    MAGE,

    /** Focus on agility and skills (DEX, INT, CHA) */
    ROGUE,

    /** Tank build (CON, STR) */
    TANK,

    /** Point-buy system with total budget */
    POINT_BUY,

    /** Completely custom stats */
    CUSTOM,

    /** Random stats (3d6 per stat, classic D&D style) */
    RANDOM
}

/**
 * Custom stat values for CUSTOM allocation.
 * Total should not exceed 70 for balance (6 stats * ~12 average).
 */
@Serializable
data class CustomStats(
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10
) {
    /** Validate that stats are within reasonable bounds */
    fun isValid(): Boolean {
        val total = strength + dexterity + constitution + intelligence + wisdom + charisma
        return total in 30..90 && // Total points reasonable
               listOf(strength, dexterity, constitution, intelligence, wisdom, charisma)
                   .all { it in 3..18 } // Individual stats within D&D range
    }

    /** Get total stat points */
    fun total(): Int = strength + dexterity + constitution + intelligence + wisdom + charisma
}

/**
 * Preference for starting equipment.
 */
@Serializable
enum class EquipmentPreference {
    /** Balanced loadout */
    BALANCED,

    /** Focus on weapons and armor */
    COMBAT,

    /** Focus on utility items and tools */
    UTILITY,

    /** Focus on consumables (potions, scrolls) */
    CONSUMABLES,

    /** Minimal equipment, more starting gold */
    GOLD
}

/**
 * Result of character creation, including generated stats and backstory.
 */
@Serializable
data class CharacterCreationResult(
    val name: String,
    val stats: Stats,
    val backstory: String,
    val startingEquipment: List<String>,
    val specialAbilities: List<String> = emptyList()
)

/**
 * Stats data class for external API.
 */
@Serializable
data class Stats(
    val strength: Int,
    val dexterity: Int,
    val constitution: Int,
    val intelligence: Int,
    val wisdom: Int,
    val charisma: Int,
    val defense: Int
)
