package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable

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
 */
@Serializable
internal enum class PlayerClass(
    val displayName: String,
    val description: String,
    internal val statBonuses: Stats
) {
    NONE(
        displayName = "Classless",
        description = "No class chosen yet. Select at D-Grade.",
        statBonuses = Stats(0, 0, 0, 0, 0, 0)
    ),
    WARRIOR(
        displayName = "Warrior",
        description = "Masters of physical combat and endurance.",
        statBonuses = Stats(strength = 5, constitution = 5, dexterity = 2)
    ),
    MAGE(
        displayName = "Mage",
        description = "Wielders of arcane power and elemental forces.",
        statBonuses = Stats(intelligence = 5, wisdom = 5, charisma = 2)
    ),
    ROGUE(
        displayName = "Rogue",
        description = "Swift strikers who rely on precision and cunning.",
        statBonuses = Stats(dexterity = 5, intelligence = 3, strength = 2)
    ),
    RANGER(
        displayName = "Ranger",
        description = "Survivalists who blend martial and mystical arts.",
        statBonuses = Stats(dexterity = 4, wisdom = 4, constitution = 2)
    ),
    CULTIVATOR(
        displayName = "Cultivator",
        description = "Those who walk the path of internal energy refinement.",
        statBonuses = Stats(wisdom = 5, constitution = 4, intelligence = 3)
    );

    fun getEvolutionOptions(currentGrade: Grade): List<ClassEvolution> {
        return when (this) {
            NONE -> listOf() // Will get initial class choices at D-Grade
            WARRIOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Berserker", "Unleash primal fury for devastating power"),
                    ClassEvolution("Guardian", "Unbreakable defense and protective auras"),
                    ClassEvolution("Weapon Master", "Transcendent skill with chosen weapons")
                )
                Grade.C_GRADE -> listOf(
                    ClassEvolution("Titan", "Grow to immense size and strength"),
                    ClassEvolution("Warlord", "Command the battlefield with tactical prowess"),
                    ClassEvolution("Duelist", "One-on-one combat perfection")
                )
                else -> emptyList()
            }
            MAGE -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Elementalist", "Master of elemental forces"),
                    ClassEvolution("Arcanist", "Pure arcane manipulation"),
                    ClassEvolution("Summoner", "Call forth creatures from beyond")
                )
                Grade.C_GRADE -> listOf(
                    ClassEvolution("Archmage", "Pinnacle of magical might"),
                    ClassEvolution("Chronomancer", "Bend time itself to your will"),
                    ClassEvolution("Voidcaller", "Channel the power of the void")
                )
                else -> emptyList()
            }
            ROGUE -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Assassin", "Silent death from the shadows"),
                    ClassEvolution("Trickster", "Illusions and misdirection"),
                    ClassEvolution("Shadow Dancer", "Merge with darkness itself")
                )
                else -> emptyList()
            }
            RANGER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Beast Master", "Bond with powerful creatures"),
                    ClassEvolution("Sharpshooter", "Perfect accuracy at any range"),
                    ClassEvolution("Tracker", "Hunt anything, anywhere")
                )
                else -> emptyList()
            }
            CULTIVATOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Sword Cultivator", "The sword is the Dao"),
                    ClassEvolution("Body Cultivator", "Forge an indestructible body"),
                    ClassEvolution("Qi Cultivator", "Pure energy manipulation")
                )
                else -> emptyList()
            }
        }
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
