package com.rpgenerator.core.skill

import com.rpgenerator.core.domain.CharacterSheet
import com.rpgenerator.core.domain.Stats

/**
 * Service for executing skills in combat.
 * Handles damage calculation, effect application, and combat flow.
 */
internal class SkillCombatService {

    /**
     * Execute a skill and calculate its effects.
     * Returns the result of skill execution including damage dealt and effects applied.
     */
    fun executeSkill(
        skill: Skill,
        user: CharacterSheet,
        targetDefense: Int = 0,
        targetWisdom: Int = 10
    ): SkillExecutionResult {
        // Check if skill can be used
        if (!skill.isReady()) {
            return SkillExecutionResult.OnCooldown(skill.currentCooldown)
        }

        if (!skill.canAfford(user.resources)) {
            return SkillExecutionResult.InsufficientResources(
                needsMana = skill.manaCost,
                needsEnergy = skill.energyCost,
                needsHealth = skill.healthCost,
                hasMana = user.resources.currentMana,
                hasEnergy = user.resources.currentEnergy,
                hasHealth = user.resources.currentHP
            )
        }

        // Calculate effects
        val effectResults = skill.effects.map { effect ->
            calculateEffect(effect, skill, user.effectiveStats(), targetDefense, targetWisdom)
        }

        return SkillExecutionResult.Success(
            skill = skill,
            effectResults = effectResults,
            totalDamage = effectResults.filterIsInstance<EffectResult.DamageDealt>().sumOf { it.amount },
            totalHealing = effectResults.filterIsInstance<EffectResult.HealingDone>().sumOf { it.amount },
            xpGained = calculateXPGain(skill)
        )
    }

    /**
     * Calculate a single effect's result.
     */
    private fun calculateEffect(
        effect: SkillEffect,
        skill: Skill,
        userStats: Stats,
        targetDefense: Int,
        targetWisdom: Int
    ): EffectResult {
        return when (effect) {
            is SkillEffect.Damage -> {
                val rawDamage = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                val reduction = calculateDamageReduction(effect.damageType, targetDefense, targetWisdom)
                val finalDamage = (rawDamage * (1 - reduction)).toInt().coerceAtLeast(1)

                EffectResult.DamageDealt(
                    amount = finalDamage,
                    damageType = effect.damageType,
                    rawAmount = rawDamage,
                    wasReduced = reduction > 0
                )
            }

            is SkillEffect.Heal -> {
                val healAmount = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.HealingDone(amount = healAmount)
            }

            is SkillEffect.Buff -> {
                val buffAmount = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.BuffApplied(
                    stat = effect.statAffected,
                    amount = buffAmount,
                    duration = effect.duration
                )
            }

            is SkillEffect.Debuff -> {
                val debuffAmount = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.DebuffApplied(
                    stat = effect.statAffected,
                    amount = debuffAmount,
                    duration = effect.duration
                )
            }

            is SkillEffect.DamageOverTime -> {
                val damagePerTurn = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.DoTApplied(
                    damagePerTurn = damagePerTurn,
                    duration = effect.duration,
                    damageType = effect.damageType
                )
            }

            is SkillEffect.HealOverTime -> {
                val healPerTurn = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.HoTApplied(
                    healPerTurn = healPerTurn,
                    duration = effect.duration
                )
            }

            is SkillEffect.Shield -> {
                val shieldAmount = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.ShieldCreated(
                    amount = shieldAmount,
                    duration = effect.duration
                )
            }

            is SkillEffect.Passive -> {
                EffectResult.PassiveActive(description = effect.description)
            }

            is SkillEffect.RestoreResource -> {
                val amount = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
                EffectResult.ResourceRestored(
                    resourceType = effect.resourceType,
                    amount = amount
                )
            }
        }
    }

    /**
     * Calculate damage reduction based on damage type and target stats.
     */
    private fun calculateDamageReduction(
        damageType: DamageType,
        targetDefense: Int,
        targetWisdom: Int
    ): Double {
        return when (damageType) {
            DamageType.PHYSICAL -> (targetDefense * 0.02).coerceAtMost(0.75)  // Max 75% reduction
            DamageType.MAGICAL,
            DamageType.FIRE,
            DamageType.ICE,
            DamageType.LIGHTNING,
            DamageType.HOLY,
            DamageType.DARK -> (targetWisdom * 0.015).coerceAtMost(0.60)  // Max 60% reduction
            DamageType.POISON -> (targetDefense * 0.01).coerceAtMost(0.50)  // Max 50% reduction
            DamageType.TRUE -> 0.0  // TRUE damage ignores all defenses
        }
    }

    /**
     * Calculate XP gained from using a skill.
     */
    private fun calculateXPGain(skill: Skill): Long {
        // Base XP per use, scaled by skill rarity
        val baseXP = 10L
        return (baseXP * skill.rarity.xpMultiplier).toLong()
    }

    /**
     * Get a formatted description of skill effects at current level.
     */
    fun describeSkillEffects(skill: Skill, userStats: Stats): List<String> {
        return skill.effects.map { effect ->
            effect.describe(skill.level)
        }
    }

    /**
     * Calculate and format the damage preview for a skill.
     */
    fun getSkillDamagePreview(skill: Skill, userStats: Stats): String {
        val damageEffects = skill.effects.filterIsInstance<SkillEffect.Damage>()
        if (damageEffects.isEmpty()) return "No damage"

        val damages = damageEffects.map { effect ->
            val damage = effect.calculateValue(skill.level, userStats, skill.rarity).toInt()
            "${damage} ${effect.damageType.name.lowercase()}"
        }

        return damages.joinToString(" + ")
    }

    /**
     * Get total damage a skill would deal (for combat AI).
     */
    fun estimateTotalDamage(skill: Skill, userStats: Stats): Int {
        return skill.effects.filterIsInstance<SkillEffect.Damage>()
            .sumOf { it.calculateValue(skill.level, userStats, skill.rarity).toInt() }
    }

    /**
     * Get total healing a skill would provide.
     */
    fun estimateTotalHealing(skill: Skill, userStats: Stats): Int {
        return skill.effects.filterIsInstance<SkillEffect.Heal>()
            .sumOf { it.calculateValue(skill.level, userStats, skill.rarity).toInt() }
    }

    /**
     * Find the best damage skill from a list.
     */
    fun findBestDamageSkill(skills: List<Skill>, userStats: Stats): Skill? {
        return skills
            .filter { it.isActive && it.isReady() }
            .maxByOrNull { estimateTotalDamage(it, userStats) }
    }

    /**
     * Find the best healing skill from a list.
     */
    fun findBestHealingSkill(skills: List<Skill>, userStats: Stats): Skill? {
        return skills
            .filter { it.isActive && it.isReady() && it.targetType == TargetType.SELF }
            .filter { skill -> skill.effects.any { it is SkillEffect.Heal } }
            .maxByOrNull { estimateTotalHealing(it, userStats) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Result of attempting to execute a skill.
 */
internal sealed class SkillExecutionResult {
    /**
     * Skill executed successfully.
     */
    data class Success(
        val skill: Skill,
        val effectResults: List<EffectResult>,
        val totalDamage: Int,
        val totalHealing: Int,
        val xpGained: Long
    ) : SkillExecutionResult() {

        /**
         * Format the results for narrative display.
         */
        fun narrativeSummary(): String {
            val parts = mutableListOf<String>()

            if (totalDamage > 0) {
                parts.add("dealing $totalDamage damage")
            }
            if (totalHealing > 0) {
                parts.add("healing for $totalHealing")
            }

            effectResults.forEach { result ->
                when (result) {
                    is EffectResult.BuffApplied ->
                        parts.add("gaining +${result.amount} ${result.stat.name.lowercase()} for ${result.duration} turns")
                    is EffectResult.DebuffApplied ->
                        parts.add("reducing target's ${result.stat.name.lowercase()} by ${result.amount} for ${result.duration} turns")
                    is EffectResult.DoTApplied ->
                        parts.add("applying ${result.damagePerTurn} ${result.damageType.name.lowercase()} damage per turn for ${result.duration} turns")
                    is EffectResult.HoTApplied ->
                        parts.add("healing ${result.healPerTurn} per turn for ${result.duration} turns")
                    is EffectResult.ShieldCreated ->
                        parts.add("creating a shield absorbing ${result.amount} damage for ${result.duration} turns")
                    is EffectResult.ResourceRestored ->
                        parts.add("restoring ${result.amount} ${result.resourceType.name.lowercase()}")
                    else -> {}
                }
            }

            return if (parts.isEmpty()) {
                "uses ${skill.name}"
            } else {
                "uses ${skill.name}, ${parts.joinToString(", ")}"
            }
        }
    }

    /**
     * Skill is on cooldown.
     */
    data class OnCooldown(val turnsRemaining: Int) : SkillExecutionResult()

    /**
     * Not enough resources to use skill.
     */
    data class InsufficientResources(
        val needsMana: Int,
        val needsEnergy: Int,
        val needsHealth: Int,
        val hasMana: Int,
        val hasEnergy: Int,
        val hasHealth: Int
    ) : SkillExecutionResult() {
        fun getMissingResources(): List<String> {
            val missing = mutableListOf<String>()
            if (needsMana > hasMana) {
                missing.add("${needsMana - hasMana} more MP")
            }
            if (needsEnergy > hasEnergy) {
                missing.add("${needsEnergy - hasEnergy} more EN")
            }
            if (needsHealth > hasHealth) {
                missing.add("${needsHealth - hasHealth} more HP")
            }
            return missing
        }
    }
}

/**
 * Result of a single skill effect.
 */
internal sealed class EffectResult {
    data class DamageDealt(
        val amount: Int,
        val damageType: DamageType,
        val rawAmount: Int,
        val wasReduced: Boolean
    ) : EffectResult()

    data class HealingDone(
        val amount: Int
    ) : EffectResult()

    data class BuffApplied(
        val stat: StatType,
        val amount: Int,
        val duration: Int
    ) : EffectResult()

    data class DebuffApplied(
        val stat: StatType,
        val amount: Int,
        val duration: Int
    ) : EffectResult()

    data class DoTApplied(
        val damagePerTurn: Int,
        val duration: Int,
        val damageType: DamageType
    ) : EffectResult()

    data class HoTApplied(
        val healPerTurn: Int,
        val duration: Int
    ) : EffectResult()

    data class ShieldCreated(
        val amount: Int,
        val duration: Int
    ) : EffectResult()

    data class PassiveActive(
        val description: String
    ) : EffectResult()

    data class ResourceRestored(
        val resourceType: ResourceType,
        val amount: Int
    ) : EffectResult()
}
