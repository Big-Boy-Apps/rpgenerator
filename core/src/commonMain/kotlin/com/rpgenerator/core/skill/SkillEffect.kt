package com.rpgenerator.core.skill

import com.rpgenerator.core.domain.Stats
import kotlinx.serialization.Serializable

/**
 * Damage types for skills - affects resistances and visual descriptions.
 */
@Serializable
internal enum class DamageType {
    PHYSICAL,   // Reduced by Defense
    MAGICAL,    // Reduced by Wisdom
    TRUE,       // Ignores all defenses
    FIRE,       // Magical fire damage
    ICE,        // Magical ice damage, may slow
    LIGHTNING,  // Magical lightning, may stun
    POISON,     // Damage over time
    HOLY,       // Extra vs undead
    DARK        // Life steal potential
}

/**
 * Which stat a skill scales with.
 */
@Serializable
internal enum class StatType {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA;

    fun getValueFrom(stats: Stats): Int = when (this) {
        STRENGTH -> stats.strength
        DEXTERITY -> stats.dexterity
        CONSTITUTION -> stats.constitution
        INTELLIGENCE -> stats.intelligence
        WISDOM -> stats.wisdom
        CHARISMA -> stats.charisma
    }
}

/**
 * Skill effects - the mechanical outcomes of using a skill.
 * A skill can have multiple effects (e.g., damage + debuff).
 */
@Serializable
internal sealed class SkillEffect {

    /**
     * Calculate the actual value of this effect given skill level and user stats.
     */
    abstract fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double

    /**
     * Human-readable description of this effect.
     */
    abstract fun describe(skillLevel: Int): String

    /**
     * Direct damage to a target.
     */
    @Serializable
    data class Damage(
        val baseAmount: Int,
        val scalingStat: StatType,
        val scalingRatio: Double,  // e.g., 0.5 = 50% of stat added
        val damageType: DamageType = DamageType.PHYSICAL
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseAmount)
            val statBonus = scalingStat.getValueFrom(stats) * scalingRatio
            val levelBonus = skillLevel * 0.1  // +10% per skill level
            return (base + statBonus) * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String {
            val levelBonus = (skillLevel * 10)
            return "${damageType.name.lowercase()} damage: $baseAmount + ${(scalingRatio * 100).toInt()}% ${scalingStat.name} (+$levelBonus% from level)"
        }
    }

    /**
     * Healing effect (on self or ally).
     */
    @Serializable
    data class Heal(
        val baseAmount: Int,
        val scalingStat: StatType = StatType.WISDOM,
        val scalingRatio: Double = 0.3
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseAmount)
            val statBonus = scalingStat.getValueFrom(stats) * scalingRatio
            val levelBonus = skillLevel * 0.1
            return (base + statBonus) * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String =
            "Heal: $baseAmount + ${(scalingRatio * 100).toInt()}% ${scalingStat.name}"
    }

    /**
     * Temporary stat buff.
     */
    @Serializable
    data class Buff(
        val statAffected: StatType,
        val baseBonus: Int,
        val duration: Int,  // turns
        val scalingRatio: Double = 0.0  // optional scaling
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseBonus)
            val levelBonus = skillLevel * 0.05  // +5% per level
            return base * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String =
            "+$baseBonus ${statAffected.name} for $duration turns"
    }

    /**
     * Temporary stat debuff on enemy.
     */
    @Serializable
    data class Debuff(
        val statAffected: StatType,
        val basePenalty: Int,
        val duration: Int,
        val scalingRatio: Double = 0.0
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(basePenalty)
            val levelBonus = skillLevel * 0.05
            return base * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String =
            "-$basePenalty ${statAffected.name} for $duration turns"
    }

    /**
     * Damage over time effect.
     */
    @Serializable
    data class DamageOverTime(
        val baseDamagePerTurn: Int,
        val duration: Int,
        val scalingStat: StatType = StatType.INTELLIGENCE,
        val scalingRatio: Double = 0.2,
        val damageType: DamageType = DamageType.POISON
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseDamagePerTurn)
            val statBonus = scalingStat.getValueFrom(stats) * scalingRatio
            val levelBonus = skillLevel * 0.08
            return (base + statBonus) * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String =
            "$baseDamagePerTurn ${damageType.name.lowercase()} damage per turn for $duration turns"
    }

    /**
     * Heal over time effect.
     */
    @Serializable
    data class HealOverTime(
        val baseHealPerTurn: Int,
        val duration: Int,
        val scalingStat: StatType = StatType.WISDOM,
        val scalingRatio: Double = 0.15
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseHealPerTurn)
            val statBonus = scalingStat.getValueFrom(stats) * scalingRatio
            val levelBonus = skillLevel * 0.08
            return (base + statBonus) * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String =
            "Heal $baseHealPerTurn per turn for $duration turns"
    }

    /**
     * Passive effect - always active, no cost.
     */
    @Serializable
    data class Passive(
        val statBonuses: Map<StatType, Int>,
        val description: String
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            // Passives scale with level
            return statBonuses.values.sum() * (1 + skillLevel * 0.1)
        }

        override fun describe(skillLevel: Int): String {
            val bonusText = statBonuses.entries.joinToString(", ") { (stat, value) ->
                val scaled = (value * (1 + skillLevel * 0.1)).toInt()
                "+$scaled ${stat.name}"
            }
            return "$description ($bonusText)"
        }
    }

    /**
     * Shield/barrier effect - absorbs damage.
     */
    @Serializable
    data class Shield(
        val baseAmount: Int,
        val duration: Int,
        val scalingStat: StatType = StatType.CONSTITUTION,
        val scalingRatio: Double = 0.5
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseAmount)
            val statBonus = scalingStat.getValueFrom(stats) * scalingRatio
            val levelBonus = skillLevel * 0.1
            return (base + statBonus) * (1 + levelBonus)
        }

        override fun describe(skillLevel: Int): String =
            "Shield: $baseAmount + ${(scalingRatio * 100).toInt()}% ${scalingStat.name} for $duration turns"
    }

    /**
     * Resource restoration (mana/energy).
     */
    @Serializable
    data class RestoreResource(
        val resourceType: ResourceType,
        val baseAmount: Int,
        val scalingStat: StatType = StatType.WISDOM,
        val scalingRatio: Double = 0.2
    ) : SkillEffect() {

        override fun calculateValue(skillLevel: Int, stats: Stats, rarity: SkillRarity): Double {
            val base = rarity.scalePower(baseAmount)
            val statBonus = scalingStat.getValueFrom(stats) * scalingRatio
            return base + statBonus + (skillLevel * 2)
        }

        override fun describe(skillLevel: Int): String =
            "Restore $baseAmount ${resourceType.name.lowercase()}"
    }
}

@Serializable
internal enum class ResourceType {
    MANA,
    ENERGY,
    HEALTH
}

/**
 * Target types for skills.
 */
@Serializable
internal enum class TargetType {
    SELF,           // Only affects the user
    SINGLE_ENEMY,   // One enemy target
    SINGLE_ALLY,    // One ally target
    ALL_ENEMIES,    // AoE damage
    ALL_ALLIES,     // AoE heal/buff
    AREA            // Affects everyone in area
}

/**
 * Skill categories for UI grouping.
 */
@Serializable
internal enum class SkillCategory {
    COMBAT,     // Offensive skills
    DEFENSIVE,  // Shields, dodges, armor
    SUPPORT,    // Heals, buffs
    UTILITY,    // Non-combat utility
    MOVEMENT,   // Mobility skills
    CRAFTING    // Crafting-related
}
