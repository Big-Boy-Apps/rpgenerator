package com.rpgenerator.core.loot

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Defines what items can drop and their probabilities.
 */
@Serializable
internal data class LootTable(
    val id: String,
    val name: String,
    val drops: List<LootDrop>,
    val guaranteedDrops: List<LootDrop> = emptyList(),
    val goldMin: Int = 0,
    val goldMax: Int = 0,
    val maxDrops: Int = 3
) {
    /**
     * Roll for loot drops based on player level and location danger.
     * @param playerLevel Current player level
     * @param locationDanger Location danger rating (1-10)
     * @param luckModifier Additional luck modifier (0.0 to 1.0+)
     * @return List of generated loot
     */
    fun rollLoot(
        playerLevel: Int,
        locationDanger: Int,
        luckModifier: Double = 0.0
    ): LootResult {
        val results = mutableListOf<LootDrop>()

        // Always add guaranteed drops
        results.addAll(guaranteedDrops)

        // Roll for random drops
        var dropsAdded = 0
        for (drop in drops) {
            if (dropsAdded >= maxDrops) break

            val adjustedChance = drop.dropChance * (1.0 + luckModifier)
            val roll = Random.nextDouble()

            if (roll <= adjustedChance) {
                results.add(drop)
                dropsAdded++
            }
        }

        // Generate gold (scales with level and danger)
        val goldAmount = if (goldMin > 0 || goldMax > 0) {
            val baseGold = Random.nextInt(goldMin, goldMax + 1)
            val scaledGold = (baseGold * (1.0 + (locationDanger / 10.0) + (playerLevel / 20.0))).toInt()
            scaledGold
        } else {
            0
        }

        return LootResult(
            items = results,
            gold = goldAmount
        )
    }
}

/**
 * Individual loot drop entry with template and drop chance.
 */
@Serializable
internal data class LootDrop(
    val templateId: String,
    val dropChance: Double, // 0.0 to 1.0
    val quantityMin: Int = 1,
    val quantityMax: Int = 1,
    val rarityWeights: Map<ItemRarity, Int> = defaultRarityWeights()
) {
    companion object {
        private fun defaultRarityWeights(): Map<ItemRarity, Int> = mapOf(
            ItemRarity.COMMON to 50,
            ItemRarity.UNCOMMON to 25,
            ItemRarity.RARE to 15,
            ItemRarity.EPIC to 8,
            ItemRarity.LEGENDARY to 2
        )
    }

    /**
     * Roll for item rarity based on weights.
     */
    fun rollRarity(): ItemRarity {
        val totalWeight = rarityWeights.values.sum()
        val roll = Random.nextInt(totalWeight)
        var accumulated = 0

        for ((rarity, weight) in rarityWeights) {
            accumulated += weight
            if (roll < accumulated) {
                return rarity
            }
        }

        return ItemRarity.COMMON
    }

    /**
     * Roll for quantity.
     */
    fun rollQuantity(): Int = Random.nextInt(quantityMin, quantityMax + 1)
}

/**
 * Result of a loot roll.
 */
@Serializable
internal data class LootResult(
    val items: List<LootDrop>,
    val gold: Int
)

/**
 * Predefined loot tables for common enemy types.
 */
internal object LootTables {

    fun forEnemyType(enemyType: String): LootTable {
        return when (enemyType.lowercase()) {
            "goblin", "kobold" -> lowTierEnemy()
            "orc", "troll", "ogre" -> midTierEnemy()
            "dragon", "demon", "lich" -> highTierEnemy()
            "boss" -> bossTier()
            else -> defaultEnemy()
        }
    }

    fun forLocationDanger(danger: Int): LootTable {
        return when {
            danger <= 2 -> lowTierLocation()
            danger <= 5 -> midTierLocation()
            danger <= 8 -> highTierLocation()
            else -> epicLocation()
        }
    }

    private fun lowTierEnemy() = LootTable(
        id = "low_tier_enemy",
        name = "Low Tier Enemy Loot",
        drops = listOf(
            LootDrop("health_potion", 0.3, 1, 2),
            LootDrop("rusty_sword", 0.15),
            LootDrop("leather_armor", 0.15),
            LootDrop("crafting_material", 0.4, 1, 3)
        ),
        goldMin = 5,
        goldMax = 20,
        maxDrops = 2
    )

    private fun midTierEnemy() = LootTable(
        id = "mid_tier_enemy",
        name = "Mid Tier Enemy Loot",
        drops = listOf(
            LootDrop("health_potion", 0.4, 1, 3),
            LootDrop("mana_potion", 0.3, 1, 2),
            LootDrop("iron_sword", 0.25, rarityWeights = mapOf(
                ItemRarity.COMMON to 40,
                ItemRarity.UNCOMMON to 30,
                ItemRarity.RARE to 20,
                ItemRarity.EPIC to 8,
                ItemRarity.LEGENDARY to 2
            )),
            LootDrop("chainmail_armor", 0.25, rarityWeights = mapOf(
                ItemRarity.COMMON to 40,
                ItemRarity.UNCOMMON to 30,
                ItemRarity.RARE to 20,
                ItemRarity.EPIC to 8,
                ItemRarity.LEGENDARY to 2
            )),
            LootDrop("magic_ring", 0.15)
        ),
        goldMin = 30,
        goldMax = 80,
        maxDrops = 3
    )

    private fun highTierEnemy() = LootTable(
        id = "high_tier_enemy",
        name = "High Tier Enemy Loot",
        drops = listOf(
            LootDrop("greater_health_potion", 0.5, 2, 4),
            LootDrop("greater_mana_potion", 0.4, 2, 3),
            LootDrop("steel_sword", 0.35, rarityWeights = mapOf(
                ItemRarity.UNCOMMON to 30,
                ItemRarity.RARE to 35,
                ItemRarity.EPIC to 25,
                ItemRarity.LEGENDARY to 10
            )),
            LootDrop("plate_armor", 0.35, rarityWeights = mapOf(
                ItemRarity.UNCOMMON to 30,
                ItemRarity.RARE to 35,
                ItemRarity.EPIC to 25,
                ItemRarity.LEGENDARY to 10
            )),
            LootDrop("enchanted_amulet", 0.25, rarityWeights = mapOf(
                ItemRarity.RARE to 40,
                ItemRarity.EPIC to 40,
                ItemRarity.LEGENDARY to 20
            ))
        ),
        goldMin = 100,
        goldMax = 300,
        maxDrops = 4
    )

    private fun bossTier() = LootTable(
        id = "boss_tier",
        name = "Boss Loot",
        drops = listOf(
            LootDrop("legendary_weapon", 0.8, rarityWeights = mapOf(
                ItemRarity.EPIC to 50,
                ItemRarity.LEGENDARY to 50
            )),
            LootDrop("legendary_armor", 0.7, rarityWeights = mapOf(
                ItemRarity.EPIC to 50,
                ItemRarity.LEGENDARY to 50
            )),
            LootDrop("artifact_accessory", 0.6, rarityWeights = mapOf(
                ItemRarity.EPIC to 40,
                ItemRarity.LEGENDARY to 60
            ))
        ),
        guaranteedDrops = listOf(
            LootDrop("boss_trophy", 1.0)
        ),
        goldMin = 500,
        goldMax = 1500,
        maxDrops = 5
    )

    private fun defaultEnemy() = LootTable(
        id = "default_enemy",
        name = "Default Enemy Loot",
        drops = listOf(
            LootDrop("health_potion", 0.2),
            LootDrop("crafting_material", 0.3, 1, 2)
        ),
        goldMin = 10,
        goldMax = 30,
        maxDrops = 2
    )

    private fun lowTierLocation() = LootTable(
        id = "low_tier_location",
        name = "Low Tier Location Treasure",
        drops = listOf(
            LootDrop("health_potion", 0.5, 1, 3),
            LootDrop("rusty_sword", 0.2),
            LootDrop("leather_armor", 0.2)
        ),
        goldMin = 20,
        goldMax = 50
    )

    private fun midTierLocation() = LootTable(
        id = "mid_tier_location",
        name = "Mid Tier Location Treasure",
        drops = listOf(
            LootDrop("health_potion", 0.6, 2, 4),
            LootDrop("mana_potion", 0.5, 1, 3),
            LootDrop("iron_sword", 0.3),
            LootDrop("chainmail_armor", 0.3),
            LootDrop("magic_ring", 0.2)
        ),
        goldMin = 50,
        goldMax = 150
    )

    private fun highTierLocation() = LootTable(
        id = "high_tier_location",
        name = "High Tier Location Treasure",
        drops = listOf(
            LootDrop("greater_health_potion", 0.7, 3, 5),
            LootDrop("greater_mana_potion", 0.6, 2, 4),
            LootDrop("steel_sword", 0.4, rarityWeights = mapOf(
                ItemRarity.RARE to 50,
                ItemRarity.EPIC to 35,
                ItemRarity.LEGENDARY to 15
            )),
            LootDrop("plate_armor", 0.4, rarityWeights = mapOf(
                ItemRarity.RARE to 50,
                ItemRarity.EPIC to 35,
                ItemRarity.LEGENDARY to 15
            ))
        ),
        goldMin = 200,
        goldMax = 500
    )

    private fun epicLocation() = LootTable(
        id = "epic_location",
        name = "Epic Location Treasure",
        drops = listOf(
            LootDrop("legendary_weapon", 0.6, rarityWeights = mapOf(
                ItemRarity.EPIC to 60,
                ItemRarity.LEGENDARY to 40
            )),
            LootDrop("legendary_armor", 0.5, rarityWeights = mapOf(
                ItemRarity.EPIC to 60,
                ItemRarity.LEGENDARY to 40
            ))
        ),
        goldMin = 500,
        goldMax = 2000
    )
}
