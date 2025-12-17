package com.rpgenerator.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterSheetTest {

    @Test
    fun `character gains XP and levels up correctly`() {
        val baseStats = Stats(strength = 10, dexterity = 10, constitution = 10)
        val resources = Resources.fromStats(baseStats)
        val sheet = CharacterSheet(level = 1, xp = 0L, baseStats = baseStats, resources = resources)

        // Gain 50 XP (level 2 requires 100 XP total to go from 1→2)
        val afterGain = sheet.gainXP(50L)
        assertEquals(1, afterGain.level, "Should still be level 1")
        assertEquals(50L, afterGain.xp)

        // Gain another 50 XP to reach 100 total - should level up to 2
        val afterLevelUp = afterGain.gainXP(50L)
        assertEquals(2, afterLevelUp.level, "Should be level 2")
        assertEquals(100L, afterLevelUp.xp)

        // Base stats should have increased
        assertTrue(afterLevelUp.baseStats.strength > baseStats.strength, "STR should increase on level up")
        assertTrue(afterLevelUp.baseStats.constitution > baseStats.constitution, "CON should increase on level up")
    }

    @Test
    fun `effective stats include equipment bonuses`() {
        val baseStats = Stats(strength = 10, dexterity = 10, constitution = 10)
        val resources = Resources.fromStats(baseStats)
        var sheet = CharacterSheet(level = 1, xp = 0L, baseStats = baseStats, resources = resources)

        val weapon = Weapon(
            id = "iron-sword",
            name = "Iron Sword",
            description = "A basic iron sword",
            baseDamage = 5,
            strengthBonus = 3,
            dexterityBonus = 1
        )

        sheet = sheet.equipItem(weapon)
        val effective = sheet.effectiveStats()

        assertEquals(13, effective.strength, "Should include weapon STR bonus")
        assertEquals(11, effective.dexterity, "Should include weapon DEX bonus")
        assertEquals(10, effective.constitution, "CON should be unchanged")
    }

    @Test
    fun `character takes damage and heals`() {
        val baseStats = Stats(constitution = 10)
        val resources = Resources.fromStats(baseStats)
        val sheet = CharacterSheet(level = 1, xp = 0L, baseStats = baseStats, resources = resources)

        val maxHP = sheet.resources.maxHP

        // Take damage
        val afterDamage = sheet.takeDamage(30)
        assertEquals(maxHP - 30, afterDamage.resources.currentHP)

        // Heal
        val afterHeal = afterDamage.heal(15)
        assertEquals(maxHP - 15, afterHeal.resources.currentHP)

        // Can't overheal
        val afterOverheal = afterHeal.heal(1000)
        assertEquals(maxHP, afterOverheal.resources.currentHP)
    }

    @Test
    fun `inventory operations work correctly`() {
        val baseStats = Stats()
        val resources = Resources.fromStats(baseStats)
        var sheet = CharacterSheet(level = 1, xp = 0L, baseStats = baseStats, resources = resources)

        val potion = InventoryItem(
            id = "health-potion",
            name = "Health Potion",
            description = "Restores 50 HP",
            type = ItemType.CONSUMABLE,
            quantity = 3
        )

        // Add item
        sheet = sheet.addToInventory(potion)
        assertTrue(sheet.inventory.hasItem("health-potion", 3))

        // Remove some
        sheet = sheet.removeFromInventory("health-potion", 1)
        assertTrue(sheet.inventory.hasItem("health-potion", 2))

        // Remove all
        sheet = sheet.removeFromInventory("health-potion", 2)
        assertTrue(!sheet.inventory.hasItem("health-potion", 1))
    }

    @Test
    fun `status effects apply and tick correctly`() {
        val baseStats = Stats(strength = 10)
        val resources = Resources.fromStats(baseStats)
        var sheet = CharacterSheet(level = 1, xp = 0L, baseStats = baseStats, resources = resources)

        val buff = StatusEffect(
            id = "strength-buff",
            name = "Strength Buff",
            description = "+5 STR for 3 turns",
            duration = 3,
            statModifiers = Stats(strength = 5)
        )

        // Apply buff
        sheet = sheet.applyStatusEffect(buff)
        assertEquals(15, sheet.effectiveStats().strength, "Should include buff STR bonus")

        // Tick once
        sheet = sheet.tickStatusEffects()
        assertEquals(1, sheet.statusEffects.size)
        assertEquals(2, sheet.statusEffects[0].duration)

        // Tick twice more - should expire
        sheet = sheet.tickStatusEffects()
        sheet = sheet.tickStatusEffects()
        assertEquals(0, sheet.statusEffects.size, "Buff should have expired")
        assertEquals(10, sheet.effectiveStats().strength, "STR should be back to base")
    }

    @Test
    fun `multiple level ups calculate correctly`() {
        val baseStats = Stats(strength = 10)
        val resources = Resources.fromStats(baseStats)
        val sheet = CharacterSheet(level = 1, xp = 0L, baseStats = baseStats, resources = resources)

        // Gain enough XP for multiple levels
        // Level 1→2: 100 XP, Level 2→3: 200 XP more (300 total), Level 3→4: 300 XP more (600 total)
        val afterGain = sheet.gainXP(600L)

        assertEquals(4, afterGain.level, "Should be level 4")
        assertEquals(600L, afterGain.xp)
    }
}
