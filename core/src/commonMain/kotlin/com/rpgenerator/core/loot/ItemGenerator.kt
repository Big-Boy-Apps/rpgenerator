package com.rpgenerator.core.loot

import com.rpgenerator.core.domain.*
import kotlinx.serialization.Serializable

/**
 * Generates items from templates with level and rarity scaling.
 */
internal class ItemGenerator {

    private val templates = mutableMapOf<String, ItemTemplate>()

    init {
        registerDefaultTemplates()
    }

    /**
     * Register an item template for generation.
     */
    fun registerTemplate(template: ItemTemplate) {
        templates[template.baseId] = template
    }

    /**
     * Generate an item from a template.
     * @param templateId The template identifier
     * @param rarity Item rarity tier
     * @param level Player/area level for scaling
     * @return Generated item or null if template not found
     */
    fun generateItem(
        templateId: String,
        rarity: ItemRarity,
        level: Int
    ): GeneratedItem? {
        val template = templates[templateId] ?: return null

        return when (val item = template.generate(rarity, level)) {
            is Weapon -> GeneratedItem.GeneratedWeapon(item, rarity)
            is Armor -> GeneratedItem.GeneratedArmor(item, rarity)
            is Accessory -> GeneratedItem.GeneratedAccessory(item, rarity)
            is InventoryItem -> GeneratedItem.GeneratedInventoryItem(item, rarity)
            else -> null
        }
    }

    /**
     * Generate multiple items from loot drops.
     */
    fun generateLoot(
        lootDrops: List<LootDrop>,
        level: Int
    ): List<GeneratedItem> {
        return lootDrops.mapNotNull { drop ->
            val rarity = drop.rollRarity()
            val quantity = drop.rollQuantity()

            generateItem(drop.templateId, rarity, level)?.let { item ->
                // Adjust quantity for stackable items
                if (item is GeneratedItem.GeneratedInventoryItem && item.item.stackable) {
                    GeneratedItem.GeneratedInventoryItem(
                        item.item.copy(quantity = quantity),
                        item.rarity
                    )
                } else {
                    item
                }
            }
        }
    }

    /**
     * Get a template by ID.
     */
    fun getTemplate(templateId: String): ItemTemplate? = templates[templateId]

    /**
     * Get all registered templates.
     */
    fun getAllTemplates(): Map<String, ItemTemplate> = templates.toMap()

    /**
     * Register default item templates.
     */
    private fun registerDefaultTemplates() {
        // Weapons
        registerTemplate(WeaponTemplate(
            baseId = "rusty_sword",
            baseName = "Rusty Sword",
            category = "Weapon",
            description = "A worn blade, better than nothing.",
            minLevel = 1,
            baseDamageMin = 3,
            baseDamageMax = 8,
            strengthBonusMin = 0,
            strengthBonusMax = 1,
            dexterityBonusMin = 0,
            dexterityBonusMax = 1
        ))

        registerTemplate(WeaponTemplate(
            baseId = "iron_sword",
            baseName = "Iron Sword",
            category = "Weapon",
            description = "A sturdy iron blade for reliable combat.",
            minLevel = 5,
            baseDamageMin = 8,
            baseDamageMax = 15,
            strengthBonusMin = 1,
            strengthBonusMax = 3,
            dexterityBonusMin = 0,
            dexterityBonusMax = 2
        ))

        registerTemplate(WeaponTemplate(
            baseId = "steel_sword",
            baseName = "Steel Sword",
            category = "Weapon",
            description = "A well-crafted weapon of tempered steel.",
            minLevel = 10,
            baseDamageMin = 15,
            baseDamageMax = 25,
            strengthBonusMin = 2,
            strengthBonusMax = 5,
            dexterityBonusMin = 1,
            dexterityBonusMax = 3
        ))

        registerTemplate(WeaponTemplate(
            baseId = "legendary_weapon",
            baseName = "Legendary Blade",
            category = "Weapon",
            description = "A weapon of exceptional power, forged by masters.",
            minLevel = 15,
            baseDamageMin = 25,
            baseDamageMax = 40,
            strengthBonusMin = 4,
            strengthBonusMax = 8,
            dexterityBonusMin = 2,
            dexterityBonusMax = 5
        ))

        // Armor
        registerTemplate(ArmorTemplate(
            baseId = "leather_armor",
            baseName = "Leather Armor",
            category = "Armor",
            description = "Basic protection from tanned hides.",
            minLevel = 1,
            baseDefenseMin = 2,
            baseDefenseMax = 5,
            constitutionBonusMin = 0,
            constitutionBonusMax = 1
        ))

        registerTemplate(ArmorTemplate(
            baseId = "chainmail_armor",
            baseName = "Chainmail Armor",
            category = "Armor",
            description = "Interlocking metal rings provide solid defense.",
            minLevel = 5,
            baseDefenseMin = 5,
            baseDefenseMax = 10,
            constitutionBonusMin = 1,
            constitutionBonusMax = 3
        ))

        registerTemplate(ArmorTemplate(
            baseId = "plate_armor",
            baseName = "Plate Armor",
            category = "Armor",
            description = "Heavy plates of metal offer superior protection.",
            minLevel = 10,
            baseDefenseMin = 10,
            baseDefenseMax = 18,
            constitutionBonusMin = 2,
            constitutionBonusMax = 5
        ))

        registerTemplate(ArmorTemplate(
            baseId = "legendary_armor",
            baseName = "Legendary Armor",
            category = "Armor",
            description = "Armor of legends, nearly impenetrable.",
            minLevel = 15,
            baseDefenseMin = 18,
            baseDefenseMax = 30,
            constitutionBonusMin = 4,
            constitutionBonusMax = 8
        ))

        // Accessories
        registerTemplate(AccessoryTemplate(
            baseId = "magic_ring",
            baseName = "Magic Ring",
            category = "Accessory",
            description = "A ring imbued with mystical energy.",
            minLevel = 3,
            intelligenceBonusMin = 1,
            intelligenceBonusMax = 3,
            wisdomBonusMin = 1,
            wisdomBonusMax = 3
        ))

        registerTemplate(AccessoryTemplate(
            baseId = "enchanted_amulet",
            baseName = "Enchanted Amulet",
            category = "Accessory",
            description = "An amulet pulsing with arcane power.",
            minLevel = 8,
            intelligenceBonusMin = 2,
            intelligenceBonusMax = 5,
            wisdomBonusMin = 2,
            wisdomBonusMax = 5
        ))

        registerTemplate(AccessoryTemplate(
            baseId = "artifact_accessory",
            baseName = "Ancient Artifact",
            category = "Accessory",
            description = "A relic of immense magical power from a bygone age.",
            minLevel = 12,
            intelligenceBonusMin = 4,
            intelligenceBonusMax = 8,
            wisdomBonusMin = 4,
            wisdomBonusMax = 8
        ))

        // Consumables
        registerTemplate(ConsumableTemplate(
            baseId = "health_potion",
            baseName = "Health Potion",
            category = "Consumable",
            description = "Restores health when consumed.",
            effectType = ConsumableEffect.HEAL_HP
        ))

        registerTemplate(ConsumableTemplate(
            baseId = "mana_potion",
            baseName = "Mana Potion",
            category = "Consumable",
            description = "Restores mana when consumed.",
            effectType = ConsumableEffect.RESTORE_MANA
        ))

        registerTemplate(ConsumableTemplate(
            baseId = "greater_health_potion",
            baseName = "Greater Health Potion",
            category = "Consumable",
            description = "Restores a large amount of health.",
            minLevel = 5,
            effectType = ConsumableEffect.HEAL_HP
        ))

        registerTemplate(ConsumableTemplate(
            baseId = "greater_mana_potion",
            baseName = "Greater Mana Potion",
            category = "Consumable",
            description = "Restores a large amount of mana.",
            minLevel = 5,
            effectType = ConsumableEffect.RESTORE_MANA
        ))

        // Quest and misc items
        registerTemplate(QuestItemTemplate(
            baseId = "boss_trophy",
            baseName = "Boss Trophy",
            category = "Quest",
            description = "A trophy proving victory over a powerful foe."
        ))

        registerTemplate(ConsumableTemplate(
            baseId = "crafting_material",
            baseName = "Crafting Material",
            category = "Material",
            description = "Basic materials for crafting.",
            effectType = ConsumableEffect.CURE_STATUS
        ))
    }
}

/**
 * Wrapper for generated items with rarity information.
 */
@Serializable
internal sealed class GeneratedItem {
    abstract val rarity: ItemRarity

    @Serializable
    internal data class GeneratedWeapon(
        val item: Weapon,
        override val rarity: ItemRarity
    ) : GeneratedItem()

    @Serializable
    internal data class GeneratedArmor(
        val item: Armor,
        override val rarity: ItemRarity
    ) : GeneratedItem()

    @Serializable
    internal data class GeneratedAccessory(
        val item: Accessory,
        override val rarity: ItemRarity
    ) : GeneratedItem()

    @Serializable
    internal data class GeneratedInventoryItem(
        val item: InventoryItem,
        override val rarity: ItemRarity
    ) : GeneratedItem()

    /**
     * Get the item name for display.
     */
    internal fun getName(): String = when (this) {
        is GeneratedWeapon -> item.name
        is GeneratedArmor -> item.name
        is GeneratedAccessory -> item.name
        is GeneratedInventoryItem -> item.name
    }

    /**
     * Get the item ID.
     */
    internal fun getId(): String = when (this) {
        is GeneratedWeapon -> item.id
        is GeneratedArmor -> item.id
        is GeneratedAccessory -> item.id
        is GeneratedInventoryItem -> item.id
    }

    /**
     * Get quantity (1 for equipment, actual quantity for stackable items).
     */
    internal fun getQuantity(): Int = when (this) {
        is GeneratedWeapon -> 1
        is GeneratedArmor -> 1
        is GeneratedAccessory -> 1
        is GeneratedInventoryItem -> item.quantity
    }

    /**
     * Convert to appropriate domain object for adding to character.
     */
    internal fun toInventoryItem(): InventoryItem? = when (this) {
        is GeneratedInventoryItem -> item
        // Equipment items don't go into inventory as InventoryItem
        // They need to be handled separately
        else -> null
    }

    /**
     * Convert to equipment item if applicable.
     */
    internal fun toEquipmentItem(): EquipmentItem? = when (this) {
        is GeneratedWeapon -> item
        is GeneratedArmor -> item
        is GeneratedAccessory -> item
        else -> null
    }
}
