package com.rpgenerator.core.domain

/**
 * Provides pre-built NPC templates for common archetypes.
 * These can be used to quickly populate game locations with interesting NPCs.
 */
internal object NPCTemplates {

    /**
     * Create a merchant NPC with a shop.
     */
    fun createMerchant(
        id: String,
        name: String,
        locationId: String,
        shopInventory: List<ShopItem> = defaultMerchantInventory()
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.MERCHANT,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("shrewd", "greedy", "observant"),
                speechPattern = "speaks in prices and values",
                motivations = listOf("maximize profit", "find rare items", "build reputation")
            ),
            shop = Shop(
                name = "$name's Shop",
                inventory = shopInventory,
                currency = "gold",
                buybackPercentage = 50
            ),
            lore = "$name has been a merchant in these lands for many years, trading in weapons, armor, and rare goods.",
            greetingContext = "Always willing to make a deal with adventurers who have coin to spend."
        )
    }

    /**
     * Create a quest giver NPC.
     */
    fun createQuestGiver(
        id: String,
        name: String,
        locationId: String,
        questIds: List<String> = emptyList()
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.QUEST_GIVER,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("helpful", "worried", "knowledgeable"),
                speechPattern = "speaks with urgency about local troubles",
                motivations = listOf("protect the community", "solve mysteries", "recruit help")
            ),
            questIds = questIds,
            lore = "$name is well-respected in the community and often asks travelers for assistance with local problems.",
            greetingContext = "Looking for brave adventurers to help with important tasks."
        )
    }

    /**
     * Create a guard NPC.
     */
    fun createGuard(
        id: String,
        name: String,
        locationId: String
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.GUARD,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("stern", "dutiful", "vigilant"),
                speechPattern = "speaks in short, authoritative sentences",
                motivations = listOf("maintain order", "protect citizens", "enforce laws")
            ),
            lore = "$name is a guard sworn to protect this place and its people.",
            greetingContext = "Keeps a watchful eye on all who pass through."
        )
    }

    /**
     * Create an innkeeper NPC.
     */
    fun createInnkeeper(
        id: String,
        name: String,
        locationId: String
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.INNKEEPER,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("friendly", "gossipy", "welcoming"),
                speechPattern = "speaks warmly, always offering food and drink",
                motivations = listOf("run a successful inn", "hear the latest news", "help travelers")
            ),
            shop = Shop(
                name = "$name's Inn",
                inventory = defaultInnInventory(),
                currency = "gold",
                buybackPercentage = 30
            ),
            lore = "$name runs the local inn, a gathering place for travelers and locals alike.",
            greetingContext = "Always ready with a warm meal and a friendly ear for travelers' tales."
        )
    }

    /**
     * Create a blacksmith NPC.
     */
    fun createBlacksmith(
        id: String,
        name: String,
        locationId: String
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.BLACKSMITH,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("gruff", "skilled", "proud"),
                speechPattern = "speaks bluntly about craftsmanship",
                motivations = listOf("craft the finest weapons", "maintain forge", "train apprentices")
            ),
            shop = Shop(
                name = "$name's Forge",
                inventory = defaultBlacksmithInventory(),
                currency = "gold",
                buybackPercentage = 60
            ),
            lore = "$name is a master blacksmith whose weapons and armor are known throughout the region.",
            greetingContext = "Takes pride in every piece that comes from the forge."
        )
    }

    /**
     * Create an alchemist NPC.
     */
    fun createAlchemist(
        id: String,
        name: String,
        locationId: String
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.ALCHEMIST,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("eccentric", "brilliant", "secretive"),
                speechPattern = "speaks in technical terms about potions and reagents",
                motivations = listOf("discover new formulas", "perfect existing potions", "gather rare ingredients")
            ),
            shop = Shop(
                name = "$name's Alchemical Supplies",
                inventory = defaultAlchemistInventory(),
                currency = "gold",
                buybackPercentage = 40
            ),
            lore = "$name is an alchemist of considerable skill, brewing potions and elixirs for those who can afford them.",
            greetingContext = "Surrounded by bubbling flasks and mysterious ingredients."
        )
    }

    /**
     * Create a scholar NPC.
     */
    fun createScholar(
        id: String,
        name: String,
        locationId: String,
        specialization: String = "ancient history"
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.SCHOLAR,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("knowledgeable", "absent-minded", "curious"),
                speechPattern = "speaks in lengthy, detailed explanations",
                motivations = listOf("preserve knowledge", "discover lost lore", "educate others")
            ),
            lore = "$name is a scholar specializing in $specialization, with vast knowledge of the world's mysteries.",
            greetingContext = "Always eager to share knowledge with those who seek it."
        )
    }

    /**
     * Create a wanderer/mysterious traveler NPC.
     */
    fun createWanderer(
        id: String,
        name: String,
        locationId: String
    ): NPC {
        return NPC(
            id = id,
            name = name,
            archetype = NPCArchetype.WANDERER,
            locationId = locationId,
            personality = NPCPersonality(
                traits = listOf("mysterious", "worldly", "cryptic"),
                speechPattern = "speaks in riddles and hints",
                motivations = listOf("seek adventure", "follow destiny", "uncover secrets")
            ),
            lore = "$name is a wanderer who has traveled far and wide, carrying many secrets and stories.",
            greetingContext = "A mysterious figure whose true purpose remains unclear."
        )
    }

    // Default shop inventories

    private fun defaultMerchantInventory(): List<ShopItem> {
        return listOf(
            ShopItem(
                id = "health_potion_s",
                name = "Health Potion",
                description = "Restores 50 HP",
                price = 25,
                stock = 10,
                itemData = ShopItemData.ConsumableData(
                    InventoryItem(
                        id = "health_potion_s",
                        name = "Health Potion",
                        description = "Restores 50 HP",
                        type = ItemType.CONSUMABLE
                    )
                )
            ),
            ShopItem(
                id = "mana_potion_s",
                name = "Mana Potion",
                description = "Restores 30 Mana",
                price = 20,
                stock = 10,
                itemData = ShopItemData.ConsumableData(
                    InventoryItem(
                        id = "mana_potion_s",
                        name = "Mana Potion",
                        description = "Restores 30 Mana",
                        type = ItemType.CONSUMABLE
                    )
                )
            ),
            ShopItem(
                id = "iron_sword",
                name = "Iron Sword",
                description = "A reliable weapon for any warrior",
                price = 100,
                stock = 3,
                requiredLevel = 1,
                itemData = ShopItemData.WeaponData(
                    Weapon(
                        id = "iron_sword",
                        name = "Iron Sword",
                        description = "A reliable weapon for any warrior",
                        baseDamage = 15,
                        strengthBonus = 2
                    )
                )
            )
        )
    }

    private fun defaultInnInventory(): List<ShopItem> {
        return listOf(
            ShopItem(
                id = "bread",
                name = "Fresh Bread",
                description = "Restores 10 HP",
                price = 5,
                stock = -1, // Unlimited
                itemData = ShopItemData.ConsumableData(
                    InventoryItem(
                        id = "bread",
                        name = "Fresh Bread",
                        description = "Restores 10 HP",
                        type = ItemType.CONSUMABLE
                    )
                )
            ),
            ShopItem(
                id = "ale",
                name = "Ale",
                description = "A refreshing drink",
                price = 3,
                stock = -1,
                itemData = ShopItemData.ConsumableData(
                    InventoryItem(
                        id = "ale",
                        name = "Ale",
                        description = "A refreshing drink",
                        type = ItemType.CONSUMABLE
                    )
                )
            )
        )
    }

    private fun defaultBlacksmithInventory(): List<ShopItem> {
        return listOf(
            ShopItem(
                id = "steel_sword",
                name = "Steel Sword",
                description = "A well-crafted blade",
                price = 250,
                stock = 2,
                requiredLevel = 5,
                itemData = ShopItemData.WeaponData(
                    Weapon(
                        id = "steel_sword",
                        name = "Steel Sword",
                        description = "A well-crafted blade",
                        baseDamage = 25,
                        strengthBonus = 4
                    )
                )
            ),
            ShopItem(
                id = "steel_armor",
                name = "Steel Armor",
                description = "Sturdy protection",
                price = 300,
                stock = 2,
                requiredLevel = 5,
                itemData = ShopItemData.ArmorData(
                    Armor(
                        id = "steel_armor",
                        name = "Steel Armor",
                        description = "Sturdy protection",
                        defenseBonus = 15,
                        constitutionBonus = 3
                    )
                )
            )
        )
    }

    private fun defaultAlchemistInventory(): List<ShopItem> {
        return listOf(
            ShopItem(
                id = "greater_health_potion",
                name = "Greater Health Potion",
                description = "Restores 100 HP",
                price = 50,
                stock = 5,
                requiredLevel = 3,
                itemData = ShopItemData.ConsumableData(
                    InventoryItem(
                        id = "greater_health_potion",
                        name = "Greater Health Potion",
                        description = "Restores 100 HP",
                        type = ItemType.CONSUMABLE
                    )
                )
            ),
            ShopItem(
                id = "elixir_of_strength",
                name = "Elixir of Strength",
                description = "Temporarily increases Strength by 5",
                price = 75,
                stock = 3,
                requiredLevel = 5,
                itemData = ShopItemData.ConsumableData(
                    InventoryItem(
                        id = "elixir_of_strength",
                        name = "Elixir of Strength",
                        description = "Temporarily increases Strength by 5",
                        type = ItemType.CONSUMABLE
                    )
                )
            )
        )
    }
}
