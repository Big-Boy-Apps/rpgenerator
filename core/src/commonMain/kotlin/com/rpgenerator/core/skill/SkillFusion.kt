package com.rpgenerator.core.skill

import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * A recipe for fusing two or more skills into a new one.
 */
@Serializable
internal data class SkillFusionRecipe(
    val id: String,
    val name: String,  // Display name like "Flame Blade Fusion"
    val description: String,

    // Required input skills
    val inputSkillIds: Set<String>,

    // Minimum levels for each input skill
    val minimumLevels: Map<String, Int> = emptyMap(),  // skillId -> minLevel

    // The resulting skill
    val resultSkillId: String,
    val resultSkillName: String,
    val resultRarity: SkillRarity,

    // Discovery
    val isDiscoverable: Boolean = true,  // Can be discovered through experimentation
    val discoveryHint: String? = null    // Hint shown when player has compatible skills
)

/**
 * Tracks a fusion recipe that the player has discovered.
 */
@Serializable
internal data class DiscoveredFusion(
    val recipeId: String,
    val discoveredAt: Long = currentTimeMillis(),
    val timesUsed: Int = 0
) {
    fun incrementUsage(): DiscoveredFusion = copy(timesUsed = timesUsed + 1)
}

/**
 * Result of attempting a skill fusion.
 */
internal sealed class FusionResult {
    /**
     * Fusion succeeded - return the new skill and consume the inputs.
     */
    data class Success(
        val newSkill: Skill,
        val consumedSkillIds: Set<String>,
        val recipeId: String,
        val wasNewDiscovery: Boolean
    ) : FusionResult()

    /**
     * Fusion failed - skills not compatible.
     */
    data class Incompatible(
        val reason: String
    ) : FusionResult()

    /**
     * Fusion failed - skill levels too low.
     */
    data class LevelTooLow(
        val skillId: String,
        val currentLevel: Int,
        val requiredLevel: Int
    ) : FusionResult()

    /**
     * Fusion failed - player doesn't have the required skills.
     */
    data class MissingSkill(
        val missingSkillIds: Set<String>
    ) : FusionResult()
}

/**
 * Helper for matching fusion tags between skills.
 */
internal object FusionMatcher {

    /**
     * Check if two sets of fusion tags are compatible for creating something new.
     * Returns potential fusion types based on tag overlap.
     */
    fun findCompatibleFusions(
        skill1Tags: Set<String>,
        skill2Tags: Set<String>
    ): Set<String> {
        val combined = skill1Tags + skill2Tags

        val potentialFusions = mutableSetOf<String>()

        // Element + Combat = Elemental Combat
        if (combined.containsAny("fire", "ice", "lightning") && combined.contains("melee")) {
            potentialFusions.add("elemental_melee")
        }
        if (combined.containsAny("fire", "ice", "lightning") && combined.contains("ranged")) {
            potentialFusions.add("elemental_ranged")
        }

        // Defense + Offense = Counter
        if (combined.contains("defensive") && combined.contains("offensive")) {
            potentialFusions.add("counter")
        }

        // Heal + Damage = Drain
        if (combined.contains("heal") && combined.contains("damage")) {
            potentialFusions.add("drain")
        }

        // Stealth + Combat = Assassination
        if (combined.contains("stealth") && combined.containsAny("melee", "damage")) {
            potentialFusions.add("assassination")
        }

        // Buff + Element = Enchantment
        if (combined.contains("buff") && combined.containsAny("fire", "ice", "lightning", "holy", "dark")) {
            potentialFusions.add("enchantment")
        }

        return potentialFusions
    }

    private fun Set<String>.containsAny(vararg values: String): Boolean =
        values.any { this.contains(it) }
}
