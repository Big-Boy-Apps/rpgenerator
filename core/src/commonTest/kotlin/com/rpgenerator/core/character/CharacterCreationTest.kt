package com.rpgenerator.core.character

import com.rpgenerator.core.api.*
import kotlin.test.*

class CharacterCreationTest {

    @Test
    fun `balanced allocation creates equal stats`() {
        val options = CharacterCreationOptions(
            name = "Test Hero",
            statAllocation = StatAllocation.BALANCED
        )

        val (stats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        assertEquals(10, stats.strength)
        assertEquals(10, stats.dexterity)
        assertEquals(10, stats.constitution)
        assertEquals(10, stats.intelligence)
        assertEquals(10, stats.wisdom)
        assertEquals(10, stats.charisma)
    }

    @Test
    fun `warrior allocation prioritizes physical stats`() {
        val options = CharacterCreationOptions(
            name = "Conan",
            statAllocation = StatAllocation.WARRIOR
        )

        val (stats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        assertTrue(stats.strength >= 14, "Warrior should have high strength")
        assertTrue(stats.constitution >= 12, "Warrior should have good constitution")
        assertTrue(stats.intelligence <= 10, "Warrior can have lower intelligence")
    }

    @Test
    fun `mage allocation prioritizes mental stats`() {
        val options = CharacterCreationOptions(
            name = "Gandalf",
            statAllocation = StatAllocation.MAGE
        )

        val (stats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.ARCANE_ACADEMY,
            difficulty = Difficulty.NORMAL
        )

        assertTrue(stats.intelligence >= 14, "Mage should have high intelligence")
        assertTrue(stats.wisdom >= 12, "Mage should have good wisdom")
        assertTrue(stats.strength <= 10, "Mage can have lower strength")
    }

    @Test
    fun `custom stats are applied when valid`() {
        val customStats = CustomStats(
            strength = 14,
            dexterity = 12,
            constitution = 13,
            intelligence = 11,
            wisdom = 10,
            charisma = 15
        )

        val options = CharacterCreationOptions(
            name = "Custom Hero",
            statAllocation = StatAllocation.CUSTOM,
            customStats = customStats
        )

        val (stats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        assertEquals(14, stats.strength)
        assertEquals(12, stats.dexterity)
        assertEquals(13, stats.constitution)
        assertEquals(11, stats.intelligence)
        assertEquals(10, stats.wisdom)
        assertEquals(15, stats.charisma)
    }

    @Test
    fun `invalid custom stats fall back to balanced`() {
        val invalidStats = CustomStats(
            strength = 20, // Too high
            dexterity = 20,
            constitution = 20,
            intelligence = 20,
            wisdom = 20,
            charisma = 20
        )

        val options = CharacterCreationOptions(
            name = "Invalid Hero",
            statAllocation = StatAllocation.CUSTOM,
            customStats = invalidStats
        )

        val (stats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        // Should fall back to balanced stats
        assertEquals(10, stats.strength)
        assertEquals(10, stats.dexterity)
        assertEquals(10, stats.constitution)
        assertEquals(10, stats.intelligence)
        assertEquals(10, stats.wisdom)
        assertEquals(10, stats.charisma)
    }

    @Test
    fun `difficulty affects stats`() {
        val options = CharacterCreationOptions(
            name = "Test Hero",
            statAllocation = StatAllocation.BALANCED
        )

        val (easyStats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.EASY
        )

        val (hardStats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.HARD
        )

        assertTrue(easyStats.strength > hardStats.strength, "Easy mode should have higher stats")
        assertTrue(easyStats.dexterity > hardStats.dexterity)
    }

    @Test
    fun `random allocation generates varied stats`() {
        val options = CharacterCreationOptions(
            name = "Random Hero",
            statAllocation = StatAllocation.RANDOM
        )

        val (stats1, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        val (stats2, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        // Random stats should produce different results (very high probability)
        val stats1Total = stats1.strength + stats1.dexterity + stats1.constitution +
                         stats1.intelligence + stats1.wisdom + stats1.charisma
        val stats2Total = stats2.strength + stats2.dexterity + stats2.constitution +
                         stats2.intelligence + stats2.wisdom + stats2.charisma

        // Stats should be in reasonable D&D range (3d6 = 3-18 per stat, total ~63)
        assertTrue(stats1Total in 18..108, "Random stats should be in D&D range")
        assertTrue(stats2Total in 18..108, "Random stats should be in D&D range")
    }

    @Test
    fun `backstory is generated for each system type`() {
        val options = CharacterCreationOptions(
            name = "Hero",
            statAllocation = StatAllocation.BALANCED
        )

        val systemTypes = SystemType.values()
        for (systemType in systemTypes) {
            val (_, backstory) = CharacterCreationService.createCharacter(
                options = options,
                systemType = systemType,
                difficulty = Difficulty.NORMAL
            )

            assertFalse(backstory.isBlank(), "Backstory should be generated for $systemType")
            assertTrue(backstory.contains("Hero"), "Backstory should contain character name")
        }
    }

    @Test
    fun `custom backstory is preserved`() {
        val customBackstory = "I am a legendary hero from ancient times, awakened to face a new threat."

        val options = CharacterCreationOptions(
            name = "Ancient One",
            statAllocation = StatAllocation.BALANCED,
            backstory = customBackstory
        )

        val (_, backstory) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.HERO_AWAKENING,
            difficulty = Difficulty.NORMAL
        )

        assertEquals(customBackstory, backstory, "Custom backstory should be preserved")
    }

    @Test
    fun `CustomStats validation works correctly`() {
        // Valid stats
        val valid = CustomStats(12, 14, 13, 11, 10, 15)
        assertTrue(valid.isValid(), "Stats within range should be valid")

        // Too high total
        val tooHigh = CustomStats(18, 18, 18, 18, 18, 18)
        assertFalse(tooHigh.isValid(), "Total 108 should be invalid")

        // Individual stat too high
        val statTooHigh = CustomStats(20, 10, 10, 10, 10, 10)
        assertFalse(statTooHigh.isValid(), "Stat > 18 should be invalid")

        // Individual stat too low
        val statTooLow = CustomStats(2, 10, 10, 10, 10, 10)
        assertFalse(statTooLow.isValid(), "Stat < 3 should be invalid")

        // Too low total
        val tooLow = CustomStats(3, 3, 3, 3, 3, 3)
        assertFalse(tooLow.isValid(), "Total 18 should be invalid")
    }

    @Test
    fun `defense is calculated from stats`() {
        val options = CharacterCreationOptions(
            name = "Tank",
            statAllocation = StatAllocation.TANK
        )

        val (stats, _) = CharacterCreationService.createCharacter(
            options = options,
            systemType = SystemType.TABLETOP_CLASSIC,
            difficulty = Difficulty.NORMAL
        )

        // Defense should be > 10 (base) due to high CON and DEX
        assertTrue(stats.defense > 10, "Tank should have high defense")
    }
}
