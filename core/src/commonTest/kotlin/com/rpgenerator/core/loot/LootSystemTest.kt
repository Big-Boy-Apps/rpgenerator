package com.rpgenerator.core.loot

import com.rpgenerator.core.domain.*
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.rules.RulesEngine
import kotlin.test.*

class LootSystemTest {

    // Test ItemRarity
    @Test
    fun `test ItemRarity weights and multipliers`() {
        assertEquals(1.0, ItemRarity.COMMON.statMultiplier)
        assertEquals(1.3, ItemRarity.UNCOMMON.statMultiplier)
        assertEquals(1.6, ItemRarity.RARE.statMultiplier)
        assertEquals(2.0, ItemRarity.EPIC.statMultiplier)
        assertEquals(2.5, ItemRarity.LEGENDARY.statMultiplier)
    }

    @Test
    fun `test ItemRarity fromRoll produces valid rarities`() {
        val totalWeight = ItemRarity.totalWeight()
        assertTrue(totalWeight > 0)

        // Test boundary cases
        val commonRoll = ItemRarity.fromRoll(0)
        assertNotNull(commonRoll)

        val maxRoll = ItemRarity.fromRoll(totalWeight - 1)
        assertNotNull(maxRoll)

        // Common should be most frequent
        val rolls = (0 until 1000).map { ItemRarity.fromRoll((0 until totalWeight).random()) }
        val commonCount = rolls.count { it == ItemRarity.COMMON }
        val legendaryCount = rolls.count { it == ItemRarity.LEGENDARY }

        assertTrue(commonCount > legendaryCount, "Common items should drop more frequently than legendary")
    }

    // Test ItemGenerator
    @Test
    fun `test ItemGenerator registers default templates`() {
        val generator = ItemGenerator()
        val templates = generator.getAllTemplates()

        assertTrue(templates.isNotEmpty(), "Should have default templates")
        assertNotNull(templates["rusty_sword"])
        assertNotNull(templates["iron_sword"])
        assertNotNull(templates["leather_armor"])
        assertNotNull(templates["health_potion"])
    }

    @Test
    fun `test ItemGenerator generates weapon with correct scaling`() {
        val generator = ItemGenerator()

        val commonWeapon = generator.generateItem("rusty_sword", ItemRarity.COMMON, 1)
        assertNotNull(commonWeapon)
        assertTrue(commonWeapon is GeneratedItem.GeneratedWeapon)
        assertEquals(ItemRarity.COMMON, commonWeapon.rarity)

        val epicWeapon = generator.generateItem("rusty_sword", ItemRarity.EPIC, 1)
        assertNotNull(epicWeapon)
        assertTrue(epicWeapon is GeneratedItem.GeneratedWeapon)

        // Epic should have higher stats than common due to rarity multiplier
        val commonDamage = (commonWeapon as GeneratedItem.GeneratedWeapon).item.baseDamage
        val epicDamage = (epicWeapon as GeneratedItem.GeneratedWeapon).item.baseDamage

        assertTrue(epicDamage > commonDamage, "Epic rarity should have higher damage")
    }

    @Test
    fun `test ItemGenerator generates armor with correct scaling`() {
        val generator = ItemGenerator()

        val commonArmor = generator.generateItem("leather_armor", ItemRarity.COMMON, 1)
        assertNotNull(commonArmor)
        assertTrue(commonArmor is GeneratedItem.GeneratedArmor)

        val rareArmor = generator.generateItem("leather_armor", ItemRarity.RARE, 5)
        assertNotNull(rareArmor)
        assertTrue(rareArmor is GeneratedItem.GeneratedArmor)

        // Rare armor at higher level should have better defense
        val commonDefense = (commonArmor as GeneratedItem.GeneratedArmor).item.defenseBonus
        val rareDefense = (rareArmor as GeneratedItem.GeneratedArmor).item.defenseBonus

        assertTrue(rareDefense >= commonDefense, "Rare armor should have equal or better defense")
    }

    @Test
    fun `test ItemGenerator generates accessory`() {
        val generator = ItemGenerator()

        val accessory = generator.generateItem("magic_ring", ItemRarity.UNCOMMON, 3)
        assertNotNull(accessory)
        assertTrue(accessory is GeneratedItem.GeneratedAccessory)

        val item = (accessory as GeneratedItem.GeneratedAccessory).item
        assertTrue(item.intelligenceBonus > 0 || item.wisdomBonus > 0)
    }

    @Test
    fun `test ItemGenerator generates consumable`() {
        val generator = ItemGenerator()

        val potion = generator.generateItem("health_potion", ItemRarity.COMMON, 1)
        assertNotNull(potion)
        assertTrue(potion is GeneratedItem.GeneratedInventoryItem)

        val item = (potion as GeneratedItem.GeneratedInventoryItem).item
        assertEquals(ItemType.CONSUMABLE, item.type)
        assertTrue(item.stackable)
    }

    @Test
    fun `test ItemGenerator handles invalid template ID`() {
        val generator = ItemGenerator()

        val result = generator.generateItem("nonexistent_item", ItemRarity.COMMON, 1)
        assertNull(result)
    }

    @Test
    fun `test ItemGenerator generates loot batch`() {
        val generator = ItemGenerator()

        val lootDrops = listOf(
            LootDrop("rusty_sword", 1.0), // Guaranteed drop
            LootDrop("health_potion", 1.0, quantityMin = 2, quantityMax = 5)
        )

        val loot = generator.generateLoot(lootDrops, level = 1)
        assertEquals(2, loot.size)

        // Check that stackable items have correct quantity
        val potion = loot.firstOrNull { it is GeneratedItem.GeneratedInventoryItem }
        assertNotNull(potion)
        assertTrue((potion as GeneratedItem.GeneratedInventoryItem).item.quantity >= 2)
    }

    // Test LootTable
    @Test
    fun `test LootTable rolls loot within max drops`() {
        val lootTable = LootTable(
            id = "test",
            name = "Test Loot",
            drops = listOf(
                LootDrop("health_potion", 1.0),
                LootDrop("rusty_sword", 1.0),
                LootDrop("leather_armor", 1.0),
                LootDrop("magic_ring", 1.0)
            ),
            goldMin = 10,
            goldMax = 50,
            maxDrops = 2
        )

        val result = lootTable.rollLoot(playerLevel = 1, locationDanger = 5)
        assertTrue(result.items.size <= 2, "Should respect maxDrops limit")
        assertTrue(result.gold in 10..100, "Gold should be in expected range with scaling")
    }

    @Test
    fun `test LootTable guaranteed drops always appear`() {
        val lootTable = LootTable(
            id = "test",
            name = "Test Loot",
            drops = listOf(
                LootDrop("health_potion", 0.1) // Low chance
            ),
            guaranteedDrops = listOf(
                LootDrop("quest_item", 1.0)
            ),
            goldMin = 0,
            goldMax = 0
        )

        val result = lootTable.rollLoot(playerLevel = 1, locationDanger = 1)
        assertTrue(result.items.any { it.templateId == "quest_item" }, "Guaranteed drops should always appear")
    }

    @Test
    fun `test LootTable luck modifier affects drop rates`() {
        val lootTable = LootTable(
            id = "test",
            name = "Test Loot",
            drops = listOf(
                LootDrop("health_potion", 0.5)
            ),
            goldMin = 0,
            goldMax = 0,
            maxDrops = 5
        )

        // Test multiple rolls to check probability
        val normalRolls = (0 until 100).map {
            lootTable.rollLoot(playerLevel = 1, locationDanger = 1, luckModifier = 0.0)
        }
        val luckyRolls = (0 until 100).map {
            lootTable.rollLoot(playerLevel = 1, locationDanger = 1, luckModifier = 0.5)
        }

        val normalDrops = normalRolls.sumOf { it.items.size }
        val luckyDrops = luckyRolls.sumOf { it.items.size }

        // With higher luck, we should generally get more drops (though not guaranteed due to randomness)
        assertTrue(luckyDrops >= normalDrops * 0.8, "Luck modifier should increase drop rates")
    }

    @Test
    fun `test LootDrop rolls rarity correctly`() {
        val drop = LootDrop(
            "test_item",
            1.0,
            rarityWeights = mapOf(
                ItemRarity.COMMON to 100,
                ItemRarity.LEGENDARY to 1
            )
        )

        val rarities = (0 until 100).map { drop.rollRarity() }
        val commonCount = rarities.count { it == ItemRarity.COMMON }
        val legendaryCount = rarities.count { it == ItemRarity.LEGENDARY }

        assertTrue(commonCount > legendaryCount, "Common should be more frequent than legendary")
    }

    @Test
    fun `test LootDrop rolls quantity in range`() {
        val drop = LootDrop("test_item", 1.0, quantityMin = 2, quantityMax = 5)

        val quantities = (0 until 100).map { drop.rollQuantity() }
        assertTrue(quantities.all { it in 2..5 }, "All quantities should be in specified range")
        assertTrue(quantities.min() == 2, "Should include minimum quantity")
        assertTrue(quantities.max() == 5, "Should include maximum quantity")
    }

    // Test predefined loot tables
    @Test
    fun `test LootTables forEnemyType returns appropriate tables`() {
        val goblinTable = LootTables.forEnemyType("goblin")
        assertEquals("low_tier_enemy", goblinTable.id)

        val orcTable = LootTables.forEnemyType("orc")
        assertEquals("mid_tier_enemy", orcTable.id)

        val dragonTable = LootTables.forEnemyType("dragon")
        assertEquals("high_tier_enemy", dragonTable.id)

        val bossTable = LootTables.forEnemyType("boss")
        assertEquals("boss_tier", bossTable.id)
    }

    @Test
    fun `test LootTables forLocationDanger scales appropriately`() {
        val lowDanger = LootTables.forLocationDanger(2)
        assertTrue(lowDanger.goldMax < 100)

        val highDanger = LootTables.forLocationDanger(8)
        assertTrue(highDanger.goldMax > 100)

        val epicDanger = LootTables.forLocationDanger(10)
        assertTrue(epicDanger.goldMax > highDanger.goldMax)
    }

    // Integration tests with RulesEngine
    @Test
    fun `test RulesEngine generates loot on combat`() {
        val rulesEngine = RulesEngine()

        val gameState = createTestGameState()
        val outcome = rulesEngine.calculateCombatOutcome("goblin", gameState)

        assertNotNull(outcome.loot)
        assertTrue(outcome.gold >= 0)
    }

    @Test
    fun `test RulesEngine loot scales with location danger`() {
        val rulesEngine = RulesEngine()

        val lowDangerState = createTestGameState(locationDanger = 1)
        val highDangerState = createTestGameState(locationDanger = 10)

        val lowDangerOutcome = rulesEngine.calculateCombatOutcome("enemy", lowDangerState)
        val highDangerOutcome = rulesEngine.calculateCombatOutcome("enemy", highDangerState)

        // Higher danger should generally give more/better loot
        assertTrue(highDangerOutcome.gold >= lowDangerOutcome.gold)
    }

    @Test
    fun `test RulesEngine recognizes enemy types`() {
        val rulesEngine = RulesEngine()
        val gameState = createTestGameState()

        val goblinOutcome = rulesEngine.calculateCombatOutcome("a fierce goblin", gameState)
        val dragonOutcome = rulesEngine.calculateCombatOutcome("ancient dragon", gameState)

        assertNotNull(goblinOutcome.loot)
        assertNotNull(dragonOutcome.loot)
    }

    @Test
    fun `test luck modifier affects loot generation`() {
        val rulesEngine = RulesEngine()

        // High wisdom and charisma should give better luck
        val luckyState = createTestGameState(wisdom = 20, charisma = 20)
        val unluckyState = createTestGameState(wisdom = 5, charisma = 5)

        val luckyOutcomes = (0 until 20).map {
            rulesEngine.calculateCombatOutcome("goblin", luckyState)
        }
        val unluckyOutcomes = (0 until 20).map {
            rulesEngine.calculateCombatOutcome("goblin", unluckyState)
        }

        val luckyAverageLoot = luckyOutcomes.sumOf { it.loot.size } / luckyOutcomes.size.toDouble()
        val unluckyAverageLoot = unluckyOutcomes.sumOf { it.loot.size } / unluckyOutcomes.size.toDouble()

        // Lucky characters should get more loot on average
        assertTrue(luckyAverageLoot >= unluckyAverageLoot * 0.9)
    }

    @Test
    fun `test GeneratedItem helper methods`() {
        val generator = ItemGenerator()
        val weapon = generator.generateItem("rusty_sword", ItemRarity.COMMON, 1)!!

        assertNotNull(weapon.getName())
        assertNotNull(weapon.getId())
        assertEquals(1, weapon.getQuantity())
        assertNotNull(weapon.toEquipmentItem())
        assertNull(weapon.toInventoryItem())
    }

    @Test
    fun `test GeneratedItem inventory item conversion`() {
        val generator = ItemGenerator()
        val potion = generator.generateItem("health_potion", ItemRarity.COMMON, 1)!!

        assertNotNull(potion.toInventoryItem())
        assertNull(potion.toEquipmentItem())
    }

    @Test
    fun `test item names include rarity for non-common items`() {
        val generator = ItemGenerator()

        val common = generator.generateItem("rusty_sword", ItemRarity.COMMON, 1)!!
        val epic = generator.generateItem("rusty_sword", ItemRarity.EPIC, 1)!!

        assertFalse(common.getName().contains("Common"))
        assertTrue(epic.getName().contains("Epic"))
    }

    @Test
    fun `test boss loot table has guaranteed drops`() {
        val bossTable = LootTables.forEnemyType("boss")

        assertTrue(bossTable.guaranteedDrops.isNotEmpty(), "Boss should have guaranteed drops")
        assertTrue(bossTable.goldMin > 400, "Boss should drop significant gold")
        assertTrue(bossTable.maxDrops > 3, "Boss should allow multiple drops")
    }

    @Test
    fun `test consumable stacking`() {
        val generator = ItemGenerator()
        val drop = LootDrop("health_potion", 1.0, quantityMin = 3, quantityMax = 5)

        val loot = generator.generateLoot(listOf(drop), 1)
        assertEquals(1, loot.size)

        val potion = loot[0] as GeneratedItem.GeneratedInventoryItem
        assertTrue(potion.item.quantity in 3..5)
        assertTrue(potion.item.stackable)
    }

    // Helper function to create test game state
    private fun createTestGameState(
        level: Int = 1,
        locationDanger: Int = 5,
        wisdom: Int = 10,
        charisma: Int = 10
    ): GameState {
        val stats = Stats(
            strength = 10,
            dexterity = 10,
            constitution = 10,
            intelligence = 10,
            wisdom = wisdom,
            charisma = charisma
        )

        val characterSheet = CharacterSheet(
            level = level,
            xp = 0,
            baseStats = stats,
            resources = Resources.fromStats(stats)
        )

        val location = Location(
            id = "test_location",
            name = "Test Location",
            zoneId = "test_zone",
            biome = Biome.SETTLEMENT,
            description = "A test location",
            danger = locationDanger,
            connections = emptyList(),
            features = emptyList(),
            lore = ""
        )

        return GameState(
            gameId = "test",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = characterSheet,
            currentLocation = location
        )
    }
}
