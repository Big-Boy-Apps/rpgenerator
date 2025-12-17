package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Represents a Non-Player Character with memory, personality, and shop functionality.
 */
@Serializable
internal data class NPC(
    val id: String,
    val name: String,
    val archetype: NPCArchetype,
    val locationId: String,
    val personality: NPCPersonality,
    val conversationHistory: List<ConversationEntry> = emptyList(),
    val relationships: Map<String, Relationship> = emptyMap(), // gameId -> relationship
    val shop: Shop? = null,
    val questIds: List<String> = emptyList(), // IDs of quests this NPC can give/progress
    val lore: String = "",
    val greetingContext: String = "" // Additional context for first greeting
) {
    /**
     * Add a conversation entry to the NPC's memory.
     */
    fun addConversation(playerInput: String, npcResponse: String, playerLevel: Int): NPC {
        val entry = ConversationEntry(
            playerInput = playerInput,
            npcResponse = npcResponse,
            playerLevel = playerLevel,
            timestamp = currentTimeMillis()
        )
        return copy(conversationHistory = conversationHistory + entry)
    }

    /**
     * Update relationship with the player.
     */
    fun updateRelationship(gameId: String, change: Int): NPC {
        val current = relationships[gameId] ?: Relationship(gameId, 0)
        val updated = current.copy(affinity = (current.affinity + change).coerceIn(-100, 100))
        return copy(relationships = relationships + (gameId to updated))
    }

    /**
     * Get relationship level with player.
     */
    fun getRelationship(gameId: String): Relationship {
        return relationships[gameId] ?: Relationship(gameId, 0)
    }

    /**
     * Get recent conversation history (last N entries).
     */
    fun getRecentConversations(limit: Int = 5): List<ConversationEntry> {
        return conversationHistory.takeLast(limit)
    }
}

@Serializable
internal enum class NPCArchetype {
    MERCHANT,
    QUEST_GIVER,
    GUARD,
    INNKEEPER,
    BLACKSMITH,
    ALCHEMIST,
    TRAINER,
    VILLAGER,
    NOBLE,
    SCHOLAR,
    WANDERER
}

@Serializable
internal data class NPCPersonality(
    val traits: List<String>, // e.g., "gruff", "friendly", "mysterious", "greedy"
    val speechPattern: String, // e.g., "speaks in riddles", "very direct", "overly formal"
    val motivations: List<String> // e.g., "wants to protect the village", "seeks profit"
)

@Serializable
internal data class ConversationEntry(
    val playerInput: String,
    val npcResponse: String,
    val playerLevel: Int,
    val timestamp: Long
)

@Serializable
internal data class Relationship(
    val gameId: String,
    val affinity: Int // -100 (hostile) to 100 (trusted ally)
) {
    fun getStatus(): RelationshipStatus {
        return when {
            affinity >= 75 -> RelationshipStatus.TRUSTED_ALLY
            affinity >= 40 -> RelationshipStatus.FRIENDLY
            affinity >= 10 -> RelationshipStatus.ACQUAINTANCE
            affinity >= -10 -> RelationshipStatus.NEUTRAL
            affinity >= -40 -> RelationshipStatus.UNFRIENDLY
            affinity >= -75 -> RelationshipStatus.HOSTILE
            else -> RelationshipStatus.ENEMY
        }
    }
}

@Serializable
internal enum class RelationshipStatus {
    TRUSTED_ALLY,
    FRIENDLY,
    ACQUAINTANCE,
    NEUTRAL,
    UNFRIENDLY,
    HOSTILE,
    ENEMY
}

/**
 * Shop system for merchant NPCs.
 */
@Serializable
internal data class Shop(
    val name: String,
    val inventory: List<ShopItem>,
    val currency: String = "gold",
    val buybackPercentage: Int = 50 // Percentage of value when buying from player
) {
    fun hasItem(itemId: String): Boolean {
        return inventory.any { it.id == itemId && it.stock > 0 }
    }

    fun getItem(itemId: String): ShopItem? {
        return inventory.find { it.id == itemId }
    }

    fun purchaseItem(itemId: String, quantity: Int): Pair<Shop, ShopItem?> {
        val item = inventory.find { it.id == itemId } ?: return Pair(this, null)

        if (item.stock < quantity) {
            return Pair(this, null)
        }

        val updatedInventory = inventory.map { shopItem ->
            if (shopItem.id == itemId) {
                shopItem.copy(stock = shopItem.stock - quantity)
            } else {
                shopItem
            }
        }

        return Pair(copy(inventory = updatedInventory), item)
    }

    fun sellItem(itemId: String, quantity: Int): Shop {
        val existingItem = inventory.find { it.id == itemId }

        val updatedInventory = if (existingItem != null) {
            inventory.map { shopItem ->
                if (shopItem.id == itemId) {
                    shopItem.copy(stock = shopItem.stock + quantity)
                } else {
                    shopItem
                }
            }
        } else {
            inventory // For now, don't add new items to shop inventory
        }

        return copy(inventory = updatedInventory)
    }
}

@Serializable
internal data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int, // -1 for unlimited
    val requiredLevel: Int = 1,
    val requiredRelationship: Int = 0, // Minimum affinity required to purchase
    val itemData: ShopItemData
)

@Serializable
internal sealed class ShopItemData {
    @Serializable
    data class WeaponData(val weapon: Weapon) : ShopItemData()

    @Serializable
    data class ArmorData(val armor: Armor) : ShopItemData()

    @Serializable
    data class AccessoryData(val accessory: Accessory) : ShopItemData()

    @Serializable
    data class ConsumableData(val item: InventoryItem) : ShopItemData()
}
