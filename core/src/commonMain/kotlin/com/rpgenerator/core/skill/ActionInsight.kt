package com.rpgenerator.core.skill

import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Tracks player actions to determine when they've done something enough times
 * to gain insight into a new skill.
 *
 * This is the core of the "action-based learning" system:
 * - Player swings sword 50 times → learns Power Strike
 * - Player sneaks around 40 times → learns Shadow Step
 * - Player meditates 30 times → learns Mana Circulation
 */
@Serializable
internal data class ActionInsightTracker(
    // Count of each action type performed
    val actionCounts: Map<String, Int> = emptyMap(),

    // Partial skills the player is developing (shown as hints)
    val partialSkills: List<PartialSkill> = emptyList(),

    // Skills unlocked through action insight (for reference)
    val insightUnlockedSkills: Set<String> = emptySet()
) {
    /**
     * Track an action and return updated tracker + any skill unlocked.
     */
    fun trackAction(
        actionType: String,
        thresholds: List<InsightThreshold>
    ): ActionTrackResult {
        val newCount = (actionCounts[actionType] ?: 0) + 1
        val newCounts = actionCounts + (actionType to newCount)

        // Find applicable threshold
        val threshold = thresholds.find { it.actionType == actionType }
            ?: return ActionTrackResult(
                updatedTracker = copy(actionCounts = newCounts),
                unlockedSkill = null,
                newPartialSkill = null,
                partialProgress = null
            )

        // Check for full unlock
        if (newCount >= threshold.fullUnlockCount && !insightUnlockedSkills.contains(threshold.skillId)) {
            val updatedTracker = copy(
                actionCounts = newCounts,
                insightUnlockedSkills = insightUnlockedSkills + threshold.skillId,
                partialSkills = partialSkills.filter { it.skillIdToUnlock != threshold.skillId }
            )
            return ActionTrackResult(
                updatedTracker = updatedTracker,
                unlockedSkill = threshold.skillId,
                newPartialSkill = null,
                partialProgress = null
            )
        }

        // Check for partial unlock (hint)
        val existingPartial = partialSkills.find { it.skillIdToUnlock == threshold.skillId }
        if (newCount >= threshold.partialUnlockCount && existingPartial == null) {
            val newPartial = PartialSkill(
                actionType = actionType,
                skillIdToUnlock = threshold.skillId,
                progress = calculateProgress(newCount, threshold),
                isRevealed = true,
                revealedAt = currentTimeMillis()
            )
            val updatedTracker = copy(
                actionCounts = newCounts,
                partialSkills = partialSkills + newPartial
            )
            return ActionTrackResult(
                updatedTracker = updatedTracker,
                unlockedSkill = null,
                newPartialSkill = newPartial,
                partialProgress = null
            )
        }

        // Update existing partial progress
        if (existingPartial != null) {
            val updatedPartial = existingPartial.copy(
                progress = calculateProgress(newCount, threshold)
            )
            val updatedPartials = partialSkills.map {
                if (it.skillIdToUnlock == threshold.skillId) updatedPartial else it
            }
            val updatedTracker = copy(
                actionCounts = newCounts,
                partialSkills = updatedPartials
            )
            return ActionTrackResult(
                updatedTracker = updatedTracker,
                unlockedSkill = null,
                newPartialSkill = null,
                partialProgress = updatedPartial
            )
        }

        return ActionTrackResult(
            updatedTracker = copy(actionCounts = newCounts),
            unlockedSkill = null,
            newPartialSkill = null,
            partialProgress = null
        )
    }

    private fun calculateProgress(count: Int, threshold: InsightThreshold): Int {
        val range = threshold.fullUnlockCount - threshold.partialUnlockCount
        val progress = count - threshold.partialUnlockCount
        return ((progress.toFloat() / range) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Get the count for a specific action type.
     */
    fun getActionCount(actionType: String): Int = actionCounts[actionType] ?: 0
}

/**
 * Result of tracking an action.
 */
internal data class ActionTrackResult(
    val updatedTracker: ActionInsightTracker,
    val unlockedSkill: String?,  // Skill ID if fully unlocked
    val newPartialSkill: PartialSkill?,  // New partial if first time reaching threshold
    val partialProgress: PartialSkill?  // Updated partial if already in progress
)

/**
 * A skill that the player is developing through repeated action.
 */
@Serializable
internal data class PartialSkill(
    val actionType: String,
    val skillIdToUnlock: String,
    val progress: Int,  // 0-100
    val isRevealed: Boolean,  // True if player knows about this
    val revealedAt: Long = 0L
) {
    /**
     * Get a progress bar for display.
     */
    fun progressBar(width: Int = 10): String {
        val filled = (progress * width / 100).coerceIn(0, width)
        val empty = width - filled
        return "[" + "▓".repeat(filled) + "░".repeat(empty) + "]"
    }

    /**
     * Get hint text for display (skill name is hidden until unlocked).
     */
    fun hintText(): String = "??? - ${progressBar()} $progress%"
}

/**
 * Threshold for unlocking a skill through action.
 */
@Serializable
internal data class InsightThreshold(
    val actionType: String,
    val skillId: String,
    val partialUnlockCount: Int,  // When to show the hint
    val fullUnlockCount: Int,     // When to actually learn the skill
    val skillName: String? = null  // For display (hidden until close to unlock)
)

/**
 * Maps player input/actions to trackable action types.
 */
internal object ActionTypeMapper {

    /**
     * Analyze player input and extract action types for tracking.
     */
    fun extractActionTypes(input: String, context: ActionContext): Set<String> {
        val lowerInput = input.lowercase()
        val types = mutableSetOf<String>()

        // Combat actions
        when {
            lowerInput.containsAny("slash", "swing", "cut") && context.hasWeaponType("sword") ->
                types.add("sword_slash")
            lowerInput.containsAny("stab", "thrust", "pierce") && context.hasWeaponType("sword") ->
                types.add("sword_thrust")
            lowerInput.containsAny("slash", "swing", "chop") && context.hasWeaponType("axe") ->
                types.add("axe_swing")
            lowerInput.containsAny("smash", "crush", "bash") && context.hasWeaponType("mace", "hammer") ->
                types.add("blunt_strike")
            lowerInput.containsAny("shoot", "fire", "loose") && context.hasWeaponType("bow") ->
                types.add("bow_shot")
            lowerInput.containsAny("attack", "hit", "strike") ->
                types.add("basic_attack")
        }

        // Magic actions
        when {
            lowerInput.containsAny("cast", "channel", "invoke") && lowerInput.containsAny("fire", "flame", "burn") ->
                types.add("fire_magic")
            lowerInput.containsAny("cast", "channel", "invoke") && lowerInput.containsAny("ice", "frost", "freeze") ->
                types.add("ice_magic")
            lowerInput.containsAny("cast", "channel", "invoke") && lowerInput.containsAny("lightning", "thunder", "shock") ->
                types.add("lightning_magic")
            lowerInput.containsAny("heal", "mend", "restore") ->
                types.add("healing_magic")
        }

        // Movement/Stealth actions
        when {
            lowerInput.containsAny("sneak", "hide", "stealth", "creep") ->
                types.add("stealth_movement")
            lowerInput.containsAny("dodge", "evade", "sidestep", "roll") ->
                types.add("evasion")
            lowerInput.containsAny("run", "sprint", "dash", "charge") ->
                types.add("quick_movement")
        }

        // Cultivation/Meditation
        when {
            lowerInput.containsAny("meditate", "focus", "center", "cultivate") ->
                types.add("cultivation_meditate")
            lowerInput.containsAny("breathe", "circulate", "qi", "energy") ->
                types.add("energy_circulation")
        }

        // Exploration/Perception
        when {
            lowerInput.containsAny("search", "examine", "investigate", "look") ->
                types.add("perception_search")
            lowerInput.containsAny("listen", "hear", "sense") ->
                types.add("perception_listen")
        }

        // Social actions
        when {
            lowerInput.containsAny("intimidate", "threaten", "scare") ->
                types.add("intimidation")
            lowerInput.containsAny("persuade", "convince", "charm") ->
                types.add("persuasion")
            lowerInput.containsAny("deceive", "lie", "bluff") ->
                types.add("deception")
        }

        // Defense actions
        when {
            lowerInput.containsAny("block", "parry", "deflect") ->
                types.add("defensive_block")
            lowerInput.containsAny("guard", "defend", "brace") ->
                types.add("defensive_stance")
        }

        return types
    }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { this.contains(it) }
}

/**
 * Context for action type extraction.
 */
internal data class ActionContext(
    val equippedWeaponType: String? = null,
    val currentLocationTags: Set<String> = emptySet(),
    val inCombat: Boolean = false
) {
    fun hasWeaponType(vararg types: String): Boolean =
        equippedWeaponType != null && types.any { equippedWeaponType.contains(it, ignoreCase = true) }
}
