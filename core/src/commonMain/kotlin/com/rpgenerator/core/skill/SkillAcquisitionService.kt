package com.rpgenerator.core.skill

import com.rpgenerator.core.domain.Stats

/**
 * Service for acquiring skills through various methods:
 * - Class starter skills
 * - Action insight (repeated actions)
 * - System rewards (quests, level-ups)
 * - Skill books
 * - Evolution (max level upgrade)
 * - Fusion (combining skills)
 */
internal class SkillAcquisitionService {

    // ═══════════════════════════════════════════════════════════════════════════
    // CLASS STARTER SKILLS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get starter skills for a character class.
     */
    fun getClassStarterSkills(className: String): List<Skill> {
        return SkillDatabase.getStarterSkills(className)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACTION INSIGHT LEARNING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process player input for action-based skill learning.
     * Returns updated tracker and any acquisition events.
     */
    fun processActionForInsight(
        input: String,
        context: ActionContext,
        tracker: ActionInsightTracker,
        ownedSkillIds: Set<String>
    ): ActionInsightResult {
        val actionTypes = ActionTypeMapper.extractActionTypes(input, context)

        if (actionTypes.isEmpty()) {
            return ActionInsightResult(
                updatedTracker = tracker,
                events = emptyList()
            )
        }

        var currentTracker = tracker
        val events = mutableListOf<SkillAcquisitionEvent>()

        for (actionType in actionTypes) {
            val result = currentTracker.trackAction(actionType, SkillDatabase.insightThresholds)
            currentTracker = result.updatedTracker

            // Check for skill unlock
            if (result.unlockedSkill != null && result.unlockedSkill !in ownedSkillIds) {
                val skill = SkillDatabase.getSkillWithSource(
                    result.unlockedSkill,
                    AcquisitionSource.ActionInsight(
                        actionType = actionType,
                        repetitions = currentTracker.getActionCount(actionType)
                    )
                )
                if (skill != null) {
                    events.add(SkillAcquisitionEvent.LearnedFromInsight(
                        skill = skill,
                        actionType = actionType,
                        totalActions = currentTracker.getActionCount(actionType)
                    ))
                }
            }

            // Check for new partial (hint shown)
            if (result.newPartialSkill != null) {
                events.add(SkillAcquisitionEvent.PartialSkillRevealed(
                    partialSkill = result.newPartialSkill,
                    actionType = actionType
                ))
            }

            // Check for progress update (optional - could be noisy)
            if (result.partialProgress != null && result.partialProgress.progress % 25 == 0) {
                events.add(SkillAcquisitionEvent.InsightProgress(
                    partialSkill = result.partialProgress,
                    actionType = actionType
                ))
            }
        }

        return ActionInsightResult(
            updatedTracker = currentTracker,
            events = events
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYSTEM REWARDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Grant a skill as a System reward (quest completion, level up, etc.).
     */
    fun grantSystemReward(
        skillId: String,
        reason: String,
        triggerId: String? = null
    ): SkillAcquisitionEvent? {
        val skill = SkillDatabase.getSkillWithSource(
            skillId,
            AcquisitionSource.SystemReward(reason, triggerId)
        )
        return skill?.let { SkillAcquisitionEvent.SystemReward(it, reason) }
    }

    /**
     * Grant a random skill from a pool based on rarity.
     */
    fun grantRandomSkill(
        rarity: SkillRarity,
        reason: String,
        excludeSkillIds: Set<String> = emptySet()
    ): SkillAcquisitionEvent? {
        val candidates = SkillDatabase.getSkillsByRarity(rarity)
            .filter { it.id !in excludeSkillIds }

        if (candidates.isEmpty()) return null

        val selected = candidates.random()
        val skill = selected.copy(
            acquisitionSource = AcquisitionSource.SystemReward(reason, null)
        )
        return SkillAcquisitionEvent.SystemReward(skill, reason)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL BOOKS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Learn a skill from a skill book item.
     */
    fun learnFromSkillBook(
        skillId: String,
        bookId: String,
        bookName: String,
        ownedSkillIds: Set<String>
    ): SkillAcquisitionEvent {
        if (skillId in ownedSkillIds) {
            return SkillAcquisitionEvent.AlreadyKnown(skillId)
        }

        val skill = SkillDatabase.getSkillWithSource(
            skillId,
            AcquisitionSource.SkillBook(bookId, bookName)
        )

        return if (skill != null) {
            SkillAcquisitionEvent.LearnedFromBook(skill, bookName)
        } else {
            SkillAcquisitionEvent.SkillNotFound(skillId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NPC TRAINING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Learn a skill from an NPC trainer.
     */
    fun learnFromNPC(
        skillId: String,
        npcId: String,
        npcName: String,
        ownedSkillIds: Set<String>
    ): SkillAcquisitionEvent {
        if (skillId in ownedSkillIds) {
            return SkillAcquisitionEvent.AlreadyKnown(skillId)
        }

        val skill = SkillDatabase.getSkillWithSource(
            skillId,
            AcquisitionSource.NPCTraining(npcId, npcName)
        )

        return if (skill != null) {
            SkillAcquisitionEvent.LearnedFromNPC(skill, npcName)
        } else {
            SkillAcquisitionEvent.SkillNotFound(skillId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL EVOLUTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a skill can evolve and return available paths.
     */
    fun getEvolutionOptions(
        skill: Skill,
        stats: Stats,
        playerLevel: Int,
        completedQuests: Set<String>
    ): List<EvolutionOption> {
        if (!skill.canEvolve()) return emptyList()

        return skill.evolutionPaths.map { path ->
            val requirementsMet = path.requirements.all { req ->
                req.isMet(stats, playerLevel, completedQuests)
            }
            EvolutionOption(
                path = path,
                requirementsMet = requirementsMet,
                unmetRequirements = path.requirements.filter { req ->
                    !req.isMet(stats, playerLevel, completedQuests)
                }
            )
        }
    }

    /**
     * Evolve a skill along a chosen path.
     */
    fun evolveSkill(
        skill: Skill,
        evolutionPathId: String,
        stats: Stats,
        playerLevel: Int,
        completedQuests: Set<String>
    ): SkillAcquisitionEvent {
        if (!skill.canEvolve()) {
            return SkillAcquisitionEvent.EvolutionFailed("Skill is not at max level")
        }

        val path = skill.evolutionPaths.find { it.evolvesIntoId == evolutionPathId }
            ?: return SkillAcquisitionEvent.EvolutionFailed("Evolution path not found")

        val unmetRequirements = path.requirements.filter { req ->
            !req.isMet(stats, playerLevel, completedQuests)
        }

        if (unmetRequirements.isNotEmpty()) {
            val reasons = unmetRequirements.map { it.describe() }
            return SkillAcquisitionEvent.EvolutionFailed(
                "Requirements not met: ${reasons.joinToString(", ")}"
            )
        }

        val evolvedSkill = SkillDatabase.getSkillWithSource(
            evolutionPathId,
            AcquisitionSource.Evolution(skill.id, evolutionPathId)
        )

        return if (evolvedSkill != null) {
            SkillAcquisitionEvent.SkillEvolved(
                oldSkill = skill,
                newSkill = evolvedSkill
            )
        } else {
            SkillAcquisitionEvent.EvolutionFailed("Evolved skill not found in database")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL FUSION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find all fusion recipes available with current skills.
     */
    fun getAvailableFusions(
        ownedSkills: List<Skill>,
        discoveredFusions: List<DiscoveredFusion>
    ): List<FusionOption> {
        val ownedSkillIds = ownedSkills.map { it.id }.toSet()
        val skillLevels = ownedSkills.associate { it.id to it.level }
        val discoveredIds = discoveredFusions.map { it.recipeId }.toSet()

        return SkillDatabase.findAvailableFusions(ownedSkillIds).map { recipe ->
            val levelRequirementsMet = recipe.minimumLevels.all { (skillId, minLevel) ->
                (skillLevels[skillId] ?: 0) >= minLevel
            }
            val unmetLevels = recipe.minimumLevels.filter { (skillId, minLevel) ->
                (skillLevels[skillId] ?: 0) < minLevel
            }

            FusionOption(
                recipe = recipe,
                isDiscovered = recipe.id in discoveredIds,
                levelRequirementsMet = levelRequirementsMet,
                unmetLevelRequirements = unmetLevels
            )
        }
    }

    /**
     * Get hints for fusions that are almost available.
     */
    fun getFusionHints(
        ownedSkillIds: Set<String>,
        discoveredFusions: List<DiscoveredFusion>
    ): List<FusionHint> {
        val discoveredIds = discoveredFusions.map { it.recipeId }.toSet()

        return SkillDatabase.findPartialFusions(ownedSkillIds)
            .filter { (recipe, _) -> recipe.isDiscoverable && recipe.id !in discoveredIds }
            .map { (recipe, missingSkills) ->
                FusionHint(
                    hint = recipe.discoveryHint ?: "You sense potential in combining your skills...",
                    missingSkillCount = missingSkills.size
                )
            }
    }

    /**
     * Attempt to fuse skills together.
     */
    fun fuseSkills(
        skillIds: Set<String>,
        ownedSkills: List<Skill>,
        discoveredFusions: List<DiscoveredFusion>
    ): SkillAcquisitionEvent {
        val ownedSkillIds = ownedSkills.map { it.id }.toSet()
        val skillLevels = ownedSkills.associate { it.id to it.level }
        val discoveredIds = discoveredFusions.map { it.recipeId }.toSet()

        // Check if player owns all the skills
        val missingSkills = skillIds - ownedSkillIds
        if (missingSkills.isNotEmpty()) {
            return SkillAcquisitionEvent.FusionFailed(
                "Missing required skills: ${missingSkills.joinToString(", ")}"
            )
        }

        // Find matching recipe
        val recipe = SkillDatabase.fusionRecipes.find { recipe ->
            recipe.inputSkillIds == skillIds
        } ?: return SkillAcquisitionEvent.FusionFailed(
            "These skills cannot be fused together"
        )

        // Check level requirements
        val unmetLevels = recipe.minimumLevels.filter { (skillId, minLevel) ->
            (skillLevels[skillId] ?: 0) < minLevel
        }
        if (unmetLevels.isNotEmpty()) {
            val details = unmetLevels.entries.joinToString(", ") { (id, min) ->
                "$id needs level $min (currently ${skillLevels[id] ?: 0})"
            }
            return SkillAcquisitionEvent.FusionFailed("Skill levels too low: $details")
        }

        // Perform fusion
        val fusedSkill = SkillDatabase.getSkillWithSource(
            recipe.resultSkillId,
            AcquisitionSource.Fusion(skillIds.toList(), recipe.id)
        )

        return if (fusedSkill != null) {
            val isNewDiscovery = recipe.id !in discoveredIds
            SkillAcquisitionEvent.SkillFused(
                newSkill = fusedSkill,
                consumedSkillIds = skillIds,
                recipeId = recipe.id,
                wasNewDiscovery = isNewDiscovery
            )
        } else {
            SkillAcquisitionEvent.FusionFailed("Fused skill not found in database")
        }
    }

    /**
     * Attempt experimental fusion (trying skills without knowing the recipe).
     */
    fun attemptExperimentalFusion(
        skillIds: Set<String>,
        ownedSkills: List<Skill>,
        discoveredFusions: List<DiscoveredFusion>
    ): SkillAcquisitionEvent {
        // First check if there's an exact recipe match
        val directResult = fuseSkills(skillIds, ownedSkills, discoveredFusions)
        if (directResult !is SkillAcquisitionEvent.FusionFailed) {
            return directResult
        }

        // Check for partial compatibility using fusion tags
        val skills = ownedSkills.filter { it.id in skillIds }
        if (skills.size < 2) {
            return SkillAcquisitionEvent.FusionFailed("Need at least 2 skills to attempt fusion")
        }

        val allTags = skills.flatMap { it.fusionTags }.toSet()
        val potentialTypes = if (skills.size == 2) {
            FusionMatcher.findCompatibleFusions(
                skills[0].fusionTags,
                skills[1].fusionTags
            )
        } else {
            emptySet()
        }

        return if (potentialTypes.isNotEmpty()) {
            SkillAcquisitionEvent.FusionFailed(
                "The skills resonate but the combination is unstable. Perhaps if they were stronger..."
            )
        } else {
            SkillAcquisitionEvent.FusionFailed(
                "These skills have no affinity for each other."
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Result of processing actions for insight.
 */
internal data class ActionInsightResult(
    val updatedTracker: ActionInsightTracker,
    val events: List<SkillAcquisitionEvent>
)

/**
 * Events that can occur during skill acquisition.
 */
internal sealed class SkillAcquisitionEvent {
    // Learning events
    data class LearnedFromInsight(
        val skill: Skill,
        val actionType: String,
        val totalActions: Int
    ) : SkillAcquisitionEvent()

    data class LearnedFromBook(
        val skill: Skill,
        val bookName: String
    ) : SkillAcquisitionEvent()

    data class LearnedFromNPC(
        val skill: Skill,
        val npcName: String
    ) : SkillAcquisitionEvent()

    data class SystemReward(
        val skill: Skill,
        val reason: String
    ) : SkillAcquisitionEvent()

    // Progress events
    data class PartialSkillRevealed(
        val partialSkill: PartialSkill,
        val actionType: String
    ) : SkillAcquisitionEvent()

    data class InsightProgress(
        val partialSkill: PartialSkill,
        val actionType: String
    ) : SkillAcquisitionEvent()

    // Evolution events
    data class SkillEvolved(
        val oldSkill: Skill,
        val newSkill: Skill
    ) : SkillAcquisitionEvent()

    data class EvolutionFailed(
        val reason: String
    ) : SkillAcquisitionEvent()

    // Fusion events
    data class SkillFused(
        val newSkill: Skill,
        val consumedSkillIds: Set<String>,
        val recipeId: String,
        val wasNewDiscovery: Boolean
    ) : SkillAcquisitionEvent()

    data class FusionFailed(
        val reason: String
    ) : SkillAcquisitionEvent()

    // Error events
    data class AlreadyKnown(
        val skillId: String
    ) : SkillAcquisitionEvent()

    data class SkillNotFound(
        val skillId: String
    ) : SkillAcquisitionEvent()
}

/**
 * Option for skill evolution.
 */
internal data class EvolutionOption(
    val path: SkillEvolutionPath,
    val requirementsMet: Boolean,
    val unmetRequirements: List<EvolutionRequirement>
)

/**
 * Option for skill fusion.
 */
internal data class FusionOption(
    val recipe: SkillFusionRecipe,
    val isDiscovered: Boolean,
    val levelRequirementsMet: Boolean,
    val unmetLevelRequirements: Map<String, Int>
)

/**
 * Hint for a fusion that's almost available.
 */
internal data class FusionHint(
    val hint: String,
    val missingSkillCount: Int
)
