package com.rpgenerator.core.skill

import com.rpgenerator.core.domain.Resources
import com.rpgenerator.core.domain.Stats
import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * A skill that a player can learn, level up, evolve, and fuse.
 *
 * Skills are the core of the LitRPG combat system:
 * - They level up independently through use
 * - They can evolve into stronger versions at max level
 * - They can fuse with other skills to create new ones
 * - They scale with player stats
 */
@Serializable
internal data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val rarity: SkillRarity,

    // Resource costs
    val manaCost: Int = 0,
    val energyCost: Int = 0,
    val healthCost: Int = 0,  // For blood magic / cultivation skills

    // Cooldown (in turns)
    val baseCooldown: Int = 0,
    val currentCooldown: Int = 0,

    // Leveling
    val level: Int = 1,
    val maxLevel: Int = 10,
    val currentXP: Long = 0L,

    // Effects - what happens when the skill is used
    val effects: List<SkillEffect>,

    // Type flags
    val isActive: Boolean = true,  // false = passive (always on)
    val isUltimate: Boolean = false,  // Ultimate skills have special rules
    val targetType: TargetType = TargetType.SINGLE_ENEMY,

    // Evolution paths (choices at max level)
    val evolutionPaths: List<SkillEvolutionPath> = emptyList(),

    // Fusion compatibility - tags that determine what can fuse
    val fusionTags: Set<String> = emptySet(),

    // Acquisition tracking
    val acquisitionSource: AcquisitionSource,
    val acquiredAt: Long = currentTimeMillis(),

    // Category for UI grouping
    val category: SkillCategory = SkillCategory.COMBAT
) {
    /**
     * XP needed to reach the next level.
     */
    fun xpToNextLevel(): Long = rarity.xpForLevel(level)

    /**
     * Progress toward next level as a percentage (0-100).
     */
    fun levelProgress(): Int {
        val needed = xpToNextLevel()
        return if (needed > 0) ((currentXP * 100) / needed).toInt().coerceIn(0, 100) else 100
    }

    /**
     * Gain XP and potentially level up.
     */
    fun gainXP(amount: Long): Skill {
        if (level >= maxLevel) return this

        var newXP = currentXP + amount
        var newLevel = level

        // Handle multiple level-ups
        while (newXP >= rarity.xpForLevel(newLevel) && newLevel < maxLevel) {
            newXP -= rarity.xpForLevel(newLevel)
            newLevel++
        }

        return copy(level = newLevel, currentXP = if (newLevel >= maxLevel) 0L else newXP)
    }

    /**
     * Check if skill is ready to use (off cooldown).
     */
    fun isReady(): Boolean = currentCooldown == 0

    /**
     * Start the cooldown after using the skill.
     */
    fun startCooldown(): Skill = copy(currentCooldown = baseCooldown)

    /**
     * Tick the cooldown down by one turn.
     */
    fun tickCooldown(): Skill = copy(currentCooldown = (currentCooldown - 1).coerceAtLeast(0))

    /**
     * Check if the player can afford to use this skill.
     */
    fun canAfford(resources: Resources): Boolean =
        resources.currentMana >= manaCost &&
        resources.currentEnergy >= energyCost &&
        resources.currentHP > healthCost  // Must have MORE than the cost to survive

    /**
     * Check if this skill can evolve (max level + has evolution paths).
     */
    fun canEvolve(): Boolean = level >= maxLevel && evolutionPaths.isNotEmpty()

    /**
     * Calculate total damage/effect value for display.
     */
    fun calculateTotalEffect(stats: Stats): Double {
        return effects.sumOf { it.calculateValue(level, stats, rarity) }
    }

    /**
     * Get a formatted cost string for display.
     */
    fun costString(): String {
        val costs = mutableListOf<String>()
        if (manaCost > 0) costs.add("$manaCost MP")
        if (energyCost > 0) costs.add("$energyCost EN")
        if (healthCost > 0) costs.add("$healthCost HP")
        return costs.joinToString(", ").ifEmpty { "Free" }
    }

    /**
     * Get a short display string for menus.
     */
    fun shortDisplay(): String {
        val cooldownStr = if (currentCooldown > 0) " [CD: $currentCooldown]" else ""
        return "[${rarity.symbol}] $name Lv.$level - ${costString()}$cooldownStr"
    }

    /**
     * Get XP bar for display (e.g., "[######----]").
     */
    fun xpBar(width: Int = 10): String {
        if (level >= maxLevel) return "[" + "M".repeat(width) + "]"  // MAX
        val filled = (levelProgress() * width / 100).coerceIn(0, width)
        val empty = width - filled
        return "[" + "#".repeat(filled) + "-".repeat(empty) + "]"
    }
}

/**
 * Evolution path - a potential upgrade when skill reaches max level.
 */
@Serializable
internal data class SkillEvolutionPath(
    val evolvesIntoId: String,
    val evolvesIntoName: String,
    val description: String,
    val requirements: List<EvolutionRequirement> = emptyList()
)

/**
 * Requirements for a specific evolution path.
 */
@Serializable
internal sealed class EvolutionRequirement {
    abstract fun isMet(stats: Stats, playerLevel: Int, completedQuests: Set<String>): Boolean
    abstract fun describe(): String

    @Serializable
    data class StatMinimum(val stat: StatType, val minimumValue: Int) : EvolutionRequirement() {
        override fun isMet(stats: Stats, playerLevel: Int, completedQuests: Set<String>): Boolean =
            stat.getValueFrom(stats) >= minimumValue

        override fun describe(): String = "${stat.name} ≥ $minimumValue"
    }

    @Serializable
    data class LevelMinimum(val minimumLevel: Int) : EvolutionRequirement() {
        override fun isMet(stats: Stats, playerLevel: Int, completedQuests: Set<String>): Boolean =
            playerLevel >= minimumLevel

        override fun describe(): String = "Player Level ≥ $minimumLevel"
    }

    @Serializable
    data class QuestCompleted(val questId: String, val questName: String) : EvolutionRequirement() {
        override fun isMet(stats: Stats, playerLevel: Int, completedQuests: Set<String>): Boolean =
            completedQuests.contains(questId)

        override fun describe(): String = "Complete: $questName"
    }
}

/**
 * How a skill was acquired - for tracking and narrative purposes.
 */
@Serializable
internal sealed class AcquisitionSource {

    @Serializable
    data class ActionInsight(
        val actionType: String,
        val repetitions: Int
    ) : AcquisitionSource()

    @Serializable
    data class SystemReward(
        val reason: String,
        val triggerId: String? = null
    ) : AcquisitionSource()

    @Serializable
    data class SkillBook(
        val bookId: String,
        val bookName: String
    ) : AcquisitionSource()

    @Serializable
    data class ClassStarter(
        val className: String
    ) : AcquisitionSource()

    @Serializable
    data class Fusion(
        val inputSkillIds: List<String>,
        val recipeId: String
    ) : AcquisitionSource()

    @Serializable
    data class Evolution(
        val previousSkillId: String,
        val evolutionPath: String
    ) : AcquisitionSource()

    @Serializable
    data class NPCTraining(
        val npcId: String,
        val npcName: String
    ) : AcquisitionSource()

    @Serializable
    object Unknown : AcquisitionSource()
}
