package com.rpgenerator.core.tools

import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.QuestGeneratorAgent
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.orchestration.Intent
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.util.currentTimeMillis

internal class GameToolsImpl(
    private val locationManager: LocationManager,
    private val rulesEngine: RulesEngine,
    private val locationGenerator: LocationGeneratorAgent,
    private val questGenerator: QuestGeneratorAgent,
    private val eventLog: MutableList<GameEvent> = mutableListOf()
) : GameTools {

    override fun getPlayerStatus(state: GameState): PlayerStatusResult {
        return PlayerStatusResult(
            level = state.playerLevel,
            xp = state.playerXP,
            xpToNextLevel = (state.playerLevel + 1) * 100L,
            locationName = state.currentLocation.name,
            locationDanger = state.currentLocation.danger
        )
    }

    override fun getCurrentLocation(state: GameState): LocationDetails {
        // Get connected locations using current location directly
        val connected = state.currentLocation.connections.mapNotNull { connectionId ->
            locationManager.getLocation(connectionId, state)
        }

        return LocationDetails(
            id = state.currentLocation.id,
            name = state.currentLocation.name,
            description = state.currentLocation.description,
            danger = state.currentLocation.danger,
            features = state.currentLocation.features,
            lore = state.currentLocation.lore,
            connectedLocationNames = connected.map { it.name }
        )
    }

    override fun searchEventLog(
        state: GameState,
        query: String?,
        categories: List<com.rpgenerator.core.events.EventCategory>?,
        npcId: String?,
        locationId: String?,
        questId: String?,
        limit: Int
    ): List<com.rpgenerator.core.events.EventMetadata> {
        // Simple in-memory search - convert GameEvents to EventMetadata
        val filtered = eventLog.filter { event ->
            val metadata = com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, state.gameId)

            // Filter by query
            val matchesQuery = query == null || metadata.searchableText.contains(query, ignoreCase = true)

            // Filter by categories
            val matchesCategory = categories == null || categories.contains(metadata.category)

            // Filter by NPC
            val matchesNpc = npcId == null || metadata.npcId == npcId

            // Filter by location
            val matchesLocation = locationId == null || metadata.locationId == locationId

            // Filter by quest
            val matchesQuest = questId == null || metadata.questId == questId

            matchesQuery && matchesCategory && matchesNpc && matchesLocation && matchesQuest
        }

        return filtered.takeLast(limit).map { event ->
            com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, state.gameId)
        }
    }

    override fun getEventSummary(state: GameState, maxEvents: Int): com.rpgenerator.core.tools.EventSummaryResult {
        val recentEvents = eventLog.takeLast(maxEvents).map { event ->
            com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, state.gameId)
        }

        val categoryCounts = recentEvents.groupBy { it.category.name }
            .mapValues { it.value.size.toLong() }

        val highlights = recentEvents
            .filter { it.importance != com.rpgenerator.core.events.EventImportance.LOW }
            .takeLast(5)
            .map { metadata ->
                com.rpgenerator.core.tools.EventHighlight(
                    category = metadata.category.name,
                    importance = metadata.importance.name,
                    text = metadata.searchableText,
                    timestamp = metadata.timestamp
                )
            }

        val summary = "Recent activity: ${recentEvents.size} events across ${categoryCounts.size} categories"

        return com.rpgenerator.core.tools.EventSummaryResult(
            totalEvents = eventLog.size.toLong(),
            categoryCounts = categoryCounts,
            recentHighlights = highlights,
            summary = summary
        )
    }

    override fun analyzeIntent(
        input: String,
        state: GameState,
        recentEvents: List<GameEvent>
    ): IntentAnalysis {
        val lowerInput = input.lowercase()

        return when {
            lowerInput.contains("attack") ||
            lowerInput.contains("fight") ||
            lowerInput.contains("kill") ||
            lowerInput.contains("strike") -> {
                val target = extractTarget(input) ?: "unknown enemy"
                IntentAnalysis(
                    intent = Intent.COMBAT,
                    target = target,
                    reasoning = "Player initiated combat action"
                )
            }

            lowerInput.contains("talk") ||
            lowerInput.contains("speak") ||
            lowerInput.contains("ask") ||
            lowerInput.contains("tell") ||
            lowerInput.contains("thank") ||
            lowerInput.contains("greet") -> {
                val target = extractTarget(input) ?: "unknown npc"
                IntentAnalysis(
                    intent = Intent.NPC_DIALOGUE,
                    target = target,
                    reasoning = "Player attempting dialogue"
                )
            }

            lowerInput.contains("stats") ||
            lowerInput.contains("status") ||
            lowerInput.contains("inventory") ||
            lowerInput.contains("level") -> {
                IntentAnalysis(
                    intent = Intent.SYSTEM_QUERY,
                    reasoning = "Player checking character status"
                )
            }

            lowerInput.contains("quest") -> {
                IntentAnalysis(
                    intent = Intent.QUEST_ACTION,
                    reasoning = "Player interacting with quest system"
                )
            }

            lowerInput.contains("class") ||
            lowerInput.contains("choose class") ||
            lowerInput.contains("select class") ||
            lowerInput.contains("pick class") ||
            lowerInput.contains("warrior") ||
            lowerInput.contains("mage") ||
            lowerInput.contains("rogue") ||
            lowerInput.contains("ranger") ||
            lowerInput.contains("cultivator") -> {
                // Extract the specific class if mentioned
                val chosenClass = when {
                    lowerInput.contains("warrior") -> "warrior"
                    lowerInput.contains("mage") -> "mage"
                    lowerInput.contains("rogue") -> "rogue"
                    lowerInput.contains("ranger") -> "ranger"
                    lowerInput.contains("cultivator") -> "cultivator"
                    else -> null
                }
                IntentAnalysis(
                    intent = Intent.CLASS_SELECTION,
                    target = chosenClass,
                    reasoning = "Player selecting or inquiring about class"
                )
            }

            // Skill-related intents
            lowerInput.contains("skill") && (lowerInput.contains("list") || lowerInput.contains("show") || lowerInput.contains("view") || lowerInput.contains("menu")) ||
            lowerInput.contains("skills") ||
            lowerInput.contains("abilities") -> {
                IntentAnalysis(
                    intent = Intent.SKILL_MENU,
                    reasoning = "Player viewing skills"
                )
            }

            lowerInput.contains("use ") || lowerInput.contains("cast ") || lowerInput.contains("activate ") -> {
                // Extract skill name from input
                val skillName = extractSkillName(input)
                IntentAnalysis(
                    intent = Intent.USE_SKILL,
                    target = skillName,
                    reasoning = "Player using a skill"
                )
            }

            lowerInput.contains("evolve") && lowerInput.contains("skill") ||
            lowerInput.contains("evolution") -> {
                val skillName = extractSkillName(input)
                IntentAnalysis(
                    intent = Intent.SKILL_EVOLUTION,
                    target = skillName,
                    reasoning = "Player evolving a skill"
                )
            }

            lowerInput.contains("fuse") || lowerInput.contains("fusion") || lowerInput.contains("combine skill") -> {
                IntentAnalysis(
                    intent = Intent.SKILL_FUSION,
                    reasoning = "Player fusing skills"
                )
            }

            // Menu intents - more specific than SYSTEM_QUERY
            lowerInput.contains("status") && lowerInput.contains("full") ||
            lowerInput.contains("character sheet") ||
            lowerInput.contains("detailed status") -> {
                IntentAnalysis(
                    intent = Intent.STATUS_MENU,
                    reasoning = "Player viewing detailed status"
                )
            }

            lowerInput.contains("inventory") && (lowerInput.contains("show") || lowerInput.contains("list") || lowerInput.contains("open")) ||
            lowerInput.contains("bag") ||
            lowerInput.contains("items") -> {
                IntentAnalysis(
                    intent = Intent.INVENTORY_MENU,
                    reasoning = "Player viewing inventory"
                )
            }

            else -> {
                val shouldDiscover = lowerInput.contains("search") ||
                                   lowerInput.contains("explore") ||
                                   lowerInput.contains("investigate")

                IntentAnalysis(
                    intent = Intent.EXPLORATION,
                    reasoning = "Player exploring or moving",
                    shouldGenerateLocation = shouldDiscover,
                    locationGenerationContext = if (shouldDiscover) input else null
                )
            }
        }
    }

    override fun resolveCombat(target: String, state: GameState): CombatResolution {
        val outcome = rulesEngine.calculateCombatOutcome(target, state)

        return CombatResolution(
            success = true,
            damage = outcome.damage,
            xpGained = outcome.xpGain,
            levelUp = outcome.levelUp,
            newLevel = outcome.newLevel,
            targetDefeated = true,
            loot = outcome.loot,
            gold = outcome.gold
        )
    }

    override fun validateAction(
        intent: Intent,
        target: String?,
        state: GameState
    ): ActionValidation {
        return when (intent) {
            Intent.COMBAT -> {
                if (target == null) {
                    ActionValidation(false, "No combat target specified")
                } else if (state.currentLocation.danger > state.playerLevel + 5) {
                    ActionValidation(false, "Location far too dangerous for player level")
                } else {
                    ActionValidation(true)
                }
            }
            else -> ActionValidation(true)
        }
    }

    override suspend fun generateLocation(
        parentLocation: Location,
        discoveryContext: String,
        state: GameState
    ): Location? {
        return locationGenerator.generateLocation(parentLocation, discoveryContext, state)
    }

    override fun getConnectedLocations(state: GameState): List<Location> {
        // Use current location directly to get its connections, rather than looking it up by ID
        // This ensures we use the latest version of the location including any runtime updates
        return state.currentLocation.connections.mapNotNull { connectionId ->
            locationManager.getLocation(connectionId, state)
        }
    }

    override fun getCharacterSheet(state: GameState): CharacterSheetDetails {
        val sheet = state.characterSheet
        val effective = sheet.effectiveStats()

        return CharacterSheetDetails(
            level = sheet.level,
            xp = sheet.xp,
            xpToNextLevel = sheet.xpToNextLevel(),
            baseStats = sheet.baseStats,
            effectiveStats = effective,
            currentHP = sheet.resources.currentHP,
            maxHP = sheet.resources.maxHP,
            currentMana = sheet.resources.currentMana,
            maxMana = sheet.resources.maxMana,
            currentEnergy = sheet.resources.currentEnergy,
            maxEnergy = sheet.resources.maxEnergy,
            skills = sheet.skills.map { skill ->
                SkillInfo(
                    id = skill.id,
                    name = skill.name,
                    description = skill.description,
                    manaCost = skill.manaCost,
                    energyCost = skill.energyCost,
                    level = skill.level
                )
            },
            equipment = EquipmentInfo(
                weapon = sheet.equipment.weapon?.name,
                armor = sheet.equipment.armor?.name,
                accessory = sheet.equipment.accessory?.name
            ),
            statusEffects = sheet.statusEffects.map { effect ->
                StatusEffectInfo(
                    name = effect.name,
                    description = effect.description,
                    turnsRemaining = effect.duration
                )
            }
        )
    }

    override fun getEffectiveStats(state: GameState): Stats {
        return state.characterSheet.effectiveStats()
    }

    override fun equipItem(itemId: String, state: GameState): EquipmentResult {
        val item = state.characterSheet.inventory.items[itemId]

        if (item == null) {
            return EquipmentResult(
                success = false,
                message = "Item not found in inventory",
                equipped = null
            )
        }

        // For POC, we'll need to expand this to convert inventory items to equipment
        // For now, just return not implemented
        return EquipmentResult(
            success = false,
            message = "Equipment system not yet fully implemented",
            equipped = null
        )
    }

    override fun useItem(itemId: String, quantity: Int, state: GameState): ItemUseResult {
        if (!state.characterSheet.inventory.hasItem(itemId, quantity)) {
            return ItemUseResult(
                success = false,
                message = "Insufficient quantity of item",
                effect = null
            )
        }

        val item = state.characterSheet.inventory.items[itemId]
            ?: return ItemUseResult(
                success = false,
                message = "Item not found",
                effect = null
            )

        // For POC, basic consumable logic
        if (item.type == ItemType.CONSUMABLE) {
            return ItemUseResult(
                success = true,
                message = "Used ${item.name}",
                effect = "Item use effects not yet implemented"
            )
        }

        return ItemUseResult(
            success = false,
            message = "Item cannot be used",
            effect = null
        )
    }

    override fun getInventory(state: GameState): InventoryDetails {
        val inventory = state.characterSheet.inventory

        return InventoryDetails(
            items = inventory.items.values.map { item ->
                InventoryItemInfo(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    type = item.type.name,
                    quantity = item.quantity
                )
            },
            usedSlots = inventory.items.size,
            maxSlots = inventory.maxSlots
        )
    }

    override fun getNPCsAtLocation(state: GameState): List<NPCInfo> {
        return state.getNPCsAtCurrentLocation().map { npc ->
            val relationship = npc.getRelationship(state.gameId)
            NPCInfo(
                id = npc.id,
                name = npc.name,
                archetype = npc.archetype.name,
                hasShop = npc.shop != null,
                hasQuests = npc.questIds.isNotEmpty(),
                relationshipStatus = relationship.getStatus().name
            )
        }
    }

    override fun findNPCByName(name: String, state: GameState): NPCDetails? {
        val npc = state.findNPCByName(name) ?: return null
        val relationship = npc.getRelationship(state.gameId)

        return NPCDetails(
            id = npc.id,
            name = npc.name,
            archetype = npc.archetype.name,
            personality = "${npc.personality.traits.joinToString(", ")} - ${npc.personality.speechPattern}",
            hasShop = npc.shop != null,
            hasQuests = npc.questIds.isNotEmpty(),
            relationship = RelationshipInfo(
                affinity = relationship.affinity,
                status = relationship.getStatus().name
            ),
            recentConversations = npc.getRecentConversations(3).map { entry ->
                "Player: ${entry.playerInput}\n${npc.name}: ${entry.npcResponse}"
            }
        )
    }

    override fun getNPCShop(npcId: String, state: GameState): ShopDetails? {
        val npc = state.findNPC(npcId) ?: return null
        val shop = npc.shop ?: return null

        return ShopDetails(
            shopName = shop.name,
            npcName = npc.name,
            items = shop.inventory.map { item ->
                ShopItemInfo(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    price = item.price,
                    stock = item.stock,
                    requiredLevel = item.requiredLevel,
                    requiredRelationship = item.requiredRelationship
                )
            },
            currency = shop.currency
        )
    }

    override fun purchaseFromShop(
        npcId: String,
        itemId: String,
        quantity: Int,
        state: GameState
    ): ShopTransactionResult {
        val npc = state.findNPC(npcId)
            ?: return ShopTransactionResult(false, "NPC not found")

        val shop = npc.shop
            ?: return ShopTransactionResult(false, "This NPC doesn't have a shop")

        val item = shop.getItem(itemId)
            ?: return ShopTransactionResult(false, "Item not found in shop")

        // Check level requirement
        if (item.requiredLevel > state.playerLevel) {
            return ShopTransactionResult(
                false,
                "You need to be level ${item.requiredLevel} to purchase this item"
            )
        }

        // Check relationship requirement
        val relationship = npc.getRelationship(state.gameId)
        if (relationship.affinity < item.requiredRelationship) {
            return ShopTransactionResult(
                false,
                "You need a better relationship with ${npc.name} to purchase this item"
            )
        }

        // Check stock
        if (item.stock >= 0 && item.stock < quantity) {
            return ShopTransactionResult(
                false,
                "Only ${item.stock} in stock"
            )
        }

        // For now, we'll return success without actually implementing gold/currency system
        // This would need to be extended when currency is added to CharacterSheet
        val totalCost = item.price * quantity

        return ShopTransactionResult(
            success = true,
            message = "Successfully purchased ${item.name} x$quantity for $totalCost ${shop.currency}",
            goldChange = -totalCost,
            itemReceived = item.name
        )
    }

    override fun sellToShop(
        npcId: String,
        inventoryItemId: String,
        quantity: Int,
        state: GameState
    ): ShopTransactionResult {
        val npc = state.findNPC(npcId)
            ?: return ShopTransactionResult(false, "NPC not found")

        val shop = npc.shop
            ?: return ShopTransactionResult(false, "This NPC doesn't have a shop")

        val playerItem = state.characterSheet.inventory.items[inventoryItemId]
            ?: return ShopTransactionResult(false, "You don't have this item")

        if (playerItem.quantity < quantity) {
            return ShopTransactionResult(
                false,
                "You only have ${playerItem.quantity} of this item"
            )
        }

        // Calculate sell value (typically a percentage of shop price)
        // For now, use a default value since we don't have shop pricing for player items
        val sellValue = 10 * quantity // Placeholder value

        return ShopTransactionResult(
            success = true,
            message = "Sold ${playerItem.name} x$quantity for $sellValue ${shop.currency}",
            goldChange = sellValue
        )
    }

    override fun getToolDefinitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_player_status",
                description = "Get current player level, XP, and location info",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_current_location",
                description = "Get detailed information about the current location",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "search_event_log",
                description = "Search through past game events for context",
                parameters = mapOf(
                    "query" to ParameterDefinition(
                        type = "string",
                        description = "Search term to filter events",
                        required = false
                    ),
                    "limit" to ParameterDefinition(
                        type = "integer",
                        description = "Maximum number of events to return",
                        required = false
                    )
                )
            ),
            ToolDefinition(
                name = "analyze_intent",
                description = "Determine what the player is trying to do",
                parameters = mapOf(
                    "input" to ParameterDefinition(
                        type = "string",
                        description = "Player's input text",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "resolve_combat",
                description = "Calculate combat outcome",
                parameters = mapOf(
                    "target" to ParameterDefinition(
                        type = "string",
                        description = "Enemy being attacked",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "get_character_sheet",
                description = "Get complete character sheet including stats, equipment, skills, and status effects",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_effective_stats",
                description = "Get character stats including all bonuses from equipment and status effects",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_inventory",
                description = "Get player's inventory with all items",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "equip_item",
                description = "Equip an item from inventory",
                parameters = mapOf(
                    "itemId" to ParameterDefinition(
                        type = "string",
                        description = "ID of the item to equip",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "use_item",
                description = "Use a consumable item from inventory",
                parameters = mapOf(
                    "itemId" to ParameterDefinition(
                        type = "string",
                        description = "ID of the item to use",
                        required = true
                    ),
                    "quantity" to ParameterDefinition(
                        type = "integer",
                        description = "Quantity to use",
                        required = false
                    )
                )
            )
        )
    }

    // Quest tool implementations
    override fun getActiveQuests(state: GameState): List<QuestInfo> {
        return state.activeQuests.values.map { quest ->
            val totalObjectives = quest.objectives.size
            val completedObjectives = quest.objectives.count { it.isComplete() }
            val completionPercentage = if (totalObjectives > 0) {
                (completedObjectives * 100) / totalObjectives
            } else 0

            QuestInfo(
                id = quest.id,
                name = quest.name,
                type = quest.type.name,
                status = quest.status.name,
                completionPercentage = completionPercentage
            )
        }
    }

    override fun getQuestDetails(questId: String, state: GameState): QuestDetails? {
        val quest = state.activeQuests[questId] ?: return null

        return QuestDetails(
            id = quest.id,
            name = quest.name,
            description = quest.description,
            type = quest.type.name,
            status = quest.status.name,
            objectives = quest.objectives.map { obj ->
                QuestObjectiveInfo(
                    description = obj.progressDescription(),
                    currentProgress = obj.currentProgress,
                    targetProgress = obj.targetProgress,
                    isComplete = obj.isComplete()
                )
            },
            rewards = QuestRewardInfo(
                xp = quest.rewards.xp,
                items = quest.rewards.items.map { it.name },
                gold = quest.rewards.gold,
                unlocksLocations = quest.rewards.unlockedLocationIds
            ),
            canComplete = quest.isComplete()
        )
    }

    override suspend fun generateNPC(
        name: String,
        role: String,
        locationId: String,
        personalityTraits: List<String>,
        backstory: String,
        motivations: List<String>,
        relationshipToPlayer: String
    ): NPCGenerationResult {
        // Validate inputs
        if (name.isBlank()) {
            return NPCGenerationResult(
                success = false,
                npc = null,
                message = "NPC name cannot be blank"
            )
        }

        if (locationId.isBlank()) {
            return NPCGenerationResult(
                success = false,
                npc = null,
                message = "Location ID cannot be blank"
            )
        }

        // Map role to archetype
        val archetype = when (role.lowercase()) {
            "merchant", "trader", "shopkeeper" -> NPCArchetype.MERCHANT
            "quest_giver", "quest giver", "questgiver" -> NPCArchetype.QUEST_GIVER
            "guard", "soldier", "defender" -> NPCArchetype.GUARD
            "innkeeper", "barkeep" -> NPCArchetype.INNKEEPER
            "blacksmith", "weaponsmith", "armorer" -> NPCArchetype.BLACKSMITH
            "alchemist", "potionmaker" -> NPCArchetype.ALCHEMIST
            "trainer", "teacher", "instructor" -> NPCArchetype.TRAINER
            "noble", "lord", "lady" -> NPCArchetype.NOBLE
            "scholar", "researcher", "sage" -> NPCArchetype.SCHOLAR
            "wanderer", "traveler", "stranger", "rival", "ally" -> NPCArchetype.WANDERER
            else -> NPCArchetype.VILLAGER
        }

        // Create personality object
        val personality = NPCPersonality(
            traits = personalityTraits.ifEmpty { listOf("neutral", "ordinary") },
            speechPattern = "Normal speech pattern",
            motivations = motivations.ifEmpty { listOf("Survive in the new world") }
        )

        // Generate unique ID
        val npcId = "npc_gen_${locationId}_${role}_${currentTimeMillis()}"

        // Create NPC (returned for orchestrator to add to state)
        val npc = NPC(
            id = npcId,
            name = name,
            archetype = archetype,
            locationId = locationId,
            personality = personality,
            lore = backstory,
            greetingContext = "Dynamically generated NPC"
        )

        return NPCGenerationResult(
            success = true,
            npc = npc,
            message = "$name has been created as a $role at $locationId"
        )
    }

    override suspend fun generateQuest(
        state: GameState,
        questType: String?,
        context: String?
    ): Quest? {
        val type = questType?.let { typeStr ->
            try {
                QuestType.valueOf(typeStr.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        return questGenerator.generateQuest(state, type, context)
    }

    override fun checkQuestProgress(state: GameState): List<QuestProgressUpdate> {
        // This method is used to check if any quest objectives can be auto-completed
        // based on current game state. For now, return empty list.
        // This will be called by GameOrchestrator after each action.
        return emptyList()
    }

    fun logEvent(event: GameEvent) {
        eventLog.add(event)
    }

    private fun extractTarget(input: String): String? {
        val words = input.lowercase().split(" ")
        val actionWords = setOf("attack", "fight", "kill", "strike", "talk", "speak", "ask", "thank", "greet", "tell")
        val skipWords = setOf("the", "to", "with", "at")

        val actionIndex = words.indexOfFirst { it in actionWords }
        if (actionIndex == -1 || actionIndex == words.size - 1) return null

        val nextWord = words[actionIndex + 1]
        return if (nextWord in skipWords && actionIndex + 2 < words.size) {
            words[actionIndex + 2]
        } else {
            nextWord
        }
    }

    /**
     * Extract skill name from player input.
     * Handles patterns like "use Power Strike", "cast Fireball", "activate Inner Focus"
     */
    private fun extractSkillName(input: String): String? {
        val lowerInput = input.lowercase()
        val skillActionWords = setOf("use", "cast", "activate", "evolve")
        val skipWords = setOf("the", "my", "skill")

        val words = input.split(" ")
        val lowerWords = lowerInput.split(" ")

        val actionIndex = lowerWords.indexOfFirst { it in skillActionWords }
        if (actionIndex == -1 || actionIndex == words.size - 1) return null

        // Collect remaining words after the action, skipping filler words
        val remainingWords = words.drop(actionIndex + 1)
            .filter { it.lowercase() !in skipWords }

        // Return multi-word skill name (e.g., "Power Strike", "Frost Bolt")
        return if (remainingWords.isNotEmpty()) {
            remainingWords.joinToString(" ")
        } else {
            null
        }
    }
}
