package com.rpgenerator.core.tools

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.events.EventCategory
import com.rpgenerator.core.events.EventMetadata
import com.rpgenerator.core.orchestration.Intent
import kotlinx.serialization.Serializable

internal interface GameTools {

    fun getPlayerStatus(state: GameState): PlayerStatusResult

    fun getCurrentLocation(state: GameState): LocationDetails

    fun searchEventLog(
        state: GameState,
        query: String? = null,
        categories: List<EventCategory>? = null,
        npcId: String? = null,
        locationId: String? = null,
        questId: String? = null,
        limit: Int = 20
    ): List<EventMetadata>

    fun analyzeIntent(
        input: String,
        state: GameState,
        recentEvents: List<GameEvent>
    ): IntentAnalysis

    fun resolveCombat(
        target: String,
        state: GameState
    ): CombatResolution

    fun validateAction(
        intent: Intent,
        target: String?,
        state: GameState
    ): ActionValidation

    suspend fun generateLocation(
        parentLocation: Location,
        discoveryContext: String,
        state: GameState
    ): Location?

    fun getConnectedLocations(
        state: GameState
    ): List<Location>

    // Character sheet tools
    fun getCharacterSheet(state: GameState): CharacterSheetDetails

    fun getEffectiveStats(state: GameState): Stats

    fun equipItem(
        itemId: String,
        state: GameState
    ): EquipmentResult

    fun useItem(
        itemId: String,
        quantity: Int = 1,
        state: GameState
    ): ItemUseResult

    fun getInventory(state: GameState): InventoryDetails

    // NPC tools
    fun getNPCsAtLocation(state: GameState): List<NPCInfo>

    fun findNPCByName(name: String, state: GameState): NPCDetails?

    fun getNPCShop(npcId: String, state: GameState): ShopDetails?

    fun purchaseFromShop(
        npcId: String,
        itemId: String,
        quantity: Int,
        state: GameState
    ): ShopTransactionResult

    fun sellToShop(
        npcId: String,
        inventoryItemId: String,
        quantity: Int,
        state: GameState
    ): ShopTransactionResult

    fun getEventSummary(
        state: GameState,
        maxEvents: Int = 50
    ): EventSummaryResult

    // NPC generation tool
    suspend fun generateNPC(
        name: String,
        role: String,
        locationId: String,
        personalityTraits: List<String>,
        backstory: String,
        motivations: List<String>,
        relationshipToPlayer: String = "neutral"
    ): NPCGenerationResult

    // Quest tools
    fun getActiveQuests(state: GameState): List<QuestInfo>

    fun getQuestDetails(questId: String, state: GameState): QuestDetails?

    suspend fun generateQuest(
        state: GameState,
        questType: String? = null,
        context: String? = null
    ): Quest?

    fun checkQuestProgress(state: GameState): List<QuestProgressUpdate>

    fun getToolDefinitions(): List<ToolDefinition>
}

@Serializable
internal data class PlayerStatusResult(
    val level: Int,
    val xp: Long,
    val xpToNextLevel: Long,
    val locationName: String,
    val locationDanger: Int
)

@Serializable
internal data class LocationDetails(
    val id: String,
    val name: String,
    val description: String,
    val danger: Int,
    val features: List<String>,
    val lore: String,
    val connectedLocationNames: List<String>
)

@Serializable
internal data class IntentAnalysis(
    val intent: Intent,
    val target: String? = null,
    val reasoning: String,
    val shouldGenerateLocation: Boolean = false,
    val locationGenerationContext: String? = null
)

@Serializable
internal data class CombatResolution(
    val success: Boolean,
    val damage: Int,
    val xpGained: Long,
    val levelUp: Boolean,
    val newLevel: Int,
    val targetDefeated: Boolean,
    val loot: List<com.rpgenerator.core.loot.GeneratedItem> = emptyList(),
    val gold: Int = 0
)

@Serializable
internal data class ActionValidation(
    val valid: Boolean,
    val reason: String? = null
)

@Serializable
internal data class CharacterSheetDetails(
    val level: Int,
    val xp: Long,
    val xpToNextLevel: Long,
    val baseStats: Stats,
    val effectiveStats: Stats,
    val currentHP: Int,
    val maxHP: Int,
    val currentMana: Int,
    val maxMana: Int,
    val currentEnergy: Int,
    val maxEnergy: Int,
    val skills: List<SkillInfo>,
    val equipment: EquipmentInfo,
    val statusEffects: List<StatusEffectInfo>
)

@Serializable
internal data class SkillInfo(
    val id: String,
    val name: String,
    val description: String,
    val manaCost: Int,
    val energyCost: Int,
    val level: Int
)

@Serializable
internal data class EquipmentInfo(
    val weapon: String?,
    val armor: String?,
    val accessory: String?
)

@Serializable
internal data class StatusEffectInfo(
    val name: String,
    val description: String,
    val turnsRemaining: Int
)

@Serializable
internal data class EquipmentResult(
    val success: Boolean,
    val message: String,
    val equipped: String?
)

@Serializable
internal data class ItemUseResult(
    val success: Boolean,
    val message: String,
    val effect: String?
)

@Serializable
internal data class InventoryDetails(
    val items: List<InventoryItemInfo>,
    val usedSlots: Int,
    val maxSlots: Int
)

@Serializable
internal data class InventoryItemInfo(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val quantity: Int
)

// NPC-related data classes
@Serializable
internal data class NPCInfo(
    val id: String,
    val name: String,
    val archetype: String,
    val hasShop: Boolean,
    val hasQuests: Boolean,
    val relationshipStatus: String
)

@Serializable
internal data class NPCDetails(
    val id: String,
    val name: String,
    val archetype: String,
    val personality: String,
    val hasShop: Boolean,
    val hasQuests: Boolean,
    val relationship: RelationshipInfo,
    val recentConversations: List<String>
)

@Serializable
internal data class RelationshipInfo(
    val affinity: Int,
    val status: String
)

@Serializable
internal data class ShopDetails(
    val shopName: String,
    val npcName: String,
    val items: List<ShopItemInfo>,
    val currency: String
)

@Serializable
internal data class ShopItemInfo(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int,
    val requiredLevel: Int,
    val requiredRelationship: Int
)

@Serializable
internal data class ShopTransactionResult(
    val success: Boolean,
    val message: String,
    val goldChange: Int = 0,
    val itemReceived: String? = null
)

@Serializable
internal data class NPCGenerationResult(
    val success: Boolean,
    val npc: NPC?,
    val message: String
)

@Serializable
internal data class EventSummaryResult(
    val totalEvents: Long,
    val categoryCounts: Map<String, Long>,
    val recentHighlights: List<EventHighlight>,
    val summary: String
)

@Serializable
internal data class EventHighlight(
    val category: String,
    val importance: String,
    val text: String,
    val timestamp: Long
)

// Quest-related data classes
@Serializable
internal data class QuestInfo(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val completionPercentage: Int
)

@Serializable
internal data class QuestDetails(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val status: String,
    val objectives: List<QuestObjectiveInfo>,
    val rewards: QuestRewardInfo,
    val canComplete: Boolean
)

@Serializable
internal data class QuestObjectiveInfo(
    val description: String,
    val currentProgress: Int,
    val targetProgress: Int,
    val isComplete: Boolean
)

@Serializable
internal data class QuestRewardInfo(
    val xp: Long,
    val items: List<String>,
    val gold: Int,
    val unlocksLocations: List<String>
)

@Serializable
internal data class QuestProgressUpdate(
    val questId: String,
    val questName: String,
    val objectiveUpdated: String,
    val progress: String
)
