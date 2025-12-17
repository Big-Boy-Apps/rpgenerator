package com.rpgenerator.core.skill

/**
 * Central repository for skill definitions, insight thresholds, and fusion recipes.
 *
 * This is the "skill book" of the game - all learnable skills are defined here.
 */
internal object SkillDatabase {

    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT SKILLS - Physical Attacks
    // ═══════════════════════════════════════════════════════════════════════════

    val powerStrike = Skill(
        id = "power_strike",
        name = "Power Strike",
        description = "A focused, powerful blow that deals heavy damage.",
        rarity = SkillRarity.COMMON,
        energyCost = 15,
        baseCooldown = 1,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 25,
                scalingStat = StatType.STRENGTH,
                scalingRatio = 0.6,
                damageType = DamageType.PHYSICAL
            )
        ),
        fusionTags = setOf("melee", "offensive", "physical"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT,
        evolutionPaths = listOf(
            SkillEvolutionPath(
                evolvesIntoId = "crushing_blow",
                evolvesIntoName = "Crushing Blow",
                description = "A devastating attack that ignores a portion of armor.",
                requirements = listOf(EvolutionRequirement.StatMinimum(StatType.STRENGTH, 20))
            ),
            SkillEvolutionPath(
                evolvesIntoId = "precision_strike",
                evolvesIntoName = "Precision Strike",
                description = "A surgical strike targeting vital points for critical damage.",
                requirements = listOf(EvolutionRequirement.StatMinimum(StatType.DEXTERITY, 18))
            )
        )
    )

    val quickSlash = Skill(
        id = "quick_slash",
        name = "Quick Slash",
        description = "A swift, precise cut that sacrifices power for speed.",
        rarity = SkillRarity.COMMON,
        energyCost = 10,
        baseCooldown = 0,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 15,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.5,
                damageType = DamageType.PHYSICAL
            )
        ),
        fusionTags = setOf("melee", "offensive", "fast"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val heavyCleave = Skill(
        id = "heavy_cleave",
        name = "Heavy Cleave",
        description = "A wide, sweeping attack that can hit multiple enemies.",
        rarity = SkillRarity.UNCOMMON,
        energyCost = 25,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 20,
                scalingStat = StatType.STRENGTH,
                scalingRatio = 0.4,
                damageType = DamageType.PHYSICAL
            )
        ),
        targetType = TargetType.ALL_ENEMIES,
        fusionTags = setOf("melee", "offensive", "aoe"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // COMBAT SKILLS - Ranged Attacks
    // ═══════════════════════════════════════════════════════════════════════════

    val precisionShot = Skill(
        id = "precision_shot",
        name = "Precision Shot",
        description = "A carefully aimed shot that rarely misses.",
        rarity = SkillRarity.COMMON,
        energyCost = 12,
        baseCooldown = 0,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 18,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.6,
                damageType = DamageType.PHYSICAL
            )
        ),
        fusionTags = setOf("ranged", "offensive", "precision"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val multiShot = Skill(
        id = "multi_shot",
        name = "Multi-Shot",
        description = "Fire multiple arrows in rapid succession.",
        rarity = SkillRarity.UNCOMMON,
        energyCost = 20,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 12,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.3,
                damageType = DamageType.PHYSICAL
            )
        ),
        targetType = TargetType.ALL_ENEMIES,
        fusionTags = setOf("ranged", "offensive", "aoe"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // MAGIC SKILLS - Elemental
    // ═══════════════════════════════════════════════════════════════════════════

    val fireball = Skill(
        id = "fireball",
        name = "Fireball",
        description = "Hurl a ball of flame at your enemy.",
        rarity = SkillRarity.COMMON,
        manaCost = 20,
        baseCooldown = 1,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 30,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.7,
                damageType = DamageType.FIRE
            )
        ),
        fusionTags = setOf("magic", "fire", "offensive"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT,
        evolutionPaths = listOf(
            SkillEvolutionPath(
                evolvesIntoId = "inferno",
                evolvesIntoName = "Inferno",
                description = "A massive explosion of flame that engulfs all enemies.",
                requirements = listOf(EvolutionRequirement.StatMinimum(StatType.INTELLIGENCE, 25))
            ),
            SkillEvolutionPath(
                evolvesIntoId = "flame_lance",
                evolvesIntoName = "Flame Lance",
                description = "A concentrated beam of fire that pierces through defenses.",
                requirements = listOf(EvolutionRequirement.LevelMinimum(15))
            )
        )
    )

    val frostBolt = Skill(
        id = "frost_bolt",
        name = "Frost Bolt",
        description = "Launch a shard of ice that chills the target.",
        rarity = SkillRarity.COMMON,
        manaCost = 18,
        baseCooldown = 1,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 22,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.5,
                damageType = DamageType.ICE
            ),
            SkillEffect.Debuff(
                statAffected = StatType.DEXTERITY,
                basePenalty = 3,
                duration = 2
            )
        ),
        fusionTags = setOf("magic", "ice", "offensive", "debuff"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val lightningBolt = Skill(
        id = "lightning_bolt",
        name = "Lightning Bolt",
        description = "Call down a bolt of lightning on your foe.",
        rarity = SkillRarity.UNCOMMON,
        manaCost = 25,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 35,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.8,
                damageType = DamageType.LIGHTNING
            )
        ),
        fusionTags = setOf("magic", "lightning", "offensive"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val arcaneBlast = Skill(
        id = "arcane_blast",
        name = "Arcane Blast",
        description = "Pure magical energy concentrated into a devastating attack.",
        rarity = SkillRarity.RARE,
        manaCost = 35,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 45,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 1.0,
                damageType = DamageType.MAGICAL
            )
        ),
        fusionTags = setOf("magic", "offensive", "pure"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SUPPORT SKILLS - Healing
    // ═══════════════════════════════════════════════════════════════════════════

    val minorHeal = Skill(
        id = "minor_heal",
        name = "Minor Heal",
        description = "Channel restorative energy to heal wounds.",
        rarity = SkillRarity.COMMON,
        manaCost = 15,
        baseCooldown = 1,
        effects = listOf(
            SkillEffect.Heal(
                baseAmount = 25,
                scalingStat = StatType.WISDOM,
                scalingRatio = 0.5
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("heal", "support", "holy"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.SUPPORT,
        evolutionPaths = listOf(
            SkillEvolutionPath(
                evolvesIntoId = "greater_heal",
                evolvesIntoName = "Greater Heal",
                description = "A powerful healing spell that restores significant health.",
                requirements = listOf(EvolutionRequirement.StatMinimum(StatType.WISDOM, 20))
            ),
            SkillEvolutionPath(
                evolvesIntoId = "rejuvenation",
                evolvesIntoName = "Rejuvenation",
                description = "A sustained healing effect that mends wounds over time.",
                requirements = listOf(EvolutionRequirement.LevelMinimum(12))
            )
        )
    )

    val regeneration = Skill(
        id = "regeneration",
        name = "Regeneration",
        description = "Grant yourself sustained healing over time.",
        rarity = SkillRarity.UNCOMMON,
        manaCost = 25,
        baseCooldown = 4,
        effects = listOf(
            SkillEffect.HealOverTime(
                baseHealPerTurn = 10,
                duration = 5,
                scalingStat = StatType.WISDOM,
                scalingRatio = 0.2
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("heal", "support", "buff"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.SUPPORT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // DEFENSIVE SKILLS
    // ═══════════════════════════════════════════════════════════════════════════

    val ironSkin = Skill(
        id = "iron_skin",
        name = "Iron Skin",
        description = "Harden your body to resist incoming damage.",
        rarity = SkillRarity.COMMON,
        energyCost = 20,
        baseCooldown = 4,
        effects = listOf(
            SkillEffect.Buff(
                statAffected = StatType.CONSTITUTION,
                baseBonus = 8,
                duration = 3
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("defensive", "buff", "physical"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.DEFENSIVE
    )

    val manaShield = Skill(
        id = "mana_shield",
        name = "Mana Shield",
        description = "Create a barrier of magical energy that absorbs damage.",
        rarity = SkillRarity.UNCOMMON,
        manaCost = 30,
        baseCooldown = 5,
        effects = listOf(
            SkillEffect.Shield(
                baseAmount = 40,
                duration = 3,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.6
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("defensive", "magic", "shield"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.DEFENSIVE
    )

    val parry = Skill(
        id = "parry",
        name = "Parry",
        description = "Deflect incoming attacks with precise timing.",
        rarity = SkillRarity.COMMON,
        energyCost = 10,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Buff(
                statAffected = StatType.DEXTERITY,
                baseBonus = 10,
                duration = 1
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("defensive", "melee", "counter"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.DEFENSIVE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // STEALTH/ROGUE SKILLS
    // ═══════════════════════════════════════════════════════════════════════════

    val shadowStrike = Skill(
        id = "shadow_strike",
        name = "Shadow Strike",
        description = "Attack from the shadows for devastating damage.",
        rarity = SkillRarity.UNCOMMON,
        energyCost = 25,
        baseCooldown = 3,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 40,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.8,
                damageType = DamageType.PHYSICAL
            )
        ),
        fusionTags = setOf("stealth", "melee", "damage"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val poisonBlade = Skill(
        id = "poison_blade",
        name = "Poison Blade",
        description = "Coat your weapon in venom that damages over time.",
        rarity = SkillRarity.UNCOMMON,
        energyCost = 18,
        baseCooldown = 4,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 12,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.3,
                damageType = DamageType.PHYSICAL
            ),
            SkillEffect.DamageOverTime(
                baseDamagePerTurn = 8,
                duration = 4,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.15,
                damageType = DamageType.POISON
            )
        ),
        fusionTags = setOf("melee", "poison", "damage", "dot"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val evasion = Skill(
        id = "evasion",
        name = "Evasion",
        description = "Enhance your ability to dodge incoming attacks.",
        rarity = SkillRarity.COMMON,
        energyCost = 15,
        baseCooldown = 3,
        effects = listOf(
            SkillEffect.Buff(
                statAffected = StatType.DEXTERITY,
                baseBonus = 12,
                duration = 2
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("defensive", "movement", "dodge"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.MOVEMENT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CULTIVATION SKILLS
    // ═══════════════════════════════════════════════════════════════════════════

    val innerFocus = Skill(
        id = "inner_focus",
        name = "Inner Focus",
        description = "Center your qi to restore energy and clarity.",
        rarity = SkillRarity.COMMON,
        baseCooldown = 5,
        effects = listOf(
            SkillEffect.RestoreResource(
                resourceType = ResourceType.ENERGY,
                baseAmount = 30,
                scalingStat = StatType.WISDOM,
                scalingRatio = 0.3
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("cultivation", "restoration", "qi"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.UTILITY
    )

    val qiStrike = Skill(
        id = "qi_strike",
        name = "Qi Strike",
        description = "Channel internal energy through your fist for devastating impact.",
        rarity = SkillRarity.UNCOMMON,
        energyCost = 20,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 35,
                scalingStat = StatType.WISDOM,
                scalingRatio = 0.6,
                damageType = DamageType.TRUE
            )
        ),
        fusionTags = setOf("cultivation", "melee", "damage", "qi"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val ironBody = Skill(
        id = "iron_body",
        name = "Iron Body",
        description = "Cultivate your body to become as hard as iron.",
        rarity = SkillRarity.RARE,
        isActive = false,  // Passive skill
        effects = listOf(
            SkillEffect.Passive(
                statBonuses = mapOf(
                    StatType.CONSTITUTION to 5,
                    StatType.STRENGTH to 3
                ),
                description = "Your body is tempered through cultivation"
            )
        ),
        targetType = TargetType.SELF,
        fusionTags = setOf("cultivation", "passive", "defensive"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.DEFENSIVE
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // EVOLVED SKILLS (Unlocked through evolution)
    // ═══════════════════════════════════════════════════════════════════════════

    val crushingBlow = Skill(
        id = "crushing_blow",
        name = "Crushing Blow",
        description = "A devastating attack that shatters armor and bones alike.",
        rarity = SkillRarity.RARE,
        energyCost = 30,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 45,
                scalingStat = StatType.STRENGTH,
                scalingRatio = 0.8,
                damageType = DamageType.PHYSICAL
            ),
            SkillEffect.Debuff(
                statAffected = StatType.CONSTITUTION,
                basePenalty = 5,
                duration = 3
            )
        ),
        fusionTags = setOf("melee", "offensive", "physical", "armor_break"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val inferno = Skill(
        id = "inferno",
        name = "Inferno",
        description = "Summon a raging inferno that engulfs all enemies in flame.",
        rarity = SkillRarity.EPIC,
        manaCost = 50,
        baseCooldown = 4,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 40,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.9,
                damageType = DamageType.FIRE
            ),
            SkillEffect.DamageOverTime(
                baseDamagePerTurn = 15,
                duration = 3,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.3,
                damageType = DamageType.FIRE
            )
        ),
        targetType = TargetType.ALL_ENEMIES,
        fusionTags = setOf("magic", "fire", "offensive", "aoe"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // FUSED SKILLS (Created through fusion)
    // ═══════════════════════════════════════════════════════════════════════════

    val flameBlade = Skill(
        id = "flame_blade",
        name = "Flame Blade",
        description = "Wreath your weapon in flame for devastating fire damage.",
        rarity = SkillRarity.RARE,
        energyCost = 15,
        manaCost = 15,
        baseCooldown = 2,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 30,
                scalingStat = StatType.STRENGTH,
                scalingRatio = 0.4,
                damageType = DamageType.PHYSICAL
            ),
            SkillEffect.Damage(
                baseAmount = 25,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.4,
                damageType = DamageType.FIRE
            )
        ),
        fusionTags = setOf("melee", "fire", "offensive", "hybrid"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val lifeSteal = Skill(
        id = "life_steal",
        name = "Life Steal",
        description = "Drain the life force from your enemy to heal yourself.",
        rarity = SkillRarity.RARE,
        manaCost = 25,
        baseCooldown = 3,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 25,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.5,
                damageType = DamageType.DARK
            ),
            SkillEffect.Heal(
                baseAmount = 15,
                scalingStat = StatType.INTELLIGENCE,
                scalingRatio = 0.3
            )
        ),
        fusionTags = setOf("magic", "dark", "drain", "heal"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val counterStrike = Skill(
        id = "counter_strike",
        name = "Counter Strike",
        description = "Parry an attack and immediately riposte with devastating force.",
        rarity = SkillRarity.RARE,
        energyCost = 20,
        baseCooldown = 3,
        effects = listOf(
            SkillEffect.Buff(
                statAffected = StatType.DEXTERITY,
                baseBonus = 15,
                duration = 1
            ),
            SkillEffect.Damage(
                baseAmount = 35,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 0.7,
                damageType = DamageType.PHYSICAL
            )
        ),
        fusionTags = setOf("melee", "defensive", "counter", "offensive"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    val assassinate = Skill(
        id = "assassinate",
        name = "Assassinate",
        description = "Strike from the shadows with lethal precision.",
        rarity = SkillRarity.EPIC,
        energyCost = 40,
        baseCooldown = 5,
        effects = listOf(
            SkillEffect.Damage(
                baseAmount = 80,
                scalingStat = StatType.DEXTERITY,
                scalingRatio = 1.2,
                damageType = DamageType.TRUE
            )
        ),
        fusionTags = setOf("stealth", "melee", "assassination", "true_damage"),
        acquisitionSource = AcquisitionSource.Unknown,
        category = SkillCategory.COMBAT
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL REGISTRY - All skills by ID
    // ═══════════════════════════════════════════════════════════════════════════

    private val allSkills: Map<String, Skill> by lazy {
        listOf(
            // Combat - Physical
            powerStrike, quickSlash, heavyCleave,
            // Combat - Ranged
            precisionShot, multiShot,
            // Combat - Magic
            fireball, frostBolt, lightningBolt, arcaneBlast,
            // Support
            minorHeal, regeneration,
            // Defensive
            ironSkin, manaShield, parry,
            // Stealth
            shadowStrike, poisonBlade, evasion,
            // Cultivation
            innerFocus, qiStrike, ironBody,
            // Evolved
            crushingBlow, inferno,
            // Fused
            flameBlade, lifeSteal, counterStrike, assassinate
        ).associateBy { it.id }
    }

    /**
     * Get a skill by its ID.
     */
    fun getSkill(id: String): Skill? = allSkills[id]

    /**
     * Get a skill and customize its acquisition source.
     */
    fun getSkillWithSource(id: String, source: AcquisitionSource): Skill? =
        allSkills[id]?.copy(acquisitionSource = source)

    /**
     * Get all skills of a specific rarity.
     */
    fun getSkillsByRarity(rarity: SkillRarity): List<Skill> =
        allSkills.values.filter { it.rarity == rarity }

    /**
     * Get all skills in a specific category.
     */
    fun getSkillsByCategory(category: SkillCategory): List<Skill> =
        allSkills.values.filter { it.category == category }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLASS STARTER SKILLS
    // ═══════════════════════════════════════════════════════════════════════════

    val classStarterSkills: Map<String, List<String>> = mapOf(
        "warrior" to listOf("power_strike", "iron_skin"),
        "mage" to listOf("fireball", "mana_shield"),
        "rogue" to listOf("quick_slash", "evasion"),
        "ranger" to listOf("precision_shot", "evasion"),
        "cultivator" to listOf("qi_strike", "inner_focus"),
        "paladin" to listOf("power_strike", "minor_heal"),
        "assassin" to listOf("shadow_strike", "poison_blade"),
        "elementalist" to listOf("fireball", "frost_bolt")
    )

    /**
     * Get starting skills for a class.
     */
    fun getStarterSkills(className: String): List<Skill> {
        val skillIds = classStarterSkills[className.lowercase()] ?: return emptyList()
        return skillIds.mapNotNull { id ->
            getSkillWithSource(id, AcquisitionSource.ClassStarter(className))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACTION INSIGHT THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════════════

    val insightThresholds: List<InsightThreshold> = listOf(
        // Combat actions
        InsightThreshold(
            actionType = "sword_slash",
            skillId = "power_strike",
            partialUnlockCount = 25,
            fullUnlockCount = 50,
            skillName = "Power Strike"
        ),
        InsightThreshold(
            actionType = "sword_thrust",
            skillId = "quick_slash",
            partialUnlockCount = 20,
            fullUnlockCount = 40,
            skillName = "Quick Slash"
        ),
        InsightThreshold(
            actionType = "basic_attack",
            skillId = "power_strike",
            partialUnlockCount = 40,
            fullUnlockCount = 80,
            skillName = "Power Strike"
        ),
        InsightThreshold(
            actionType = "bow_shot",
            skillId = "precision_shot",
            partialUnlockCount = 30,
            fullUnlockCount = 60,
            skillName = "Precision Shot"
        ),

        // Magic actions
        InsightThreshold(
            actionType = "fire_magic",
            skillId = "fireball",
            partialUnlockCount = 15,
            fullUnlockCount = 30,
            skillName = "Fireball"
        ),
        InsightThreshold(
            actionType = "ice_magic",
            skillId = "frost_bolt",
            partialUnlockCount = 15,
            fullUnlockCount = 30,
            skillName = "Frost Bolt"
        ),
        InsightThreshold(
            actionType = "lightning_magic",
            skillId = "lightning_bolt",
            partialUnlockCount = 20,
            fullUnlockCount = 45,
            skillName = "Lightning Bolt"
        ),
        InsightThreshold(
            actionType = "healing_magic",
            skillId = "minor_heal",
            partialUnlockCount = 15,
            fullUnlockCount = 30,
            skillName = "Minor Heal"
        ),

        // Stealth/Movement
        InsightThreshold(
            actionType = "stealth_movement",
            skillId = "shadow_strike",
            partialUnlockCount = 30,
            fullUnlockCount = 60,
            skillName = "Shadow Strike"
        ),
        InsightThreshold(
            actionType = "evasion",
            skillId = "evasion",
            partialUnlockCount = 25,
            fullUnlockCount = 50,
            skillName = "Evasion"
        ),

        // Cultivation
        InsightThreshold(
            actionType = "cultivation_meditate",
            skillId = "inner_focus",
            partialUnlockCount = 20,
            fullUnlockCount = 40,
            skillName = "Inner Focus"
        ),
        InsightThreshold(
            actionType = "energy_circulation",
            skillId = "qi_strike",
            partialUnlockCount = 35,
            fullUnlockCount = 70,
            skillName = "Qi Strike"
        ),

        // Defense
        InsightThreshold(
            actionType = "defensive_block",
            skillId = "parry",
            partialUnlockCount = 20,
            fullUnlockCount = 40,
            skillName = "Parry"
        ),
        InsightThreshold(
            actionType = "defensive_stance",
            skillId = "iron_skin",
            partialUnlockCount = 25,
            fullUnlockCount = 50,
            skillName = "Iron Skin"
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // FUSION RECIPES
    // ═══════════════════════════════════════════════════════════════════════════

    val fusionRecipes: List<SkillFusionRecipe> = listOf(
        // Fire + Melee = Flame Blade
        SkillFusionRecipe(
            id = "fusion_flame_blade",
            name = "Flame Blade Fusion",
            description = "Combine fire magic with melee prowess to create a flaming weapon skill.",
            inputSkillIds = setOf("fireball", "power_strike"),
            minimumLevels = mapOf("fireball" to 5, "power_strike" to 5),
            resultSkillId = "flame_blade",
            resultSkillName = "Flame Blade",
            resultRarity = SkillRarity.RARE,
            isDiscoverable = true,
            discoveryHint = "The flames seem to dance with your blade movements..."
        ),

        // Heal + Dark Damage = Life Steal
        SkillFusionRecipe(
            id = "fusion_life_steal",
            name = "Life Drain Fusion",
            description = "Twist healing magic into a parasitic drain.",
            inputSkillIds = setOf("minor_heal", "shadow_strike"),
            minimumLevels = mapOf("minor_heal" to 6, "shadow_strike" to 6),
            resultSkillId = "life_steal",
            resultSkillName = "Life Steal",
            resultRarity = SkillRarity.RARE,
            isDiscoverable = true,
            discoveryHint = "You sense an affinity between life and shadow..."
        ),

        // Defense + Offense = Counter
        SkillFusionRecipe(
            id = "fusion_counter_strike",
            name = "Counter Strike Fusion",
            description = "Perfect the art of turning defense into offense.",
            inputSkillIds = setOf("parry", "power_strike"),
            minimumLevels = mapOf("parry" to 7, "power_strike" to 7),
            resultSkillId = "counter_strike",
            resultSkillName = "Counter Strike",
            resultRarity = SkillRarity.RARE,
            isDiscoverable = true,
            discoveryHint = "Your defensive instincts merge with offensive power..."
        ),

        // Stealth + Damage = Assassination
        SkillFusionRecipe(
            id = "fusion_assassinate",
            name = "Assassination Fusion",
            description = "Master the deadly art of the unseen killer.",
            inputSkillIds = setOf("shadow_strike", "quick_slash", "evasion"),
            minimumLevels = mapOf("shadow_strike" to 8, "quick_slash" to 8, "evasion" to 6),
            resultSkillId = "assassinate",
            resultSkillName = "Assassinate",
            resultRarity = SkillRarity.EPIC,
            isDiscoverable = true,
            discoveryHint = "Shadows, speed, and steel blend into lethal harmony..."
        ),

        // Fire + Ice = Steam Burst (unlisted for now - could add this skill)
        SkillFusionRecipe(
            id = "fusion_elemental_burst",
            name = "Elemental Burst Fusion",
            description = "Combine opposing elements for chaotic power.",
            inputSkillIds = setOf("fireball", "frost_bolt"),
            minimumLevels = mapOf("fireball" to 6, "frost_bolt" to 6),
            resultSkillId = "arcane_blast",
            resultSkillName = "Arcane Blast",
            resultRarity = SkillRarity.RARE,
            isDiscoverable = true,
            discoveryHint = "Fire and ice war within you, seeking balance..."
        )
    )

    /**
     * Find all fusion recipes that could be made with the given skill IDs.
     */
    fun findAvailableFusions(ownedSkillIds: Set<String>): List<SkillFusionRecipe> =
        fusionRecipes.filter { recipe ->
            recipe.inputSkillIds.all { it in ownedSkillIds }
        }

    /**
     * Find recipes where the player has some but not all required skills.
     */
    fun findPartialFusions(ownedSkillIds: Set<String>): List<Pair<SkillFusionRecipe, Set<String>>> =
        fusionRecipes.mapNotNull { recipe ->
            val missing = recipe.inputSkillIds - ownedSkillIds
            if (missing.isNotEmpty() && missing.size < recipe.inputSkillIds.size) {
                recipe to missing
            } else {
                null
            }
        }

    /**
     * Get a fusion recipe by ID.
     */
    fun getFusionRecipe(id: String): SkillFusionRecipe? =
        fusionRecipes.find { it.id == id }
}
