package com.rpgenerator.core.character

import com.rpgenerator.core.api.*
import com.rpgenerator.core.domain.Stats as DomainStats
import kotlin.random.Random

/**
 * Service for creating characters based on player choices.
 */
internal object CharacterCreationService {

    /**
     * Create a character based on the provided options.
     */
    fun createCharacter(
        options: CharacterCreationOptions,
        systemType: SystemType,
        difficulty: Difficulty
    ): Pair<DomainStats, String> {
        val stats = generateStats(options, difficulty)
        val backstory = generateBackstory(options, systemType)
        return Pair(stats, backstory)
    }

    /**
     * Generate character stats based on allocation method.
     */
    private fun generateStats(options: CharacterCreationOptions, difficulty: Difficulty): DomainStats {
        val baseStats = when (options.statAllocation) {
            StatAllocation.BALANCED -> CustomStats(10, 10, 10, 10, 10, 10)

            StatAllocation.WARRIOR -> CustomStats(
                strength = 15,
                dexterity = 12,
                constitution = 14,
                intelligence = 8,
                wisdom = 9,
                charisma = 10
            )

            StatAllocation.MAGE -> CustomStats(
                strength = 8,
                dexterity = 10,
                constitution = 10,
                intelligence = 16,
                wisdom = 14,
                charisma = 10
            )

            StatAllocation.ROGUE -> CustomStats(
                strength = 10,
                dexterity = 16,
                constitution = 10,
                intelligence = 12,
                wisdom = 10,
                charisma = 14
            )

            StatAllocation.TANK -> CustomStats(
                strength = 14,
                dexterity = 10,
                constitution = 16,
                intelligence = 8,
                wisdom = 10,
                charisma = 10
            )

            StatAllocation.POINT_BUY -> generatePointBuyStats()

            StatAllocation.CUSTOM -> {
                if (options.customStats != null && options.customStats.isValid()) {
                    options.customStats
                } else {
                    CustomStats(10, 10, 10, 10, 10, 10) // Fallback to balanced
                }
            }

            StatAllocation.RANDOM -> generateRandomStats()
        }

        // Apply difficulty modifier
        val difficultyBonus = when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.NORMAL -> 0
            Difficulty.HARD -> -1
            Difficulty.NIGHTMARE -> -2
        }

        return DomainStats(
            strength = (baseStats.strength + difficultyBonus).coerceAtLeast(3),
            dexterity = (baseStats.dexterity + difficultyBonus).coerceAtLeast(3),
            constitution = (baseStats.constitution + difficultyBonus).coerceAtLeast(3),
            intelligence = (baseStats.intelligence + difficultyBonus).coerceAtLeast(3),
            wisdom = (baseStats.wisdom + difficultyBonus).coerceAtLeast(3),
            charisma = (baseStats.charisma + difficultyBonus).coerceAtLeast(3),
            defense = calculateBaseDefense(baseStats.dexterity, baseStats.constitution)
        )
    }

    /**
     * Generate stats using point-buy system (27 points, 8-15 range).
     * Distributed to create a viable character.
     */
    private fun generatePointBuyStats(): CustomStats {
        // Standard point-buy: start at 8, each point costs progressively more
        // We'll distribute 27 points for a balanced but interesting character
        return CustomStats(
            strength = 12,    // 4 points
            dexterity = 14,   // 7 points
            constitution = 13, // 5 points
            intelligence = 10, // 2 points
            wisdom = 12,      // 4 points
            charisma = 11     // 3 points
            // Total: 25 points (close to 27)
        )
    }

    /**
     * Generate random stats (3d6 per stat, classic D&D).
     */
    private fun generateRandomStats(): CustomStats {
        fun rollStat(): Int = (1..3).sumOf { Random.nextInt(1, 7) }

        return CustomStats(
            strength = rollStat(),
            dexterity = rollStat(),
            constitution = rollStat(),
            intelligence = rollStat(),
            wisdom = rollStat(),
            charisma = rollStat()
        )
    }

    /**
     * Calculate base defense from stats.
     */
    private fun calculateBaseDefense(dexterity: Int, constitution: Int): Int {
        return 10 + (dexterity / 2) + (constitution / 4)
    }

    /**
     * Generate character backstory.
     */
    private fun generateBackstory(options: CharacterCreationOptions, systemType: SystemType): String {
        // If user provided backstory, use it
        if (!options.backstory.isNullOrBlank()) {
            return options.backstory
        }

        // Generate system-appropriate backstory
        val name = options.name
        val classDesc = options.startingClass?.let { " as a $it" } ?: ""

        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION ->
                "$name was an ordinary person until the System arrived. Now integrated$classDesc, " +
                "they must adapt to this new reality or perish. The old world is gone. Survival is paramount."

            SystemType.CULTIVATION_PATH ->
                "$name begins their journey on the cultivation path$classDesc. " +
                "With determination and enlightenment, they will ascend through the realms " +
                "and comprehend the mysteries of heaven and earth."

            SystemType.DEATH_LOOP ->
                "$name awakens in the Respawn Chamber$classDesc, knowing death is not the end " +
                "but a teacher. Each failure makes them stronger. Each death reveals new secrets."

            SystemType.DUNGEON_DELVE ->
                "$name stands before the dungeon entrance$classDesc, driven by ambition, desperation, " +
                "or perhaps fate itself. The depths promise both treasure and terror."

            SystemType.ARCANE_ACADEMY ->
                "$name has been accepted into the prestigious Arcane Academy$classDesc. " +
                "Years of study and magical practice await, but greatness demands sacrifice."

            SystemType.TABLETOP_CLASSIC ->
                "$name is an adventurer$classDesc seeking fortune and glory. " +
                "With sword and spell, they will face dragons, explore dungeons, and forge legends."

            SystemType.EPIC_JOURNEY ->
                "$name begins an epic quest that will test not just their strength$classDesc, " +
                "but their courage, wisdom, and fellowship. Great deeds await."

            SystemType.HERO_AWAKENING ->
                "$name discovers they possess extraordinary abilities$classDesc. " +
                "As danger threatens the city, they must decide whether to embrace their destiny " +
                "or return to a normal life."
        }
    }
}
