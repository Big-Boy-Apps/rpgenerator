package com.rpgenerator.core.domain

import com.rpgenerator.core.skill.ActionContext
import com.rpgenerator.core.skill.ActionInsightTracker
import com.rpgenerator.core.skill.DiscoveredFusion
import com.rpgenerator.core.skill.Skill
import com.rpgenerator.core.skill.SkillAcquisitionService
import com.rpgenerator.core.skill.SkillDatabase
import kotlinx.serialization.Serializable

@Serializable
internal data class CharacterSheet(
    val level: Int = 1,
    val xp: Long = 0L,
    val baseStats: Stats,
    val resources: Resources,
    val skills: List<Skill> = emptyList(),
    val equipment: Equipment = Equipment(),
    val inventory: Inventory = Inventory(),
    val statusEffects: List<StatusEffect> = emptyList(),
    // Tier system fields
    val currentGrade: Grade = Grade.E_GRADE,
    val playerClass: PlayerClass = PlayerClass.NONE,
    val classEvolutionPath: List<String> = emptyList(), // Track evolution history
    val unspentStatPoints: Int = 0,
    // Skill acquisition tracking
    val actionInsightTracker: ActionInsightTracker = ActionInsightTracker(),
    val discoveredFusions: List<DiscoveredFusion> = emptyList()
) {
    fun xpToNextLevel(): Long = (level + 1) * 100L

    fun effectiveStats(): Stats {
        var effective = baseStats.copy()

        // Apply equipment bonuses
        equipment.weapon?.let { weapon ->
            effective = effective.copy(
                strength = effective.strength + weapon.strengthBonus,
                dexterity = effective.dexterity + weapon.dexterityBonus
            )
        }

        equipment.armor?.let { armor ->
            effective = effective.copy(
                constitution = effective.constitution + armor.constitutionBonus,
                defense = effective.defense + armor.defenseBonus
            )
        }

        equipment.accessory?.let { accessory ->
            effective = effective.copy(
                intelligence = effective.intelligence + accessory.intelligenceBonus,
                wisdom = effective.wisdom + accessory.wisdomBonus
            )
        }

        // Apply status effect modifiers
        statusEffects.forEach { effect ->
            effective = effect.applyTo(effective)
        }

        return effective
    }

    fun gainXP(amount: Long): CharacterSheet {
        val newXP = xp + amount
        val newLevel = calculateLevel(newXP)

        return if (newLevel > level) {
            // Level up: increase base stats
            val statIncrease = newLevel - level
            val newGrade = Grade.fromLevel(newLevel)

            // Check for grade advancement
            val gradeChanged = newGrade != currentGrade
            val statPointsAwarded = if (gradeChanged) {
                when (newGrade) {
                    Grade.D_GRADE -> 10
                    Grade.C_GRADE -> 20
                    Grade.B_GRADE -> 30
                    Grade.A_GRADE -> 50
                    Grade.S_GRADE -> 100
                    else -> 0
                }
            } else {
                0
            }

            copy(
                level = newLevel,
                xp = newXP,
                baseStats = baseStats.increaseOnLevelUp(statIncrease),
                resources = resources.restoreOnLevelUp(),
                currentGrade = newGrade,
                unspentStatPoints = unspentStatPoints + statPointsAwarded
            )
        } else {
            copy(xp = newXP)
        }
    }

    fun evolveClass(evolution: ClassEvolution): CharacterSheet {
        // Apply stat bonuses from evolution
        val newStats = Stats(
            strength = baseStats.strength + evolution.statModifiers.strength,
            dexterity = baseStats.dexterity + evolution.statModifiers.dexterity,
            constitution = baseStats.constitution + evolution.statModifiers.constitution,
            intelligence = baseStats.intelligence + evolution.statModifiers.intelligence,
            wisdom = baseStats.wisdom + evolution.statModifiers.wisdom,
            charisma = baseStats.charisma + evolution.statModifiers.charisma,
            defense = baseStats.defense + evolution.statModifiers.defense
        )

        // Record evolution in history
        val newPath = classEvolutionPath + evolution.name

        return copy(
            baseStats = newStats,
            classEvolutionPath = newPath
        )
    }

    fun chooseInitialClass(chosenClass: PlayerClass): CharacterSheet {
        // Apply class stat bonuses
        val newStats = Stats(
            strength = baseStats.strength + chosenClass.statBonuses.strength,
            dexterity = baseStats.dexterity + chosenClass.statBonuses.dexterity,
            constitution = baseStats.constitution + chosenClass.statBonuses.constitution,
            intelligence = baseStats.intelligence + chosenClass.statBonuses.intelligence,
            wisdom = baseStats.wisdom + chosenClass.statBonuses.wisdom,
            charisma = baseStats.charisma + chosenClass.statBonuses.charisma
        )

        return copy(
            playerClass = chosenClass,
            baseStats = newStats,
            classEvolutionPath = listOf(chosenClass.displayName)
        )
    }

    fun spendStatPoints(stats: Stats): CharacterSheet {
        // Calculate total points being spent
        val pointsSpent = stats.strength + stats.dexterity + stats.constitution +
                         stats.intelligence + stats.wisdom + stats.charisma

        if (pointsSpent > unspentStatPoints) {
            return this // Not enough points
        }

        val newStats = Stats(
            strength = baseStats.strength + stats.strength,
            dexterity = baseStats.dexterity + stats.dexterity,
            constitution = baseStats.constitution + stats.constitution,
            intelligence = baseStats.intelligence + stats.intelligence,
            wisdom = baseStats.wisdom + stats.wisdom,
            charisma = baseStats.charisma + stats.charisma,
            defense = baseStats.defense
        )

        return copy(
            baseStats = newStats,
            unspentStatPoints = unspentStatPoints - pointsSpent
        )
    }

    fun takeDamage(damage: Int): CharacterSheet {
        val newHP = (resources.currentHP - damage).coerceAtLeast(0)
        return copy(resources = resources.copy(currentHP = newHP))
    }

    fun heal(amount: Int): CharacterSheet {
        val newHP = (resources.currentHP + amount).coerceAtMost(resources.maxHP)
        return copy(resources = resources.copy(currentHP = newHP))
    }

    fun spendMana(amount: Int): CharacterSheet {
        val newMana = (resources.currentMana - amount).coerceAtLeast(0)
        return copy(resources = resources.copy(currentMana = newMana))
    }

    fun addSkill(skill: Skill): CharacterSheet {
        if (skills.any { it.id == skill.id }) return this
        return copy(skills = skills + skill)
    }

    /**
     * Get a skill by ID.
     */
    fun getSkill(id: String): Skill? = skills.find { it.id == id }

    /**
     * Update a skill (e.g., after gaining XP or using it).
     */
    fun updateSkill(updatedSkill: Skill): CharacterSheet {
        val newSkills = skills.map { if (it.id == updatedSkill.id) updatedSkill else it }
        return copy(skills = newSkills)
    }

    /**
     * Remove a skill (used when fusing).
     */
    fun removeSkill(skillId: String): CharacterSheet {
        return copy(skills = skills.filter { it.id != skillId })
    }

    /**
     * Replace multiple skills with a new one (for fusion).
     */
    fun replaceSkillsWithFused(consumedIds: Set<String>, newSkill: Skill): CharacterSheet {
        val remainingSkills = skills.filter { it.id !in consumedIds }
        return copy(skills = remainingSkills + newSkill)
    }

    /**
     * Grant starter skills for a class.
     */
    fun grantClassStarterSkills(className: String): CharacterSheet {
        val starterSkills = SkillDatabase.getStarterSkills(className)
        val existingIds = skills.map { it.id }.toSet()
        val newSkills = starterSkills.filter { it.id !in existingIds }
        return copy(skills = skills + newSkills)
    }

    /**
     * Process player action for insight-based skill learning.
     */
    fun processActionInsight(
        input: String,
        context: ActionContext = ActionContext(equippedWeaponType = equipment.weapon?.name)
    ): Pair<CharacterSheet, List<Skill>> {
        val service = SkillAcquisitionService()
        val ownedIds = skills.map { it.id }.toSet()

        val result = service.processActionForInsight(input, context, actionInsightTracker, ownedIds)

        // Collect newly learned skills
        val newSkills = result.events.mapNotNull { event ->
            when (event) {
                is com.rpgenerator.core.skill.SkillAcquisitionEvent.LearnedFromInsight -> event.skill
                else -> null
            }
        }

        // Update character sheet
        val updatedSheet = copy(
            actionInsightTracker = result.updatedTracker,
            skills = skills + newSkills
        )

        return updatedSheet to newSkills
    }

    /**
     * Get partial skills (hints about skills being developed).
     */
    fun getPartialSkills() = actionInsightTracker.partialSkills

    /**
     * Add a discovered fusion recipe.
     */
    fun discoverFusion(recipeId: String): CharacterSheet {
        if (discoveredFusions.any { it.recipeId == recipeId }) return this
        return copy(discoveredFusions = discoveredFusions + DiscoveredFusion(recipeId = recipeId))
    }

    /**
     * Tick all skill cooldowns by one turn.
     */
    fun tickSkillCooldowns(): CharacterSheet {
        val tickedSkills = skills.map { it.tickCooldown() }
        return copy(skills = tickedSkills)
    }

    /**
     * Get all skills that are ready to use.
     */
    fun getReadySkills(): List<Skill> = skills.filter { it.isActive && it.isReady() }

    /**
     * Get all passive skills.
     */
    fun getPassiveSkills(): List<Skill> = skills.filter { !it.isActive }

    /**
     * Check if player can afford to use a skill.
     */
    fun canUseSkill(skill: Skill): Boolean =
        skill.isReady() && skill.canAfford(resources)

    /**
     * Spend resources to use a skill.
     */
    fun spendSkillResources(skill: Skill): CharacterSheet {
        return copy(
            resources = resources.copy(
                currentMana = (resources.currentMana - skill.manaCost).coerceAtLeast(0),
                currentEnergy = (resources.currentEnergy - skill.energyCost).coerceAtLeast(0),
                currentHP = (resources.currentHP - skill.healthCost).coerceAtLeast(1)
            )
        )
    }

    /**
     * Use a skill - spend resources, start cooldown, gain XP.
     */
    fun useSkill(skillId: String, xpGain: Long = 10L): CharacterSheet? {
        val skill = getSkill(skillId) ?: return null
        if (!canUseSkill(skill)) return null

        val usedSkill = skill.startCooldown().gainXP(xpGain)
        return spendSkillResources(skill).updateSkill(usedSkill)
    }

    fun equipItem(item: EquipmentItem): CharacterSheet {
        val newEquipment = when (item) {
            is Weapon -> equipment.copy(weapon = item)
            is Armor -> equipment.copy(armor = item)
            is Accessory -> equipment.copy(accessory = item)
        }
        return copy(equipment = newEquipment)
    }

    fun addToInventory(item: InventoryItem): CharacterSheet {
        return copy(inventory = inventory.addItem(item))
    }

    fun removeFromInventory(itemId: String, quantity: Int = 1): CharacterSheet {
        return copy(inventory = inventory.removeItem(itemId, quantity))
    }

    fun applyStatusEffect(effect: StatusEffect): CharacterSheet {
        return copy(statusEffects = statusEffects + effect)
    }

    fun tickStatusEffects(): CharacterSheet {
        val updated = statusEffects.mapNotNull { effect ->
            val ticked = effect.tick()
            if (ticked.duration > 0) ticked else null
        }
        return copy(statusEffects = updated)
    }

    private fun calculateLevel(totalXP: Long): Int {
        var level = 1
        var xpRequired = 100L
        var accumulated = 0L

        while (totalXP >= accumulated + xpRequired) {
            accumulated += xpRequired
            level++
            xpRequired = level * 100L
        }

        return level
    }
}

@Serializable
internal data class Stats(
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10,
    val defense: Int = 0
) {
    fun increaseOnLevelUp(levels: Int): Stats {
        // Each level grants +2 to two random stats and +1 to others
        // For POC, we'll use a simple pattern: +2 STR/CON, +1 others per level
        return copy(
            strength = strength + (2 * levels),
            dexterity = dexterity + levels,
            constitution = constitution + (2 * levels),
            intelligence = intelligence + levels,
            wisdom = wisdom + levels,
            charisma = charisma + levels
        )
    }
}

@Serializable
internal data class Resources(
    val currentHP: Int,
    val maxHP: Int,
    val currentMana: Int,
    val maxMana: Int,
    val currentEnergy: Int,
    val maxEnergy: Int
) {
    companion object {
        fun fromStats(stats: Stats): Resources {
            val hp = 100 + (stats.constitution * 10)
            val mana = 50 + (stats.intelligence * 5)
            val energy = 100 + (stats.dexterity * 2)

            return Resources(
                currentHP = hp,
                maxHP = hp,
                currentMana = mana,
                maxMana = mana,
                currentEnergy = energy,
                maxEnergy = energy
            )
        }
    }

    fun restoreOnLevelUp(): Resources {
        val hpIncrease = 10
        val manaIncrease = 5
        val energyIncrease = 10

        return copy(
            currentHP = maxHP + hpIncrease,
            maxHP = maxHP + hpIncrease,
            currentMana = maxMana + manaIncrease,
            maxMana = maxMana + manaIncrease,
            currentEnergy = maxEnergy + energyIncrease,
            maxEnergy = maxEnergy + energyIncrease
        )
    }

    fun restore(): Resources {
        return copy(
            currentHP = maxHP,
            currentMana = maxMana,
            currentEnergy = maxEnergy
        )
    }
}

@Serializable
internal data class Equipment(
    val weapon: Weapon? = null,
    val armor: Armor? = null,
    val accessory: Accessory? = null
)

@Serializable
internal sealed class EquipmentItem {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val requiredLevel: Int
}

@Serializable
internal data class Weapon(
    override val id: String,
    override val name: String,
    override val description: String,
    override val requiredLevel: Int = 1,
    val baseDamage: Int,
    val strengthBonus: Int = 0,
    val dexterityBonus: Int = 0
) : EquipmentItem()

@Serializable
internal data class Armor(
    override val id: String,
    override val name: String,
    override val description: String,
    override val requiredLevel: Int = 1,
    val defenseBonus: Int,
    val constitutionBonus: Int = 0
) : EquipmentItem()

@Serializable
internal data class Accessory(
    override val id: String,
    override val name: String,
    override val description: String,
    override val requiredLevel: Int = 1,
    val intelligenceBonus: Int = 0,
    val wisdomBonus: Int = 0
) : EquipmentItem()

@Serializable
internal data class Inventory(
    val items: Map<String, InventoryItem> = emptyMap(),
    val maxSlots: Int = 50
) {
    fun addItem(item: InventoryItem): Inventory {
        val existing = items[item.id]
        return if (existing != null) {
            copy(items = items + (item.id to existing.copy(quantity = existing.quantity + item.quantity)))
        } else {
            if (items.size >= maxSlots) {
                this // Inventory full
            } else {
                copy(items = items + (item.id to item))
            }
        }
    }

    fun removeItem(itemId: String, quantity: Int): Inventory {
        val existing = items[itemId] ?: return this

        return if (existing.quantity <= quantity) {
            copy(items = items - itemId)
        } else {
            copy(items = items + (itemId to existing.copy(quantity = existing.quantity - quantity)))
        }
    }

    fun hasItem(itemId: String, quantity: Int = 1): Boolean {
        return items[itemId]?.quantity ?: 0 >= quantity
    }
}

@Serializable
internal data class InventoryItem(
    val id: String,
    val name: String,
    val description: String,
    val type: ItemType,
    val quantity: Int = 1,
    val stackable: Boolean = true
)

@Serializable
internal enum class ItemType {
    CONSUMABLE,
    QUEST_ITEM,
    CRAFTING_MATERIAL,
    MISC
}

@Serializable
internal data class StatusEffect(
    val id: String,
    val name: String,
    val description: String,
    val duration: Int, // turns/seconds remaining
    val statModifiers: Stats = Stats()
) {
    fun applyTo(stats: Stats): Stats {
        return Stats(
            strength = stats.strength + statModifiers.strength,
            dexterity = stats.dexterity + statModifiers.dexterity,
            constitution = stats.constitution + statModifiers.constitution,
            intelligence = stats.intelligence + statModifiers.intelligence,
            wisdom = stats.wisdom + statModifiers.wisdom,
            charisma = stats.charisma + statModifiers.charisma,
            defense = stats.defense + statModifiers.defense
        )
    }

    fun tick(): StatusEffect {
        return copy(duration = duration - 1)
    }
}
