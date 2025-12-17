package com.rpgenerator.core.loot

import kotlinx.serialization.Serializable

/**
 * Item rarity tiers that affect drop rates, power levels, and value.
 */
@Serializable
internal enum class ItemRarity(
    val displayName: String,
    val dropWeight: Int, // Higher = more common
    val statMultiplier: Double // Multiplier for item stats
) {
    COMMON("Common", 100, 1.0),
    UNCOMMON("Uncommon", 40, 1.3),
    RARE("Rare", 15, 1.6),
    EPIC("Epic", 5, 2.0),
    LEGENDARY("Legendary", 1, 2.5);

    companion object {
        /**
         * Get rarity based on total drop weights.
         * @param roll Random value from 0 to sum of all weights
         */
        fun fromRoll(roll: Int): ItemRarity {
            var accumulated = 0
            for (rarity in values()) {
                accumulated += rarity.dropWeight
                if (roll <= accumulated) {
                    return rarity
                }
            }
            return COMMON
        }

        /**
         * Get total weight for all rarities (for random selection)
         */
        fun totalWeight(): Int = values().sumOf { it.dropWeight }
    }
}
