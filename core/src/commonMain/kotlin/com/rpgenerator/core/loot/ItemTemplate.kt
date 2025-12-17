package com.rpgenerator.core.loot

import com.rpgenerator.core.domain.*
import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Template for generating items with randomized stats.
 */
@Serializable
internal sealed class ItemTemplate {
    abstract val baseId: String
    abstract val baseName: String
    abstract val category: String
    abstract val description: String
    abstract val minLevel: Int
    abstract val stackable: Boolean

    /**
     * Generate a concrete item from this template
     */
    internal abstract fun generate(rarity: ItemRarity, level: Int): Any
}

@Serializable
internal data class WeaponTemplate(
    override val baseId: String,
    override val baseName: String,
    override val category: String,
    override val description: String,
    override val minLevel: Int = 1,
    override val stackable: Boolean = false,
    val baseDamageMin: Int,
    val baseDamageMax: Int,
    val strengthBonusMin: Int = 0,
    val strengthBonusMax: Int = 2,
    val dexterityBonusMin: Int = 0,
    val dexterityBonusMax: Int = 2
) : ItemTemplate() {
    override fun generate(rarity: ItemRarity, level: Int): Weapon {
        val scaledDamage = (baseDamageMin + (level / 3)) * rarity.statMultiplier
        val damage = scaledDamage.toInt()

        val strBonus = ((strengthBonusMin..strengthBonusMax).random() * rarity.statMultiplier).toInt()
        val dexBonus = ((dexterityBonusMin..dexterityBonusMax).random() * rarity.statMultiplier).toInt()

        val itemName = if (rarity != ItemRarity.COMMON) {
            "${rarity.displayName} $baseName"
        } else {
            baseName
        }

        return Weapon(
            id = "$baseId-${rarity.name.lowercase()}-${currentTimeMillis()}",
            name = itemName,
            description = description,
            requiredLevel = minLevel,
            baseDamage = damage,
            strengthBonus = strBonus,
            dexterityBonus = dexBonus
        )
    }
}

@Serializable
internal data class ArmorTemplate(
    override val baseId: String,
    override val baseName: String,
    override val category: String,
    override val description: String,
    override val minLevel: Int = 1,
    override val stackable: Boolean = false,
    val baseDefenseMin: Int,
    val baseDefenseMax: Int,
    val constitutionBonusMin: Int = 0,
    val constitutionBonusMax: Int = 2
) : ItemTemplate() {
    override fun generate(rarity: ItemRarity, level: Int): Armor {
        val scaledDefense = (baseDefenseMin + (level / 3)) * rarity.statMultiplier
        val defense = scaledDefense.toInt()

        val conBonus = ((constitutionBonusMin..constitutionBonusMax).random() * rarity.statMultiplier).toInt()

        val itemName = if (rarity != ItemRarity.COMMON) {
            "${rarity.displayName} $baseName"
        } else {
            baseName
        }

        return Armor(
            id = "$baseId-${rarity.name.lowercase()}-${currentTimeMillis()}",
            name = itemName,
            description = description,
            requiredLevel = minLevel,
            defenseBonus = defense,
            constitutionBonus = conBonus
        )
    }
}

@Serializable
internal data class AccessoryTemplate(
    override val baseId: String,
    override val baseName: String,
    override val category: String,
    override val description: String,
    override val minLevel: Int = 1,
    override val stackable: Boolean = false,
    val intelligenceBonusMin: Int = 0,
    val intelligenceBonusMax: Int = 3,
    val wisdomBonusMin: Int = 0,
    val wisdomBonusMax: Int = 3
) : ItemTemplate() {
    override fun generate(rarity: ItemRarity, level: Int): Accessory {
        val intBonus = ((intelligenceBonusMin..intelligenceBonusMax).random() * rarity.statMultiplier).toInt()
        val wisBonus = ((wisdomBonusMin..wisdomBonusMax).random() * rarity.statMultiplier).toInt()

        val itemName = if (rarity != ItemRarity.COMMON) {
            "${rarity.displayName} $baseName"
        } else {
            baseName
        }

        return Accessory(
            id = "$baseId-${rarity.name.lowercase()}-${currentTimeMillis()}",
            name = itemName,
            description = description,
            requiredLevel = minLevel,
            intelligenceBonus = intBonus,
            wisdomBonus = wisBonus
        )
    }
}

@Serializable
internal data class ConsumableTemplate(
    override val baseId: String,
    override val baseName: String,
    override val category: String,
    override val description: String,
    override val minLevel: Int = 1,
    override val stackable: Boolean = true,
    val effectType: ConsumableEffect
) : ItemTemplate() {
    override fun generate(rarity: ItemRarity, level: Int): InventoryItem {
        val itemName = if (rarity != ItemRarity.COMMON) {
            "${rarity.displayName} $baseName"
        } else {
            baseName
        }

        return InventoryItem(
            id = "$baseId-${rarity.name.lowercase()}",
            name = itemName,
            description = description,
            type = ItemType.CONSUMABLE,
            quantity = 1,
            stackable = stackable
        )
    }
}

@Serializable
internal data class QuestItemTemplate(
    override val baseId: String,
    override val baseName: String,
    override val category: String,
    override val description: String,
    override val minLevel: Int = 1,
    override val stackable: Boolean = false
) : ItemTemplate() {
    override fun generate(rarity: ItemRarity, level: Int): InventoryItem {
        return InventoryItem(
            id = baseId,
            name = baseName,
            description = description,
            type = ItemType.QUEST_ITEM,
            quantity = 1,
            stackable = stackable
        )
    }
}

@Serializable
enum class ConsumableEffect {
    HEAL_HP,
    RESTORE_MANA,
    RESTORE_ENERGY,
    STAT_BOOST,
    CURE_STATUS
}
