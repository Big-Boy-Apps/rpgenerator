package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable

/**
 * Class archetype categories for grouping similar playstyles
 */
@Serializable
enum class ClassArchetype(val displayName: String) {
    UNALIGNED("Unaligned"),
    COMBAT("Combat"),
    MYSTICAL("Mystical"),
    HYBRID("Hybrid"),
    SUPPORT("Support"),
    UNIQUE("Unique")
}

/**
 * Grade/Tier system for progression
 * Inspired by Defiance of the Fall, Primal Hunter, etc.
 */
@Serializable
enum class Grade(
    val displayName: String,
    val levelRange: IntRange,
    val description: String
) {
    E_GRADE(
        displayName = "E-Grade",
        levelRange = 1..25,
        description = "Tutorial tier. Basic skills and foundation building."
    ),
    D_GRADE(
        displayName = "D-Grade",
        levelRange = 26..75,
        description = "First evolution. Specialized paths and class selection."
    ),
    C_GRADE(
        displayName = "C-Grade",
        levelRange = 76..150,
        description = "Advanced tier. Skill mastery and path refinement."
    ),
    B_GRADE(
        displayName = "B-Grade",
        levelRange = 151..250,
        description = "Expert tier. Dao comprehension and power consolidation."
    ),
    A_GRADE(
        displayName = "A-Grade",
        levelRange = 251..400,
        description = "Master tier. World-shaking powers emerge."
    ),
    S_GRADE(
        displayName = "S-Grade",
        levelRange = 401..1000,
        description = "Transcendent tier. Peak of mortal achievement."
    );

    companion object {
        fun fromLevel(level: Int): Grade {
            return values().firstOrNull { level in it.levelRange } ?: E_GRADE
        }

        fun isGradeUp(oldLevel: Int, newLevel: Int): Boolean {
            val oldGrade = fromLevel(oldLevel)
            val newGrade = fromLevel(newLevel)
            return newGrade.ordinal > oldGrade.ordinal
        }
    }
}

/**
 * Class archetype - the base path a player chooses
 * Each class has different evolution options at tier-ups
 *
 * Classes are inspired by various LitRPG subgenres:
 * - System Apocalypse (combat/survival focused)
 * - Cultivation (internal energy paths)
 * - Tower/Dungeon (specialized combat roles)
 * - Crafting/Support (non-combat mastery)
 * - Unique paths (rare/exotic options)
 */
@Serializable
internal enum class PlayerClass(
    val displayName: String,
    val description: String,
    internal val statBonuses: Stats,
    val archetype: ClassArchetype = ClassArchetype.COMBAT
) {
    NONE(
        displayName = "Classless",
        description = "The System has not yet assigned you a path. Your choices will shape your destiny.",
        statBonuses = Stats(0, 0, 0, 0, 0, 0),
        archetype = ClassArchetype.UNALIGNED
    ),

    // === COMBAT ARCHETYPES ===
    SLAYER(
        displayName = "Slayer",
        description = "Kill count is everything. Each enemy defeated makes you stronger. The System rewards those who embrace violence.",
        statBonuses = Stats(strength = 4, dexterity = 4, constitution = 2),
        archetype = ClassArchetype.COMBAT
    ),
    BULWARK(
        displayName = "Bulwark",
        description = "An immovable object. Your body becomes your fortress, protecting yourself and those behind you.",
        statBonuses = Stats(constitution = 6, strength = 3, wisdom = 1),
        archetype = ClassArchetype.COMBAT
    ),
    STRIKER(
        displayName = "Striker",
        description = "Speed kills. Hit fast, hit hard, don't get hit back. Mobility is survival.",
        statBonuses = Stats(dexterity = 6, strength = 2, intelligence = 2),
        archetype = ClassArchetype.COMBAT
    ),

    // === MYSTICAL ARCHETYPES ===
    CHANNELER(
        displayName = "Channeler",
        description = "The System's energy flows through you. Shape mana, bend reality, unleash devastation.",
        statBonuses = Stats(intelligence = 5, wisdom = 4, charisma = 1),
        archetype = ClassArchetype.MYSTICAL
    ),
    CULTIVATOR(
        displayName = "Cultivator",
        description = "Refine your inner energy. Strengthen your foundation. The path to immortality begins with a single breath.",
        statBonuses = Stats(wisdom = 5, constitution = 3, intelligence = 2),
        archetype = ClassArchetype.MYSTICAL
    ),
    PSION(
        displayName = "Psion",
        description = "The mind is the ultimate weapon. Telekinesis, telepathy, precognition—thought becomes reality.",
        statBonuses = Stats(intelligence = 4, wisdom = 4, charisma = 2),
        archetype = ClassArchetype.MYSTICAL
    ),

    // === HYBRID ARCHETYPES ===
    ADAPTER(
        displayName = "Adapter",
        description = "No fixed path. Learn from everything. The jack of all trades who becomes master of adaptation.",
        statBonuses = Stats(intelligence = 3, wisdom = 3, dexterity = 2, strength = 2),
        archetype = ClassArchetype.HYBRID
    ),
    SURVIVALIST(
        displayName = "Survivalist",
        description = "When the world ends, you endure. Track, hunt, trap, heal—whatever it takes to see tomorrow.",
        statBonuses = Stats(constitution = 4, wisdom = 3, dexterity = 3),
        archetype = ClassArchetype.HYBRID
    ),
    BLADE_DANCER(
        displayName = "Blade Dancer",
        description = "Combat as art. Weapons as extensions of will. Every fight is a performance, and death is beautiful.",
        statBonuses = Stats(dexterity = 5, charisma = 3, strength = 2),
        archetype = ClassArchetype.HYBRID
    ),

    // === SUPPORT ARCHETYPES ===
    ARTIFICER(
        displayName = "Artificer",
        description = "Build. Create. Improve. The System provides blueprints—you provide the genius to break its limits.",
        statBonuses = Stats(intelligence = 5, dexterity = 3, wisdom = 2),
        archetype = ClassArchetype.SUPPORT
    ),
    HEALER(
        displayName = "Healer",
        description = "Life is sacred. Mend wounds, cure ailments, restore what was broken. In a world of death, you bring hope.",
        statBonuses = Stats(wisdom = 5, charisma = 3, constitution = 2),
        archetype = ClassArchetype.SUPPORT
    ),
    COMMANDER(
        displayName = "Commander",
        description = "Lead from the front or direct from behind. Your presence inspires, your tactics devastate.",
        statBonuses = Stats(charisma = 5, intelligence = 3, wisdom = 2),
        archetype = ClassArchetype.SUPPORT
    ),

    // === UNIQUE ARCHETYPES ===
    CONTRACTOR(
        displayName = "Contractor",
        description = "Entities beyond the System offer power—for a price. Bind spirits, demons, or stranger things to your will.",
        statBonuses = Stats(charisma = 4, wisdom = 4, intelligence = 2),
        archetype = ClassArchetype.UNIQUE
    ),
    GLITCH(
        displayName = "Glitch",
        description = "Something went wrong during Integration. You exist between System rules. Exploit the errors.",
        statBonuses = Stats(intelligence = 4, dexterity = 4, wisdom = 2),
        archetype = ClassArchetype.UNIQUE
    ),
    ECHO(
        displayName = "Echo",
        description = "Memories of the old world cling to you. Channel the skills and powers of who you were—and who you could have been.",
        statBonuses = Stats(wisdom = 4, charisma = 3, constitution = 3),
        archetype = ClassArchetype.UNIQUE
    );

    fun getEvolutionOptions(currentGrade: Grade): List<ClassEvolution> {
        return when (this) {
            NONE -> emptyList()

            // Combat Classes
            SLAYER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Reaper", "Death itself follows in your wake"),
                    ClassEvolution("Berserker", "Pain becomes power, rage becomes strength"),
                    ClassEvolution("Executioner", "One strike, one kill—efficiency perfected")
                )
                else -> emptyList()
            }
            BULWARK -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Juggernaut", "Unstoppable force meets immovable object—you are both"),
                    ClassEvolution("Sentinel", "Guardian of the weak, bane of the wicked"),
                    ClassEvolution("Fortress", "Your body becomes literal armor")
                )
                else -> emptyList()
            }
            STRIKER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Phantom", "So fast you're barely real"),
                    ClassEvolution("Duelist", "Every fight is a dance, and you lead"),
                    ClassEvolution("Tempest", "A storm of blades and motion")
                )
                else -> emptyList()
            }

            // Mystical Classes
            CHANNELER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Elementalist", "Fire, ice, lightning—all bow to your will"),
                    ClassEvolution("Void Mage", "Channel the emptiness between stars"),
                    ClassEvolution("Arcane Savant", "Pure magical theory made devastating practice")
                )
                else -> emptyList()
            }
            CULTIVATOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Sword Saint", "Your blade is your dao, your dao is your blade"),
                    ClassEvolution("Body Refiner", "Forge flesh into something beyond mortal"),
                    ClassEvolution("Qi Master", "Internal energy flows like a raging river")
                )
                else -> emptyList()
            }
            PSION -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Mindbreaker", "Shatter mental defenses like glass"),
                    ClassEvolution("Telekinetic", "Move mountains with a thought"),
                    ClassEvolution("Oracle", "See what was, is, and will be")
                )
                else -> emptyList()
            }

            // Hybrid Classes
            ADAPTER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Mimic", "Every skill you see becomes yours"),
                    ClassEvolution("Polymath", "Master of many, slave to none"),
                    ClassEvolution("Synthesis", "Combine incompatible powers into something new")
                )
                else -> emptyList()
            }
            SURVIVALIST -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Apex Predator", "Top of every food chain"),
                    ClassEvolution("Wayfinder", "No terrain is impassable, no trail invisible"),
                    ClassEvolution("Alchemist", "Nature's secrets become your weapons")
                )
                else -> emptyList()
            }
            BLADE_DANCER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Weapon Maestro", "Every weapon sings in your hands"),
                    ClassEvolution("Spell Sword", "Magic and steel intertwined"),
                    ClassEvolution("Wind Dancer", "Move like air, strike like thunder")
                )
                else -> emptyList()
            }

            // Support Classes
            ARTIFICER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Runesmith", "Inscribe power into permanent form"),
                    ClassEvolution("Golem Maker", "Build allies from nothing"),
                    ClassEvolution("Technomancer", "Merge System tech with your creations")
                )
                else -> emptyList()
            }
            HEALER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Life Weaver", "Restore even the nearly dead"),
                    ClassEvolution("Combat Medic", "Heal and harm in equal measure"),
                    ClassEvolution("Purifier", "Cleanse corruption, cure the incurable")
                )
                else -> emptyList()
            }
            COMMANDER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Warlord", "Your army moves as one devastating unit"),
                    ClassEvolution("Tactician", "Predict and counter every enemy move"),
                    ClassEvolution("Champion", "Lead by example, inspire by presence")
                )
                else -> emptyList()
            }

            // Unique Classes
            CONTRACTOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Demon Binder", "The abyss serves those bold enough to demand"),
                    ClassEvolution("Spirit Caller", "The dead answer your summons"),
                    ClassEvolution("Pact Master", "Multiple contracts, multiple powers")
                )
                else -> emptyList()
            }
            GLITCH -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("System Breaker", "The rules bend around you"),
                    ClassEvolution("Anomaly", "You shouldn't exist—use that"),
                    ClassEvolution("Debugger", "Find and exploit every flaw")
                )
                else -> emptyList()
            }
            ECHO -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Past Life Inheritor", "Your previous selves grant their power"),
                    ClassEvolution("Memory Thief", "Steal skills from others' pasts"),
                    ClassEvolution("Timeline Walker", "Step between what was and what is")
                )
                else -> emptyList()
            }
        }
    }

    companion object {
        /** Get classes grouped by archetype for display */
        fun byArchetype(): Map<ClassArchetype, List<PlayerClass>> {
            return values()
                .filter { it != NONE }
                .groupBy { it.archetype }
        }

        /** Get all selectable classes (excluding NONE) */
        fun selectableClasses(): List<PlayerClass> = values().filter { it != NONE }
    }
}

/**
 * Evolution choice at tier-up
 */
@Serializable
internal data class ClassEvolution(
    val name: String,
    val description: String,
    internal val statModifiers: Stats = Stats(0, 0, 0, 0, 0, 0)
)

/**
 * Tier-up event when player reaches a new grade
 */
@Serializable
internal data class TierUpEvent(
    val oldGrade: Grade,
    val newGrade: Grade,
    val classEvolutionOptions: List<ClassEvolution>,
    val skillSlotUnlocked: Boolean,
    val statPointsAwarded: Int,
    val systemMessage: String
) {
    companion object {
        internal fun create(oldLevel: Int, newLevel: Int, currentClass: PlayerClass): TierUpEvent? {
            if (!Grade.isGradeUp(oldLevel, newLevel)) return null

            val oldGrade = Grade.fromLevel(oldLevel)
            val newGrade = Grade.fromLevel(newLevel)

            val evolutionOptions = if (currentClass == PlayerClass.NONE && newGrade == Grade.D_GRADE) {
                // First class selection at D-Grade
                listOf(
                    ClassEvolution("Warrior", "Path of martial might"),
                    ClassEvolution("Mage", "Path of arcane power"),
                    ClassEvolution("Rogue", "Path of precision and shadows"),
                    ClassEvolution("Ranger", "Path of wilderness mastery"),
                    ClassEvolution("Cultivator", "Path of internal refinement")
                )
            } else {
                currentClass.getEvolutionOptions(newGrade)
            }

            return TierUpEvent(
                oldGrade = oldGrade,
                newGrade = newGrade,
                classEvolutionOptions = evolutionOptions,
                skillSlotUnlocked = true,
                statPointsAwarded = when (newGrade) {
                    Grade.D_GRADE -> 10
                    Grade.C_GRADE -> 20
                    Grade.B_GRADE -> 30
                    Grade.A_GRADE -> 50
                    Grade.S_GRADE -> 100
                    else -> 0
                },
                systemMessage = """
                    ╔════════════════════════════════════════╗
                    ║  GRADE ADVANCEMENT: ${newGrade.displayName}
                    ║  ${newGrade.description}
                    ╚════════════════════════════════════════╝

                    You have transcended ${oldGrade.displayName} and entered ${newGrade.displayName}.
                    The System recognizes your achievements.

                    Choices await.
                """.trimIndent()
            )
        }
    }
}
