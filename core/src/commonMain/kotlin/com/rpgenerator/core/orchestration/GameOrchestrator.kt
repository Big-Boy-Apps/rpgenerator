package com.rpgenerator.core.orchestration

import com.rpgenerator.core.agents.AutonomousNPCAgent
import com.rpgenerator.core.agents.GameMasterAgent
import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.NarratorAgent
import com.rpgenerator.core.agents.NPCAgent
import com.rpgenerator.core.agents.QuestGeneratorAgent
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.QuestStatus
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.LocationManager
import com.rpgenerator.core.domain.NPC
import com.rpgenerator.core.domain.ObjectiveType
import com.rpgenerator.core.domain.Quest
import com.rpgenerator.core.domain.QuestType
import com.rpgenerator.core.domain.QuestObjective
import com.rpgenerator.core.domain.QuestRewards
import com.rpgenerator.core.domain.PlayerClass
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.skill.ActionContext
import com.rpgenerator.core.skill.SkillAcquisitionService
import com.rpgenerator.core.skill.SkillCombatService
import com.rpgenerator.core.skill.SkillDatabase
import com.rpgenerator.core.skill.SkillExecutionResult
import com.rpgenerator.core.story.MainStoryArc
import com.rpgenerator.core.story.NPCManager
import com.rpgenerator.core.story.StoryFoundation
import com.rpgenerator.core.story.StoryPlanningService
import com.rpgenerator.core.story.NarratorContext
import com.rpgenerator.core.generation.NPCArchetypeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rpgenerator.core.tools.GameTools
import com.rpgenerator.core.tools.GameToolsImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * Path complexity level for routing decisions
 */
internal enum class PathComplexity {
    SIMPLE,     // Fast path - direct routing to handlers
    COMPLEX     // Coordinated path - Game Master coordinates agents
}

internal class GameOrchestrator(
    private val llm: LLMInterface,
    private var gameState: GameState
) {
    // Lazy agent initialization - agents are only created when first used
    private val narratorAgent by lazy { NarratorAgent(llm) }
    private val npcAgent by lazy { NPCAgent(llm) }
    private val locationGeneratorAgent by lazy { LocationGeneratorAgent(llm) }
    private val questGeneratorAgent by lazy { QuestGeneratorAgent(llm) }
    private val gameMasterAgent by lazy { GameMasterAgent(llm) }
    private val autonomousNPCAgent by lazy { AutonomousNPCAgent(llm) }
    private val npcArchetypeGenerator by lazy { NPCArchetypeGenerator(llm) }
    private val storyPlanningService by lazy { StoryPlanningService(llm) }
    private val npcManager = NPCManager()
    private val locationManager = LocationManager().apply {
        loadLocations(gameState.systemType)
    }
    private val rulesEngine = RulesEngine()
    private val tools by lazy { GameToolsImpl(locationManager, rulesEngine, locationGeneratorAgent, questGeneratorAgent) }
    private val skillAcquisitionService = SkillAcquisitionService()
    private val skillCombatService = SkillCombatService()

    // Simple in-memory event log
    private val eventLog = mutableListOf<GameEvent>()

    // Story foundation - generated at game start, provides narrative context
    private var storyFoundation: StoryFoundation? = null
    private var storyPlanningStarted = false

    // Background scope for async operations
    private val backgroundScope = CoroutineScope(Dispatchers.Default)

    // Flag to track if we've initialized story NPCs
    private var storyNPCsInitialized = false

    suspend fun processInput(input: String): Flow<GameEvent> = flow {
        // Initialize NPCs first (adds to game state only, no events emitted yet)
        if (!storyNPCsInitialized) {
            initializeDynamicNPCsSilent()
            storyNPCsInitialized = true
        }

        // Play opening narration on first input (now NPCs are available in game state)
        if (!gameState.hasOpeningNarrationPlayed) {
            val openingNarration = narratorAgent.narrateOpening(gameState, storyFoundation?.narratorContext)
            emit(GameEvent.NarratorText(openingNarration))
            eventLog.add(GameEvent.NarratorText(openingNarration))
            gameState = gameState.copy(hasOpeningNarrationPlayed = true)

            // Now emit quest/NPC events after the opening narration
            emitInitialQuestEvents(this)

            // Don't process empty input after opening - player hasn't acted yet
            if (input.isBlank()) return@flow
        }

        // Skip empty input - player hasn't provided an action
        if (input.isBlank()) {
            return@flow
        }

        // Check if player is dead before processing input
        if (gameState.isDead) {
            emit(GameEvent.SystemNotification("You are dead. Respawning..."))
            handleDeath(this, "previous combat")
            return@flow
        }

        // Use simple event log instead of complex EventStore
        val recentEvents = eventLog.takeLast(5)

        // Determine if we should use fast path or coordinated path
        val complexity = analyzeComplexity(input, gameState, recentEvents)

        when (complexity) {
            PathComplexity.SIMPLE -> {
                // Fast path - direct routing to specific handlers
                handleSimplePath(input, recentEvents, this)
            }
            PathComplexity.COMPLEX -> {
                // Coordinated path - Game Master coordinates multiple agents
                handleCoordinatedPath(input, recentEvents, this)
            }
        }
    }

    /**
     * Analyze input complexity to determine routing strategy
     */
    private fun analyzeComplexity(
        input: String,
        state: GameState,
        recentEvents: List<GameEvent>
    ): PathComplexity {
        val lowerInput = input.lowercase()

        // Complex scenarios requiring coordination
        return when {
            // NPCs present - need to coordinate NPC reactions
            state.getNPCsAtCurrentLocation().isNotEmpty() -> PathComplexity.COMPLEX

            // Combat - may trigger quests, NPC reactions, environment changes
            lowerInput.contains("attack") ||
            lowerInput.contains("fight") ||
            lowerInput.contains("combat") -> PathComplexity.COMPLEX

            // Quest-related - may involve NPCs, locations, multiple objectives
            lowerInput.contains("quest") &&
            !lowerInput.contains("list") -> PathComplexity.COMPLEX

            // Exploration with high danger level - encounters likely
            lowerInput.contains("explore") &&
            state.currentLocation.danger >= 3 -> PathComplexity.COMPLEX

            // Simple queries - stats, inventory, quest list
            lowerInput.contains("status") ||
            lowerInput.contains("stat") ||
            lowerInput.contains("inventory") ||
            (lowerInput.contains("quest") && lowerInput.contains("list")) -> PathComplexity.SIMPLE

            // Empty exploration in safe areas
            state.getNPCsAtCurrentLocation().isEmpty() &&
            state.currentLocation.danger < 3 -> PathComplexity.SIMPLE

            // Default to simple for unknown commands
            else -> PathComplexity.SIMPLE
        }
    }

    /**
     * Fast path - direct routing without Game Master coordination
     */
    private suspend fun handleSimplePath(
        input: String,
        recentEvents: List<GameEvent>,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val intentAnalysis = tools.analyzeIntent(input, gameState, recentEvents)

        val validation = tools.validateAction(
            intentAnalysis.intent,
            intentAnalysis.target,
            gameState
        )

        if (!validation.valid) {
            flowCollector.emit(GameEvent.SystemNotification("Cannot perform action: ${validation.reason}"))
            return
        }

        // Execute the action directly
        executeAction(intentAnalysis, flowCollector, input)
    }

    /**
     * Coordinated path - Game Master creates a scene plan, mechanics execute, Narrator renders.
     *
     * Flow:
     * 1. GM analyzes situation and creates a ScenePlan
     * 2. Execute mechanical actions (combat, movement, etc.)
     * 3. Build SceneResults from mechanical execution
     * 4. Narrator renders plan + results into cohesive prose
     * 5. Emit single unified narrative
     */
    private suspend fun handleCoordinatedPath(
        input: String,
        recentEvents: List<GameEvent>,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // Convert recent events to strings for context
        val recentEventStrings = recentEvents.map { eventToString(it) }

        // Step 1: Game Master creates the scene plan
        val scenePlan = gameMasterAgent.planScene(
            playerInput = input,
            state = gameState,
            recentEvents = recentEventStrings,
            npcsAtLocation = gameState.getNPCsAtCurrentLocation()
        )

        // Validate the planned action
        val intentAnalysis = tools.analyzeIntent(input, gameState, recentEvents)
        val validation = tools.validateAction(
            intentAnalysis.intent,
            intentAnalysis.target,
            gameState
        )

        if (!validation.valid) {
            flowCollector.emit(GameEvent.SystemNotification("Cannot perform action: ${validation.reason}"))
            return
        }

        // Step 2: Execute mechanical actions and collect results
        val sceneResults = executeMechanicalActions(scenePlan, intentAnalysis, input)

        // Step 3: Narrator renders the complete scene
        val unifiedNarration = narratorAgent.renderScene(
            plan = scenePlan,
            results = sceneResults,
            state = gameState,
            playerInput = input,
            narratorContext = storyFoundation?.narratorContext
        )

        // Step 4: Emit the unified narrative
        val narrativeEvent = GameEvent.NarratorText(unifiedNarration)
        flowCollector.emit(narrativeEvent)
        eventLog.add(narrativeEvent)

        // Emit mechanical events for UI tracking (these happen silently in the background)
        emitMechanicalEvents(sceneResults, flowCollector)

        // Handle any triggered events from the scene plan
        handleTriggeredEvents(scenePlan.triggeredEvents, flowCollector)
    }

    /**
     * Execute the mechanical parts of a scene and return results
     */
    private suspend fun executeMechanicalActions(
        plan: ScenePlan,
        intentAnalysis: com.rpgenerator.core.tools.IntentAnalysis,
        input: String
    ): SceneResults {
        var combatResult: CombatSceneResult? = null
        var xpChange: XPChange? = null
        val itemsGained = mutableListOf<ItemGain>()
        val locationsDiscovered = mutableListOf<String>()
        val questUpdates = mutableListOf<QuestProgressUpdate>()
        val stateChanges = mutableListOf<String>()

        when (plan.primaryAction.type) {
            ActionType.COMBAT -> {
                val target = plan.primaryAction.target ?: intentAnalysis.target ?: "enemy"
                val result = tools.resolveCombat(target, gameState)

                combatResult = CombatSceneResult(
                    target = target,
                    damageDealt = result.damage,
                    damageReceived = 0, // TODO: Track damage received
                    enemyDefeated = true, // Simplified for now
                    criticalHit = result.damage > 20, // Simplified crit detection
                    specialEffects = emptyList()
                )

                if (result.xpGained > 0) {
                    xpChange = XPChange(
                        xpGained = result.xpGained,
                        newTotal = gameState.playerXP + result.xpGained,
                        leveledUp = result.levelUp,
                        newLevel = if (result.levelUp) result.newLevel else null
                    )
                    gameState = gameState.gainXP(result.xpGained)
                }

                // Handle loot
                result.loot.forEach { generatedItem ->
                    itemsGained.add(ItemGain(
                        itemName = generatedItem.getName(),
                        quantity = generatedItem.getQuantity(),
                        rarity = "common" // TODO: Get actual rarity
                    ))
                    gameState = addItemToInventory(generatedItem)
                }

                if (result.gold > 0) {
                    stateChanges.add("Gained ${result.gold} gold")
                }

                // Check for death
                if (gameState.isDead) {
                    stateChanges.add("Player died")
                }
            }

            ActionType.EXPLORATION -> {
                if (intentAnalysis.shouldGenerateLocation && intentAnalysis.locationGenerationContext != null) {
                    val generatedLocation = tools.generateLocation(
                        parentLocation = gameState.currentLocation,
                        discoveryContext = intentAnalysis.locationGenerationContext,
                        state = gameState
                    )
                    if (generatedLocation != null) {
                        gameState = gameState.addCustomLocation(generatedLocation)
                        locationsDiscovered.add(generatedLocation.name)
                    }
                } else {
                    val connectedLocations = tools.getConnectedLocations(gameState)
                    connectedLocations.forEach { loc ->
                        if (!gameState.discoveredTemplateLocations.contains(loc.id)) {
                            gameState = gameState.discoverLocation(loc.id)
                            locationsDiscovered.add(loc.name)
                        }
                    }
                }
            }

            ActionType.DIALOGUE -> {
                // Dialogue is handled through NPC reactions in the scene plan
                val npcName = plan.primaryAction.target ?: intentAnalysis.target
                if (npcName != null) {
                    val npc = gameState.findNPCByName(npcName)
                    if (npc != null) {
                        // Update relationship based on dialogue
                        val relationshipChange = calculateRelationshipChange(input)
                        if (relationshipChange != 0) {
                            val updatedNPC = npc.updateRelationship(gameState.gameId, relationshipChange)
                            gameState = gameState.updateNPC(updatedNPC)
                            stateChanges.add("Relationship with ${npc.name} changed by $relationshipChange")
                        }
                    }
                }
            }

            ActionType.QUEST_ACTION -> {
                // Handle quest actions
                handleQuestAction(input, questUpdates, stateChanges)
            }

            else -> {
                // SYSTEM_QUERY, MOVEMENT, INTERACTION - minimal mechanical impact
            }
        }

        // Track quest progress for all action types
        trackQuestProgressSilent(intentAnalysis.intent, intentAnalysis.target, questUpdates)

        return SceneResults(
            combatResult = combatResult,
            itemsGained = itemsGained,
            xpChange = xpChange,
            locationsDiscovered = locationsDiscovered,
            questUpdates = questUpdates,
            stateChanges = stateChanges
        )
    }

    private fun addItemToInventory(generatedItem: com.rpgenerator.core.loot.GeneratedItem): GameState {
        return when (generatedItem) {
            is com.rpgenerator.core.loot.GeneratedItem.GeneratedInventoryItem -> {
                gameState.addItem(generatedItem.item)
            }
            else -> {
                gameState.addItem(
                    com.rpgenerator.core.domain.InventoryItem(
                        id = generatedItem.getId(),
                        name = generatedItem.getName(),
                        description = "Equipment: ${generatedItem.getName()}",
                        type = com.rpgenerator.core.domain.ItemType.MISC,
                        quantity = 1,
                        stackable = false
                    )
                )
            }
        }
    }

    private fun calculateRelationshipChange(input: String): Int {
        val lowerInput = input.lowercase()
        return when {
            lowerInput.contains("thank") || lowerInput.contains("please") -> 5
            lowerInput.contains("insult") || lowerInput.contains("threaten") -> -10
            else -> 1
        }
    }

    private fun handleQuestAction(
        input: String,
        questUpdates: MutableList<QuestProgressUpdate>,
        stateChanges: MutableList<String>
    ) {
        val lowerInput = input.lowercase()
        when {
            lowerInput.contains("complete") || lowerInput.contains("turn in") -> {
                val completableQuests = gameState.activeQuests.values.filter { it.isComplete() }
                completableQuests.forEach { quest ->
                    gameState = gameState.completeQuest(quest.id)
                    questUpdates.add(QuestProgressUpdate(
                        questName = quest.name,
                        objectiveCompleted = "All objectives",
                        questComplete = true
                    ))
                    stateChanges.add("Completed quest: ${quest.name}")
                }
            }
        }
    }

    private fun trackQuestProgressSilent(
        intent: Intent,
        target: String?,
        questUpdates: MutableList<QuestProgressUpdate>
    ) {
        gameState.activeQuests.values.forEach { quest ->
            quest.objectives.forEach { objective ->
                val shouldUpdate = when (objective.type) {
                    ObjectiveType.KILL -> intent == Intent.COMBAT && target != null &&
                            objective.targetId.lowercase() == target.lowercase()
                    ObjectiveType.REACH_LOCATION -> objective.targetId == gameState.currentLocation.id &&
                            !objective.isComplete()
                    ObjectiveType.EXPLORE -> intent == Intent.EXPLORATION &&
                            gameState.discoveredTemplateLocations.contains(objective.targetId)
                    else -> false
                }

                if (shouldUpdate && !objective.isComplete()) {
                    gameState = gameState.updateQuestObjective(quest.id, objective.id, 1)
                    val updatedQuest = gameState.activeQuests[quest.id]
                    val updatedObj = updatedQuest?.objectives?.find { it.id == objective.id }

                    if (updatedObj != null) {
                        questUpdates.add(QuestProgressUpdate(
                            questName = quest.name,
                            objectiveCompleted = updatedObj.progressDescription(),
                            questComplete = updatedQuest.isComplete()
                        ))
                    }
                }
            }
        }
    }

    private suspend fun emitMechanicalEvents(
        results: SceneResults,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // These are emitted for UI tracking but don't add narrative text

        results.xpChange?.let { xp ->
            val statChange = GameEvent.StatChange(
                "xp",
                (xp.newTotal - xp.xpGained).toInt(),
                xp.newTotal.toInt()
            )
            flowCollector.emit(statChange)
            eventLog.add(statChange)

            if (xp.leveledUp && xp.newLevel != null) {
                val levelUp = GameEvent.SystemNotification("Level up! You are now level ${xp.newLevel}")
                flowCollector.emit(levelUp)
                eventLog.add(levelUp)
            }
        }

        results.itemsGained.forEach { item ->
            val itemEvent = GameEvent.ItemGained(
                itemId = item.itemName.lowercase().replace(" ", "_"),
                itemName = item.itemName,
                quantity = item.quantity
            )
            flowCollector.emit(itemEvent)
            eventLog.add(itemEvent)
        }

        results.questUpdates.filter { it.questComplete }.forEach { update ->
            val questEvent = GameEvent.QuestUpdate(
                questId = update.questName.lowercase().replace(" ", "_"),
                questName = update.questName,
                status = QuestStatus.COMPLETED
            )
            flowCollector.emit(questEvent)
            eventLog.add(questEvent)
        }
    }

    private suspend fun handleTriggeredEvents(
        triggeredEvents: List<TriggeredEvent>,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        triggeredEvents.filter { it.timing == EventTiming.IMMEDIATE }.forEach { event ->
            when (event.eventType) {
                EventType.NPC_ARRIVAL -> {
                    // Handle NPC spawning
                    val notification = GameEvent.SystemNotification(event.description)
                    flowCollector.emit(notification)
                    eventLog.add(notification)
                }
                EventType.ENCOUNTER -> {
                    val notification = GameEvent.SystemNotification(event.description)
                    flowCollector.emit(notification)
                    eventLog.add(notification)
                }
                else -> {
                    // Other event types handled silently or through narrative
                }
            }
        }
    }

    private fun eventToString(event: GameEvent): String = when (event) {
        is GameEvent.NarratorText -> event.text
        is GameEvent.SystemNotification -> event.text
        is GameEvent.NPCDialogue -> "${event.npcName}: ${event.text}"
        else -> event.toString()
    }

    /**
     * Execute the validated action based on intent
     */
    private suspend fun executeAction(
        intentAnalysis: com.rpgenerator.core.tools.IntentAnalysis,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        input: String = ""
    ) {
        when (intentAnalysis.intent) {
            Intent.COMBAT -> {
                val target = intentAnalysis.target ?: "unknown enemy"
                val combatResult = tools.resolveCombat(target, gameState)

                val narration = narratorAgent.narrateCombat(
                    input = input,
                    target = target,
                    outcome = com.rpgenerator.core.rules.CombatOutcome(
                        damage = combatResult.damage,
                        xpGain = combatResult.xpGained,
                        newXP = gameState.playerXP + combatResult.xpGained,
                        levelUp = combatResult.levelUp,
                        newLevel = combatResult.newLevel,
                        loot = combatResult.loot,
                        gold = combatResult.gold
                    ),
                    state = gameState
                )

                val narratorEvent = GameEvent.NarratorText(narration)
                flowCollector.emit(narratorEvent)
                eventLog.add(narratorEvent)

                val combatLog = GameEvent.CombatLog("You dealt ${combatResult.damage} damage to $target")
                flowCollector.emit(combatLog)
                eventLog.add(combatLog)

                val statChange = GameEvent.StatChange(
                    "xp",
                    gameState.playerXP.toInt(),
                    (gameState.playerXP + combatResult.xpGained).toInt()
                )
                flowCollector.emit(statChange)
                eventLog.add(statChange)

                if (combatResult.levelUp) {
                    val levelUpNotif = GameEvent.SystemNotification("Level up! You are now level ${combatResult.newLevel}")
                    flowCollector.emit(levelUpNotif)
                    eventLog.add(levelUpNotif)
                }

                // Handle loot drops
                if (combatResult.loot.isNotEmpty()) {
                    combatResult.loot.forEach { generatedItem ->
                        val itemGained = GameEvent.ItemGained(
                            itemId = generatedItem.getId(),
                            itemName = generatedItem.getName(),
                            quantity = generatedItem.getQuantity()
                        )
                        flowCollector.emit(itemGained)
                        eventLog.add(itemGained)

                        // Add to character inventory/equipment
                        gameState = when (generatedItem) {
                            is com.rpgenerator.core.loot.GeneratedItem.GeneratedInventoryItem -> {
                                gameState.addItem(generatedItem.item)
                            }
                            is com.rpgenerator.core.loot.GeneratedItem.GeneratedWeapon,
                            is com.rpgenerator.core.loot.GeneratedItem.GeneratedArmor,
                            is com.rpgenerator.core.loot.GeneratedItem.GeneratedAccessory -> {
                                // Equipment items are added to inventory as InventoryItem placeholders
                                // or could be auto-equipped if better than current
                                gameState.addItem(
                                    com.rpgenerator.core.domain.InventoryItem(
                                        id = generatedItem.getId(),
                                        name = generatedItem.getName(),
                                        description = "Equipment: ${generatedItem.getName()}",
                                        type = com.rpgenerator.core.domain.ItemType.MISC,
                                        quantity = 1,
                                        stackable = false
                                    )
                                )
                            }
                        }
                    }
                }

                // Handle gold
                if (combatResult.gold > 0) {
                    val goldNotif = GameEvent.SystemNotification("You found ${combatResult.gold} gold!")
                    flowCollector.emit(goldNotif)
                    eventLog.add(goldNotif)
                    // TODO: Add gold tracking to character sheet
                }

                gameState = gameState.gainXP(combatResult.xpGained)

                // Check if player died in combat
                if (gameState.isDead) {
                    handleDeath(flowCollector, target)
                    return
                }
            }

            Intent.EXPLORATION -> {
                // Check if we should generate a new location
                if (intentAnalysis.shouldGenerateLocation && intentAnalysis.locationGenerationContext != null) {
                    val generatedLocation = tools.generateLocation(
                        parentLocation = gameState.currentLocation,
                        discoveryContext = intentAnalysis.locationGenerationContext,
                        state = gameState
                    )

                    if (generatedLocation != null) {
                        gameState = gameState.addCustomLocation(generatedLocation)
                        val discovery = GameEvent.SystemNotification("Discovered: ${generatedLocation.name}")
                        flowCollector.emit(discovery)
                        eventLog.add(discovery)

                        val narration = GameEvent.NarratorText(
                            "Your exploration reveals ${generatedLocation.name}. ${generatedLocation.description}"
                        )
                        flowCollector.emit(narration)
                        eventLog.add(narration)
                    } else {
                        val failure = GameEvent.NarratorText("Your search turns up nothing of interest.")
                        flowCollector.emit(failure)
                        eventLog.add(failure)
                    }
                } else {
                    // Normal exploration - discover connected template locations
                    val connectedLocations = tools.getConnectedLocations(gameState)

                    connectedLocations.forEach { loc ->
                        if (!gameState.discoveredTemplateLocations.contains(loc.id)) {
                            gameState = gameState.discoverLocation(loc.id)
                            val discovery = GameEvent.SystemNotification("Discovered: ${loc.name}")
                            flowCollector.emit(discovery)
                            eventLog.add(discovery)
                        }
                    }

                    // Generate contextual narration based on player input
                    val explorationNarration = narratorAgent.narrateExploration(
                        input = input,
                        state = gameState,
                        connectedLocations = connectedLocations
                    )
                    val narration = GameEvent.NarratorText(explorationNarration)
                    flowCollector.emit(narration)
                    eventLog.add(narration)
                }
            }

            Intent.SYSTEM_QUERY -> {
                val status = tools.getPlayerStatus(gameState)
                val statusText = "Level ${status.level}, XP: ${status.xp}/${status.xpToNextLevel}"
                val statusEvent = GameEvent.SystemNotification(statusText)
                flowCollector.emit(statusEvent)
                eventLog.add(statusEvent)
            }

            Intent.NPC_DIALOGUE -> {
                val npcName = intentAnalysis.target ?: "unknown"
                var npc = gameState.findNPCByName(npcName)

                // If fuzzy matching failed, try LLM resolution
                if (npc == null) {
                    val availableNpcs = gameState.getNPCsAtCurrentLocation()
                    if (availableNpcs.isEmpty()) {
                        val notFound = GameEvent.SystemNotification("There's no one here to talk to.")
                        flowCollector.emit(notFound)
                        eventLog.add(notFound)
                        return
                    }

                    // Use LLM to resolve which NPC the player means
                    npc = resolveNPCWithLLM(input, availableNpcs)

                    if (npc == null) {
                        val options = availableNpcs.joinToString(", ") { it.name }
                        val notFound = GameEvent.SystemNotification("Who do you want to talk to? Available: $options")
                        flowCollector.emit(notFound)
                        eventLog.add(notFound)
                        return
                    }
                }

                // Generate NPC dialogue using NPCAgent
                val npcResponse = npcAgent.generateDialogue(npc, input, gameState)

                // Emit NPC dialogue event
                val dialogueEvent = GameEvent.NPCDialogue(
                    npcId = npc.id,
                    npcName = npc.name,
                    text = npcResponse
                )
                flowCollector.emit(dialogueEvent)
                eventLog.add(dialogueEvent)

                // Update NPC with conversation history
                val updatedNPC = npc.addConversation(input, npcResponse, gameState.playerLevel)
                gameState = gameState.updateNPC(updatedNPC)

                // Check if the dialogue should affect relationship (simple heuristic for now)
                // In a more advanced system, this could be determined by sentiment analysis
                val relationshipChange = if (input.lowercase().contains("thank") ||
                                           input.lowercase().contains("please")) {
                    5
                } else if (input.lowercase().contains("insult") ||
                          input.lowercase().contains("attack") ||
                          input.lowercase().contains("threaten")) {
                    -10
                } else {
                    1 // Small positive gain for any interaction
                }

                if (relationshipChange != 0) {
                    val npcWithRelationship = updatedNPC.updateRelationship(gameState.gameId, relationshipChange)
                    gameState = gameState.updateNPC(npcWithRelationship)

                    val relationship = npcWithRelationship.getRelationship(gameState.gameId)
                    if (relationshipChange > 0 && relationship.affinity % 20 == 0) {
                        // Notify on milestone relationship increases
                        val relNotif = GameEvent.SystemNotification(
                            "${npc.name} seems to trust you more. (${relationship.getStatus()})"
                        )
                        flowCollector.emit(relNotif)
                        eventLog.add(relNotif)
                    }
                }
            }

            Intent.QUEST_ACTION -> {
                val lowerInput = input.lowercase()

                when {
                    lowerInput.contains("list") || lowerInput.contains("show") -> {
                        val quests = tools.getActiveQuests(gameState)

                        if (quests.isEmpty()) {
                            val noQuests = GameEvent.SystemNotification("You have no active quests.")
                            flowCollector.emit(noQuests)
                            eventLog.add(noQuests)
                        } else {
                            quests.forEach { quest ->
                                val questInfo = GameEvent.SystemNotification(
                                    "${quest.name} (${quest.type}) - ${quest.completionPercentage}% complete"
                                )
                                flowCollector.emit(questInfo)
                                eventLog.add(questInfo)
                            }
                        }
                    }

                    lowerInput.contains("new") || lowerInput.contains("generate") || lowerInput.contains("get") -> {
                        val newQuest = tools.generateQuest(gameState, null, input)

                        if (newQuest != null) {
                            gameState = gameState.addQuest(newQuest)

                            val questEvent = GameEvent.QuestUpdate(
                                questId = newQuest.id,
                                questName = newQuest.name,
                                status = QuestStatus.NEW
                            )
                            flowCollector.emit(questEvent)
                            eventLog.add(questEvent)

                            val questDetails = GameEvent.SystemNotification(
                                "New Quest: ${newQuest.name}\n${newQuest.description}"
                            )
                            flowCollector.emit(questDetails)
                            eventLog.add(questDetails)
                        } else {
                            val failure = GameEvent.SystemNotification("Failed to generate quest.")
                            flowCollector.emit(failure)
                            eventLog.add(failure)
                        }
                    }

                    lowerInput.contains("complete") || lowerInput.contains("turn in") -> {
                        val completableQuests = gameState.activeQuests.values.filter { it.isComplete() }

                        if (completableQuests.isEmpty()) {
                            val none = GameEvent.SystemNotification("You have no quests ready to complete.")
                            flowCollector.emit(none)
                            eventLog.add(none)
                        } else {
                            completableQuests.forEach { quest ->
                                gameState = gameState.completeQuest(quest.id)

                                val questCompleted = GameEvent.QuestUpdate(
                                    questId = quest.id,
                                    questName = quest.name,
                                    status = QuestStatus.COMPLETED
                                )
                                flowCollector.emit(questCompleted)
                                eventLog.add(questCompleted)

                                val rewardNotif = GameEvent.SystemNotification(
                                    "Quest Complete: ${quest.name}! Gained ${quest.rewards.xp} XP"
                                )
                                flowCollector.emit(rewardNotif)
                                eventLog.add(rewardNotif)

                                quest.rewards.items.forEach { item ->
                                    val itemGained = GameEvent.ItemGained(item.id, item.name, item.quantity)
                                    flowCollector.emit(itemGained)
                                    eventLog.add(itemGained)
                                }
                            }
                        }
                    }

                    else -> {
                        val help = GameEvent.SystemNotification(
                            "Quest commands: 'list quests', 'get new quest', 'complete quests'"
                        )
                        flowCollector.emit(help)
                        eventLog.add(help)
                    }
                }
            }

            Intent.CLASS_SELECTION -> {
                handleClassSelection(intentAnalysis.target, input, flowCollector)
            }

            Intent.SKILL_MENU -> {
                handleSkillMenu(flowCollector)
            }

            Intent.USE_SKILL -> {
                handleUseSkill(intentAnalysis.target, flowCollector, input)
            }

            Intent.SKILL_EVOLUTION -> {
                handleSkillEvolution(intentAnalysis.target, flowCollector)
            }

            Intent.SKILL_FUSION -> {
                handleSkillFusion(flowCollector, input)
            }

            Intent.STATUS_MENU -> {
                handleStatusMenu(flowCollector)
            }

            Intent.INVENTORY_MENU -> {
                handleInventoryMenu(flowCollector)
            }
        }

        // Track quest progress after each action
        trackQuestProgress(intentAnalysis.intent, intentAnalysis.target, flowCollector)
    }

    /**
     * Handle class selection during tutorial.
     * Shows classes grouped by archetype and supports custom class generation
     * for players who push for something unique.
     */
    private suspend fun handleClassSelection(
        chosenClassName: String?,
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // Check if player already has a class
        if (gameState.characterSheet.playerClass != PlayerClass.NONE) {
            val alreadyHasClass = GameEvent.SystemNotification(
                "You have already chosen the ${gameState.characterSheet.playerClass.displayName} class."
            )
            flowCollector.emit(alreadyHasClass)
            eventLog.add(alreadyHasClass)
            return
        }

        // Check if player is asking for a custom/unique class
        val lowerInput = input.lowercase()
        val isAskingForCustom = lowerInput.contains("custom") ||
            lowerInput.contains("unique") ||
            lowerInput.contains("special") ||
            lowerInput.contains("different") ||
            lowerInput.contains("create my own") ||
            lowerInput.contains("make my own") ||
            lowerInput.contains("something else") ||
            lowerInput.contains("none of these") ||
            lowerInput.contains("other") ||
            (lowerInput.contains("want") && lowerInput.contains("be")) || // "I want to be a..."
            (lowerInput.contains("can i") && lowerInput.contains("be"))   // "Can I be a..."

        if (isAskingForCustom && chosenClassName == null) {
            // Player is pushing for something custom - engage with them
            handleCustomClassRequest(input, flowCollector)
            return
        }

        // If no specific class chosen, show options grouped by archetype
        if (chosenClassName == null) {
            showClassOptions(flowCollector)
            return
        }

        // Check if this looks like a custom class request disguised as a class name
        val availableClasses = PlayerClass.selectableClasses()
        val selectedClass = availableClasses.find {
            it.name.equals(chosenClassName, ignoreCase = true) ||
            it.displayName.equals(chosenClassName, ignoreCase = true)
        }

        if (selectedClass == null) {
            // Not a standard class - this might be a custom class request!
            // Try to generate a custom class based on what they asked for
            handleCustomClassRequest(input, flowCollector, customClassName = chosenClassName)
            return
        }

        // Apply the class selection
        applyClassSelection(selectedClass, flowCollector)
    }

    /**
     * Show class options grouped by archetype.
     */
    private suspend fun showClassOptions(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>) {
        val header = GameEvent.SystemNotification(
            "╔══════════════════════════════════════════════════════════════╗\n" +
            "║              SYSTEM CLASS SELECTION                          ║\n" +
            "║  Choose your path. This decision shapes your destiny.        ║\n" +
            "╚══════════════════════════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        // Group classes by archetype
        val classesByArchetype = PlayerClass.byArchetype()

        classesByArchetype.forEach { (archetype, classes) ->
            val archetypeHeader = GameEvent.SystemNotification(
                "\n═══ ${archetype.displayName.uppercase()} ═══"
            )
            flowCollector.emit(archetypeHeader)
            eventLog.add(archetypeHeader)

            classes.forEach { playerClass ->
                val classInfo = GameEvent.SystemNotification(
                    "  【${playerClass.displayName}】 ${playerClass.description}"
                )
                flowCollector.emit(classInfo)
                eventLog.add(classInfo)
            }
        }

        val footer = GameEvent.SystemNotification(
            "\n═══════════════════════════════════════════════════════════════\n" +
            "Type a class name to select it.\n" +
            "Or describe what kind of path you want - the System may accommodate.\n" +
            "═══════════════════════════════════════════════════════════════"
        )
        flowCollector.emit(footer)
        eventLog.add(footer)

        // Generate narrative for class selection moment
        val availableClasses = PlayerClass.selectableClasses()
        val narration = narratorAgent.narrateClassSelection(gameState, availableClasses)
        val narrativeEvent = GameEvent.NarratorText(narration)
        flowCollector.emit(narrativeEvent)
        eventLog.add(narrativeEvent)
    }

    /**
     * Handle requests for custom/unique classes.
     * The System (via LLM) evaluates if the request is worthy and generates a unique class.
     */
    private suspend fun handleCustomClassRequest(
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        customClassName: String? = null
    ) {
        val requestedClass = customClassName ?: input

        // Ask the LLM to evaluate and potentially generate a custom class
        val customClassResult = generateCustomClass(requestedClass, gameState)

        if (customClassResult != null) {
            // The System grants a custom class!
            val grantNotice = GameEvent.SystemNotification(
                "╔══════════════════════════════════════════════════════════════╗\n" +
                "║           SYSTEM NOTIFICATION: UNIQUE CLASS DETECTED         ║\n" +
                "╚══════════════════════════════════════════════════════════════╝\n\n" +
                "The System recognizes your unconventional request.\n" +
                "Analyzing compatibility... Generating unique class template..."
            )
            flowCollector.emit(grantNotice)
            eventLog.add(grantNotice)

            // Apply the custom class (we'll map it to the closest archetype)
            val baseClass = customClassResult.baseClass
            applyClassSelection(baseClass, flowCollector, customClassResult.customName, customClassResult.customDescription)
        } else {
            // The System doesn't grant a custom class - guide them back to options
            val denial = GameEvent.SystemNotification(
                "The System considers your request...\n\n" +
                "\"${requestedClass}\" is not recognized as a valid class path.\n" +
                "Perhaps describe what you're looking for? Or choose from the available paths.\n" +
                "\nType 'classes' to see available options."
            )
            flowCollector.emit(denial)
            eventLog.add(denial)
        }
    }

    /**
     * Generate a custom class based on player request.
     * Returns null if the request doesn't warrant a custom class.
     */
    private suspend fun generateCustomClass(request: String, state: GameState): CustomClassResult? {
        // Use LLM to evaluate and generate a custom class
        val systemPrompt = """
You are the System, an impartial cosmic force that assigns classes to Integrated beings.
You evaluate non-standard class requests and either grant unique classes or reject unworthy requests.
Respond ONLY in the exact format specified - no additional text.
""".trim()

        val prompt = """
A player has requested a non-standard class: "$request"
Player backstory: ${state.backstory}
Player name: ${state.playerName}

Evaluate if this is a legitimate creative request worthy of a unique class.
Reject requests that are:
- Too vague (just "custom" or "something cool")
- Joke/troll requests
- Overpowered wish fulfillment ("god" "invincible" etc)

If worthy, generate a unique class that:
1. Fits the LitRPG/System Apocalypse genre
2. Has a unique identity related to their request
3. Maps to one of these base archetypes: SLAYER, BULWARK, STRIKER, CHANNELER, CULTIVATOR, PSION, ADAPTER, SURVIVALIST, BLADE_DANCER, ARTIFICER, HEALER, COMMANDER, CONTRACTOR, GLITCH, ECHO

Respond in EXACTLY this format (or REJECT if not worthy):
ACCEPT
CLASS_NAME: [Unique class name, 1-3 words]
DESCRIPTION: [One sentence description of the class]
BASE_ARCHETYPE: [One of the archetypes listed above]

Or if rejecting:
REJECT
REASON: [Brief reason]
""".trim()

        val agent = llm.startAgent(systemPrompt)
        val response = agent.sendMessage(prompt).toList().joinToString("")
        val lines = response.trim().lines()

        if (lines.isEmpty() || lines[0].trim().uppercase() == "REJECT") {
            return null
        }

        if (lines[0].trim().uppercase() == "ACCEPT" && lines.size >= 4) {
            val className = lines.find { it.startsWith("CLASS_NAME:") }
                ?.substringAfter(":")?.trim() ?: return null
            val description = lines.find { it.startsWith("DESCRIPTION:") }
                ?.substringAfter(":")?.trim() ?: return null
            val archetypeName = lines.find { it.startsWith("BASE_ARCHETYPE:") }
                ?.substringAfter(":")?.trim()?.uppercase() ?: return null

            val baseClass = try {
                PlayerClass.valueOf(archetypeName)
            } catch (e: Exception) {
                PlayerClass.ADAPTER // Default fallback
            }

            return CustomClassResult(
                customName = className,
                customDescription = description,
                baseClass = baseClass
            )
        }

        return null
    }

    /**
     * Apply the class selection to the character.
     */
    private suspend fun applyClassSelection(
        selectedClass: PlayerClass,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        customName: String? = null,
        customDescription: String? = null
    ) {
        val displayName = customName ?: selectedClass.displayName
        val description = customDescription ?: selectedClass.description

        val newSheet = gameState.characterSheet.chooseInitialClass(selectedClass)
        gameState = gameState.copy(characterSheet = newSheet)

        // Mark tutorial objective complete
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_class", 1)
        }

        // Emit class selection notification
        val isCustom = customName != null
        val headerText = if (isCustom) "UNIQUE CLASS GRANTED" else "CLASS CHOSEN"
        val paddedName = displayName.uppercase().take(22).padEnd(22)

        val classChosen = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║  $headerText: $paddedName║\n" +
            "╚════════════════════════════════════════╝\n\n" +
            "$description\n\n" +
            if (isCustom) "Base archetype: ${selectedClass.displayName}\n" else "" +
            "Stat bonuses applied!"
        )
        flowCollector.emit(classChosen)
        eventLog.add(classChosen)

        // Generate narrative for class acquisition
        val narration = narratorAgent.narrateClassAcquisition(gameState, selectedClass)
        val narrativeEvent = GameEvent.NarratorText(narration)
        flowCollector.emit(narrativeEvent)
        eventLog.add(narrativeEvent)

        // Quest progress notification
        val progressEvent = GameEvent.SystemNotification(
            "Quest Progress: System Integration - Choose your class (Complete)"
        )
        flowCollector.emit(progressEvent)
        eventLog.add(progressEvent)
    }

    /**
     * Use LLM to resolve which NPC the player is trying to interact with.
     */
    private suspend fun resolveNPCWithLLM(playerInput: String, availableNpcs: List<NPC>): NPC? {
        if (availableNpcs.isEmpty()) return null
        if (availableNpcs.size == 1) return availableNpcs.first()

        val npcList = availableNpcs.mapIndexed { i, npc ->
            "${i + 1}. ${npc.name} (${npc.archetype.name.lowercase().replace("_", " ")})"
        }.joinToString("\n")

        val prompt = """
Player input: "$playerInput"

NPCs present:
$npcList

Which NPC (if any) is the player trying to interact with?
Respond with ONLY the number (1, 2, etc.) or "NONE" if unclear.
""".trim()

        val agent = llm.startAgent("You resolve ambiguous NPC references. Respond only with a number or NONE.")
        val response = agent.sendMessage(prompt).toList().joinToString("").trim()

        val index = response.toIntOrNull()?.minus(1)
        return if (index != null && index in availableNpcs.indices) {
            availableNpcs[index]
        } else {
            null
        }
    }

    /** Result of custom class generation */
    private data class CustomClassResult(
        val customName: String,
        val customDescription: String,
        val baseClass: PlayerClass
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL HANDLERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Display the skill menu.
     */
    private suspend fun handleSkillMenu(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val sheet = gameState.characterSheet
        val skills = sheet.skills

        if (skills.isEmpty()) {
            val noSkills = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║            SKILLS                      ║\n" +
                "╚════════════════════════════════════════╝\n" +
                "\nYou haven't learned any skills yet.\n" +
                "Skills are learned by:\n" +
                "  • Repeating actions (Action Insight)\n" +
                "  • Class selection (starter skills)\n" +
                "  • Quest rewards\n" +
                "  • Skill books and NPCs"
            )
            flowCollector.emit(noSkills)
            eventLog.add(noSkills)
            return
        }

        val header = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║            SKILLS (${skills.size.toString().padStart(2)})                   ║\n" +
            "╚════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        // Display each skill
        skills.forEachIndexed { index, skill ->
            val cooldownStr = if (skill.currentCooldown > 0) " [CD: ${skill.currentCooldown}]" else ""
            val canEvolveStr = if (skill.canEvolve()) " ★EVOLVE READY★" else ""
            val activeStr = if (skill.isActive) "" else " (Passive)"

            val skillInfo = GameEvent.SystemNotification(
                "\n${index + 1}. [${skill.rarity.symbol}] ${skill.name} Lv.${skill.level}$activeStr\n" +
                "   ${skill.xpBar()} ${skill.levelProgress()}% to next level\n" +
                "   Cost: ${skill.costString()}$cooldownStr$canEvolveStr\n" +
                "   ${skill.description}"
            )
            flowCollector.emit(skillInfo)
            eventLog.add(skillInfo)
        }

        // Show partial skills (hints)
        val partialSkills = sheet.getPartialSkills()
        if (partialSkills.isNotEmpty()) {
            val partialHeader = GameEvent.SystemNotification("\n--- Developing Skills (Insights) ---")
            flowCollector.emit(partialHeader)
            eventLog.add(partialHeader)

            partialSkills.forEach { partial ->
                val hintText = GameEvent.SystemNotification("  ${partial.hintText()}")
                flowCollector.emit(hintText)
                eventLog.add(hintText)
            }
        }

        // Show help
        val helpText = GameEvent.SystemNotification(
            "\nCommands: 'use [skill name]', 'evolve skill [name]', 'fuse skills'"
        )
        flowCollector.emit(helpText)
        eventLog.add(helpText)

        // Mark tutorial objective if applicable
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            val skillObj = tutorialQuest.objectives.find { it.id == "tutorial_obj_skill" }
            if (skillObj != null && !skillObj.isComplete()) {
                gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_skill", 1)
                val progressNotif = GameEvent.SystemNotification(
                    "Quest Progress: Survive the Tutorial - Learn a basic skill (Complete)"
                )
                flowCollector.emit(progressNotif)
                eventLog.add(progressNotif)
            }
        }
    }

    /**
     * Handle using a skill.
     */
    private suspend fun handleUseSkill(
        skillNameOrId: String?,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        input: String
    ) {
        if (skillNameOrId == null) {
            val noSkill = GameEvent.SystemNotification("Specify a skill to use. Example: 'use Power Strike'")
            flowCollector.emit(noSkill)
            eventLog.add(noSkill)
            return
        }

        val sheet = gameState.characterSheet
        val skill = sheet.skills.find {
            it.id.equals(skillNameOrId, ignoreCase = true) ||
            it.name.equals(skillNameOrId, ignoreCase = true) ||
            it.name.lowercase().contains(skillNameOrId.lowercase())
        }

        if (skill == null) {
            val unknownSkill = GameEvent.SystemNotification(
                "You don't know a skill called '$skillNameOrId'.\n" +
                "Known skills: ${sheet.skills.joinToString(", ") { it.name }}"
            )
            flowCollector.emit(unknownSkill)
            eventLog.add(unknownSkill)
            return
        }

        if (!skill.isActive) {
            val passiveSkill = GameEvent.SystemNotification(
                "${skill.name} is a passive skill - it's always active!"
            )
            flowCollector.emit(passiveSkill)
            eventLog.add(passiveSkill)
            return
        }

        // Execute the skill
        val result = skillCombatService.executeSkill(
            skill = skill,
            user = sheet,
            targetDefense = 10,  // Default target defense
            targetWisdom = 10
        )

        when (result) {
            is SkillExecutionResult.Success -> {
                // Use the skill (spend resources, start cooldown, gain XP)
                val newSheet = sheet.useSkill(skill.id, result.xpGained)
                if (newSheet != null) {
                    gameState = gameState.copy(characterSheet = newSheet)
                }

                val narration = narratorAgent.narrateSkillUse(skill, result, gameState)
                val skillEvent = GameEvent.NarratorText(narration)
                flowCollector.emit(skillEvent)
                eventLog.add(skillEvent)

                // Combat log
                val combatLog = GameEvent.CombatLog(result.narrativeSummary())
                flowCollector.emit(combatLog)
                eventLog.add(combatLog)

                // Check for level up
                val updatedSkill = newSheet?.getSkill(skill.id)
                if (updatedSkill != null && updatedSkill.level > skill.level) {
                    val levelUpNotif = GameEvent.SystemNotification(
                        "★ ${skill.name} leveled up to Level ${updatedSkill.level}! ★"
                    )
                    flowCollector.emit(levelUpNotif)
                    eventLog.add(levelUpNotif)
                }
            }

            is SkillExecutionResult.OnCooldown -> {
                val cdNotif = GameEvent.SystemNotification(
                    "${skill.name} is on cooldown for ${result.turnsRemaining} more turn(s)."
                )
                flowCollector.emit(cdNotif)
                eventLog.add(cdNotif)
            }

            is SkillExecutionResult.InsufficientResources -> {
                val missing = result.getMissingResources()
                val resourceNotif = GameEvent.SystemNotification(
                    "Not enough resources for ${skill.name}. Need: ${missing.joinToString(", ")}"
                )
                flowCollector.emit(resourceNotif)
                eventLog.add(resourceNotif)
            }
        }

        // Process action insight for this input
        processActionInsight(input, flowCollector)
    }

    /**
     * Handle skill evolution.
     */
    private suspend fun handleSkillEvolution(
        skillNameOrId: String?,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val sheet = gameState.characterSheet

        // Find skills that can evolve
        val evolvableSkills = sheet.skills.filter { it.canEvolve() }

        if (evolvableSkills.isEmpty()) {
            val noEvolve = GameEvent.SystemNotification(
                "No skills ready to evolve. Skills can evolve at max level."
            )
            flowCollector.emit(noEvolve)
            eventLog.add(noEvolve)
            return
        }

        // If no specific skill named, show evolution options
        if (skillNameOrId == null) {
            val header = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║       SKILL EVOLUTION                  ║\n" +
                "╚════════════════════════════════════════╝\n" +
                "\nSkills ready to evolve:"
            )
            flowCollector.emit(header)
            eventLog.add(header)

            evolvableSkills.forEach { skill ->
                val options = skillAcquisitionService.getEvolutionOptions(
                    skill = skill,
                    stats = sheet.effectiveStats(),
                    playerLevel = sheet.level,
                    completedQuests = gameState.completedQuests
                )

                val skillInfo = GameEvent.SystemNotification(
                    "\n[${skill.rarity.symbol}] ${skill.name} (Lv. MAX)\n" +
                    "Evolution paths:"
                )
                flowCollector.emit(skillInfo)
                eventLog.add(skillInfo)

                options.forEach { option ->
                    val statusStr = if (option.requirementsMet) "✓ Ready" else "✗ Locked"
                    val reqStr = if (option.unmetRequirements.isNotEmpty()) {
                        "\n      Requires: ${option.unmetRequirements.joinToString(", ") { it.describe() }}"
                    } else ""

                    val pathInfo = GameEvent.SystemNotification(
                        "  → ${option.path.evolvesIntoName} [$statusStr]$reqStr\n" +
                        "    ${option.path.description}"
                    )
                    flowCollector.emit(pathInfo)
                    eventLog.add(pathInfo)
                }
            }

            val helpText = GameEvent.SystemNotification(
                "\nTo evolve: 'evolve skill [skill name] into [evolution name]'"
            )
            flowCollector.emit(helpText)
            eventLog.add(helpText)
            return
        }

        // Find the specific skill to evolve
        val skill = evolvableSkills.find {
            it.id.equals(skillNameOrId, ignoreCase = true) ||
            it.name.equals(skillNameOrId, ignoreCase = true) ||
            it.name.lowercase().contains(skillNameOrId.lowercase())
        }

        if (skill == null) {
            val notFound = GameEvent.SystemNotification(
                "Skill '$skillNameOrId' not found or not ready to evolve."
            )
            flowCollector.emit(notFound)
            eventLog.add(notFound)
            return
        }

        // For now, auto-select the first available evolution path
        val options = skillAcquisitionService.getEvolutionOptions(
            skill = skill,
            stats = sheet.effectiveStats(),
            playerLevel = sheet.level,
            completedQuests = gameState.completedQuests
        )

        val availableOption = options.find { it.requirementsMet }
        if (availableOption == null) {
            val locked = GameEvent.SystemNotification(
                "No evolution paths available for ${skill.name} yet. Check requirements."
            )
            flowCollector.emit(locked)
            eventLog.add(locked)
            return
        }

        // Perform evolution
        val event = skillAcquisitionService.evolveSkill(
            skill = skill,
            evolutionPathId = availableOption.path.evolvesIntoId,
            stats = sheet.effectiveStats(),
            playerLevel = sheet.level,
            completedQuests = gameState.completedQuests
        )

        when (event) {
            is com.rpgenerator.core.skill.SkillAcquisitionEvent.SkillEvolved -> {
                // Update character sheet
                val newSheet = sheet.removeSkill(skill.id).addSkill(event.newSkill)
                gameState = gameState.copy(characterSheet = newSheet)

                val evolveNotif = GameEvent.SystemNotification(
                    "╔════════════════════════════════════════╗\n" +
                    "║       ★ SKILL EVOLVED! ★               ║\n" +
                    "╚════════════════════════════════════════╝\n\n" +
                    "${skill.name} has evolved into ${event.newSkill.name}!\n" +
                    "[${event.newSkill.rarity.displayName}] ${event.newSkill.description}"
                )
                flowCollector.emit(evolveNotif)
                eventLog.add(evolveNotif)
            }

            is com.rpgenerator.core.skill.SkillAcquisitionEvent.EvolutionFailed -> {
                val failNotif = GameEvent.SystemNotification("Evolution failed: ${event.reason}")
                flowCollector.emit(failNotif)
                eventLog.add(failNotif)
            }

            else -> {}
        }
    }

    /**
     * Handle skill fusion.
     */
    private suspend fun handleSkillFusion(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        input: String
    ) {
        val sheet = gameState.characterSheet

        if (sheet.skills.size < 2) {
            val notEnough = GameEvent.SystemNotification(
                "Need at least 2 skills to attempt fusion."
            )
            flowCollector.emit(notEnough)
            eventLog.add(notEnough)
            return
        }

        // Get available fusions
        val availableFusions = skillAcquisitionService.getAvailableFusions(
            ownedSkills = sheet.skills,
            discoveredFusions = sheet.discoveredFusions
        )

        // Show fusion options
        val header = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║       SKILL FUSION                     ║\n" +
            "╚════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        if (availableFusions.isEmpty()) {
            val noFusions = GameEvent.SystemNotification(
                "\nNo fusion recipes available with your current skills.\n" +
                "Learn more skills to unlock fusion possibilities!"
            )
            flowCollector.emit(noFusions)
            eventLog.add(noFusions)

            // Show hints if any
            val hints = skillAcquisitionService.getFusionHints(
                ownedSkillIds = sheet.skills.map { it.id }.toSet(),
                discoveredFusions = sheet.discoveredFusions
            )
            if (hints.isNotEmpty()) {
                val hintHeader = GameEvent.SystemNotification("\n--- Fusion Hints ---")
                flowCollector.emit(hintHeader)
                eventLog.add(hintHeader)

                hints.take(3).forEach { hint ->
                    val hintText = GameEvent.SystemNotification("  • ${hint.hint}")
                    flowCollector.emit(hintText)
                    eventLog.add(hintText)
                }
            }
            return
        }

        availableFusions.forEachIndexed { index, option ->
            val discoveredStr = if (option.isDiscovered) " (Known)" else " (Undiscovered)"
            val levelStr = if (!option.levelRequirementsMet) {
                "\n   ⚠ Level requirements not met"
            } else ""

            val fusionInfo = GameEvent.SystemNotification(
                "\n${index + 1}. ${option.recipe.name}$discoveredStr\n" +
                "   Combines: ${option.recipe.inputSkillIds.joinToString(" + ")}\n" +
                "   Creates: ${option.recipe.resultSkillName} [${option.recipe.resultRarity.displayName}]$levelStr"
            )
            flowCollector.emit(fusionInfo)
            eventLog.add(fusionInfo)
        }

        val helpText = GameEvent.SystemNotification(
            "\nTo fuse, level up the required skills and try the combination!"
        )
        flowCollector.emit(helpText)
        eventLog.add(helpText)
    }

    /**
     * Display detailed character status.
     */
    private suspend fun handleStatusMenu(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val sheet = gameState.characterSheet
        val stats = sheet.effectiveStats()

        val statusText = """
╔═══════════════════════════════════════════════════════════════╗
║                    CHARACTER STATUS                           ║
╠═══════════════════════════════════════════════════════════════╣
║  Name: ${gameState.playerName.padEnd(20)} Level: ${sheet.level.toString().padEnd(10)} ║
║  Class: ${sheet.playerClass.displayName.padEnd(18)} Grade: ${sheet.currentGrade.name.padEnd(11)} ║
╠═══════════════════════════════════════════════════════════════╣
║  HP:     ${sheet.resources.currentHP}/${sheet.resources.maxHP}${" ".repeat(50 - "${sheet.resources.currentHP}/${sheet.resources.maxHP}".length)}║
║  MP:     ${sheet.resources.currentMana}/${sheet.resources.maxMana}${" ".repeat(50 - "${sheet.resources.currentMana}/${sheet.resources.maxMana}".length)}║
║  Energy: ${sheet.resources.currentEnergy}/${sheet.resources.maxEnergy}${" ".repeat(50 - "${sheet.resources.currentEnergy}/${sheet.resources.maxEnergy}".length)}║
╠═══════════════════════════════════════════════════════════════╣
║  STR: ${stats.strength.toString().padEnd(5)} DEX: ${stats.dexterity.toString().padEnd(5)} CON: ${stats.constitution.toString().padEnd(18)}║
║  INT: ${stats.intelligence.toString().padEnd(5)} WIS: ${stats.wisdom.toString().padEnd(5)} CHA: ${stats.charisma.toString().padEnd(18)}║
║  DEF: ${stats.defense.toString().padEnd(54)}║
╠═══════════════════════════════════════════════════════════════╣
║  XP: ${sheet.xp}/${sheet.xpToNextLevel()} to next level${" ".repeat(40 - "${sheet.xp}/${sheet.xpToNextLevel()} to next level".length)}║
║  Skills: ${sheet.skills.size}   Unspent Points: ${sheet.unspentStatPoints}${" ".repeat(30 - "${sheet.skills.size}   Unspent Points: ${sheet.unspentStatPoints}".length)}║
╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent()

        val statusEvent = GameEvent.SystemNotification(statusText)
        flowCollector.emit(statusEvent)
        eventLog.add(statusEvent)

        // Mark tutorial objective if applicable
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            val statsObj = tutorialQuest.objectives.find { it.id == "tutorial_obj_stats" }
            if (statsObj != null && !statsObj.isComplete()) {
                gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_stats", 1)
                val progressNotif = GameEvent.SystemNotification(
                    "Quest Progress: Survive the Tutorial - Check your status (Complete)"
                )
                flowCollector.emit(progressNotif)
                eventLog.add(progressNotif)
            }
        }
    }

    /**
     * Display inventory.
     */
    private suspend fun handleInventoryMenu(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val inventory = gameState.characterSheet.inventory
        val equipment = gameState.characterSheet.equipment

        val header = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║           INVENTORY                    ║\n" +
            "╚════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        // Show equipment
        val equipmentText = GameEvent.SystemNotification(
            "\n--- Equipped ---\n" +
            "  Weapon:    ${equipment.weapon?.name ?: "(none)"}\n" +
            "  Armor:     ${equipment.armor?.name ?: "(none)"}\n" +
            "  Accessory: ${equipment.accessory?.name ?: "(none)"}"
        )
        flowCollector.emit(equipmentText)
        eventLog.add(equipmentText)

        // Show inventory items
        if (inventory.items.isEmpty()) {
            val emptyText = GameEvent.SystemNotification("\n--- Items ---\n  (empty)")
            flowCollector.emit(emptyText)
            eventLog.add(emptyText)
        } else {
            val itemsHeader = GameEvent.SystemNotification("\n--- Items (${inventory.items.size}/${inventory.maxSlots}) ---")
            flowCollector.emit(itemsHeader)
            eventLog.add(itemsHeader)

            inventory.items.values.forEach { item ->
                val qtyStr = if (item.quantity > 1) " x${item.quantity}" else ""
                val itemText = GameEvent.SystemNotification(
                    "  [${item.type.name}] ${item.name}$qtyStr"
                )
                flowCollector.emit(itemText)
                eventLog.add(itemText)
            }
        }
    }

    /**
     * Process player input for action insight skill learning.
     */
    private suspend fun processActionInsight(
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val context = ActionContext(
            equippedWeaponType = gameState.characterSheet.equipment.weapon?.name,
            inCombat = false  // Could track actual combat state
        )

        val (updatedSheet, newSkills) = gameState.characterSheet.processActionInsight(input, context)
        gameState = gameState.copy(characterSheet = updatedSheet)

        // Notify about any new skills learned
        newSkills.forEach { skill ->
            val learnedNotif = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║      ★ NEW SKILL LEARNED! ★            ║\n" +
                "╚════════════════════════════════════════╝\n\n" +
                "Through repeated practice, you've gained insight into:\n\n" +
                "[${skill.rarity.displayName}] ${skill.name}\n" +
                "${skill.description}"
            )
            flowCollector.emit(learnedNotif)
            eventLog.add(learnedNotif)
        }
    }

    private suspend fun trackQuestProgress(
        intent: Intent,
        target: String?,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {

        gameState.activeQuests.values.forEach { quest ->
            quest.objectives.forEach { objective ->
                val shouldUpdate = when (objective.type) {
                    ObjectiveType.KILL -> {
                        intent == Intent.COMBAT && target != null &&
                        objective.targetId.lowercase() == target.lowercase()
                    }
                    ObjectiveType.REACH_LOCATION -> {
                        objective.targetId == gameState.currentLocation.id && !objective.isComplete()
                    }
                    ObjectiveType.EXPLORE -> {
                        intent == Intent.EXPLORATION &&
                        gameState.discoveredTemplateLocations.contains(objective.targetId)
                    }
                    else -> false
                }

                if (shouldUpdate && !objective.isComplete()) {
                    gameState = gameState.updateQuestObjective(quest.id, objective.id, 1)

                    val updatedQuest = gameState.activeQuests[quest.id]
                    val updatedObj = updatedQuest?.objectives?.find { it.id == objective.id }

                    if (updatedObj != null) {
                        val progressEvent = GameEvent.SystemNotification(
                            "Quest Progress: ${quest.name} - ${updatedObj.progressDescription()}"
                        )
                        flowCollector.emit(progressEvent)
                        eventLog.add(progressEvent)

                        if (updatedQuest.isComplete()) {
                            val questUpdateEvent = GameEvent.QuestUpdate(
                                questId = quest.id,
                                questName = quest.name,
                                status = QuestStatus.IN_PROGRESS
                            )
                            flowCollector.emit(questUpdateEvent)
                            eventLog.add(questUpdateEvent)
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle player death based on system type.
     */
    private suspend fun handleDeath(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>, cause: String) {
        // Emit death narration
        val deathNarration = narratorAgent.narrateDeath(gameState, cause)
        val deathEvent = GameEvent.NarratorText(deathNarration)
        flowCollector.emit(deathEvent)
        eventLog.add(deathEvent)

        // Increment death count
        val newDeathCount = gameState.deathCount + 1
        gameState = gameState.copy(deathCount = newDeathCount)

        // Handle death based on system type
        when (gameState.systemType) {
            com.rpgenerator.core.api.SystemType.DEATH_LOOP -> {
                // Death makes you stronger - respawn with bonuses
                val deathBonus = newDeathCount * 2 // +2 to all stats per death
                val newStats = gameState.characterSheet.baseStats.copy(
                    strength = gameState.characterSheet.baseStats.strength + deathBonus,
                    dexterity = gameState.characterSheet.baseStats.dexterity + deathBonus,
                    constitution = gameState.characterSheet.baseStats.constitution + deathBonus,
                    intelligence = gameState.characterSheet.baseStats.intelligence + deathBonus,
                    wisdom = gameState.characterSheet.baseStats.wisdom + deathBonus,
                    charisma = gameState.characterSheet.baseStats.charisma + deathBonus
                )

                val newSheet = gameState.characterSheet.copy(baseStats = newStats)
                val restoredSheet = newSheet.copy(resources = newSheet.resources.restore())

                gameState = gameState.copy(characterSheet = restoredSheet)

                // Emit respawn narration
                val respawnNarration = narratorAgent.narrateRespawn(gameState)
                val respawnEvent = GameEvent.NarratorText(respawnNarration)
                flowCollector.emit(respawnEvent)
                eventLog.add(respawnEvent)

                val bonusNotif = GameEvent.SystemNotification(
                    "Death has strengthened you. All stats increased by $deathBonus!"
                )
                flowCollector.emit(bonusNotif)
                eventLog.add(bonusNotif)
            }

            com.rpgenerator.core.api.SystemType.DUNGEON_DELVE -> {
                // Permadeath - game over
                val gameOverEvent = GameEvent.SystemNotification(
                    "GAME OVER - Permadeath. Your adventure ends here. Total deaths: $newDeathCount"
                )
                flowCollector.emit(gameOverEvent)
                eventLog.add(gameOverEvent)
                // Character remains dead - player must start a new game
            }

            else -> {
                // Standard respawn - restore HP, small level penalty
                val restoredSheet = gameState.characterSheet.copy(
                    resources = gameState.characterSheet.resources.restore(),
                    xp = (gameState.playerXP * 0.9).toLong() // 10% XP penalty
                )

                gameState = gameState.copy(characterSheet = restoredSheet)

                val respawnEvent = GameEvent.SystemNotification(
                    "You have respawned. Lost 10% XP as death penalty."
                )
                flowCollector.emit(respawnEvent)
                eventLog.add(respawnEvent)
            }
        }
    }

    /**
     * Initialize dynamically generated NPCs for the current location (if in tutorial).
     * Silent version - only updates game state, does NOT emit any events.
     * Events are emitted separately via emitInitialQuestEvents() after the opening narration.
     */
    private suspend fun initializeDynamicNPCsSilent() {
        // Start story planning in background - don't block game start
        if (!storyPlanningStarted) {
            storyPlanningStarted = true
            backgroundScope.launch {
                try {
                    val foundation = storyPlanningService.initializeStory(
                        gameId = gameState.gameId,
                        systemType = gameState.systemType,
                        playerName = gameState.playerName,
                        backstory = gameState.backstory,
                        startingLocation = gameState.currentLocation
                    )
                    storyFoundation = foundation
                } catch (e: Exception) {
                    // Story planning failed - game continues without it
                    println("Story planning failed: ${e.message}")
                }
            }
        }

        // Generate tutorial guide dynamically if in tutorial zone
        if (gameState.currentLocation.id.contains("tutorial")) {
            // Generate unique tutorial guide for this playthrough
            val tutorialGuide = npcArchetypeGenerator.generateTutorialGuide(
                playerName = gameState.playerName,
                systemType = gameState.systemType,
                playerLevel = gameState.playerLevel,
                seed = gameState.gameId.hashCode().toLong()
            )

            // Add to game state and NPC manager (no events emitted)
            gameState = gameState.addNPC(tutorialGuide)
            npcManager.registerGeneratedNPC(tutorialGuide)

            // Add tutorial quest if not already present (no events emitted yet)
            if (!gameState.activeQuests.containsKey("quest_survive_tutorial")) {
                val tutorialQuest = createTutorialQuest(tutorialGuide.name)
                gameState = gameState.addQuest(tutorialQuest)
            }
        }
    }

    /**
     * Get the narrator context from the story foundation.
     * Returns a default context if story planning hasn't been initialized.
     */
    internal fun getNarratorContext(): NarratorContext? {
        return storyFoundation?.narratorContext
    }

    /**
     * Get the full story foundation for debugging/inspection.
     */
    internal fun getStoryFoundation(): StoryFoundation? {
        return storyFoundation
    }

    /**
     * Emit initial quest and NPC events AFTER the opening narration has been displayed.
     * This ensures the narrative flow is: Opening narration -> Quest notification -> NPC notice
     */
    private suspend fun emitInitialQuestEvents(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>) {
        // Emit quest notification for tutorial quest
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            val questEvent = GameEvent.QuestUpdate(
                questId = tutorialQuest.id,
                questName = tutorialQuest.name,
                status = QuestStatus.NEW
            )
            flowCollector.emit(questEvent)
            eventLog.add(questEvent)
        }

        // Emit notification about tutorial guide's presence (if in tutorial)
        if (gameState.currentLocation.id.contains("tutorial")) {
            val tutorialGuide = gameState.getNPCsAtCurrentLocation().firstOrNull()
            if (tutorialGuide != null) {
                val guideNotice = GameEvent.SystemNotification("${tutorialGuide.name} materializes before you.")
                flowCollector.emit(guideNotice)
                eventLog.add(guideNotice)
            }
        }
    }

    /**
     * Initialize dynamically generated NPCs for the current location (if in tutorial)
     * @deprecated Use initializeDynamicNPCsSilent() + emitInitialQuestEvents() instead
     */
    private suspend fun initializeDynamicNPCs(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>) {
        // Generate tutorial guide dynamically if in tutorial zone
        if (gameState.currentLocation.id.contains("tutorial")) {
            // Generate unique tutorial guide for this playthrough
            val tutorialGuide = npcArchetypeGenerator.generateTutorialGuide(
                playerName = gameState.playerName,
                systemType = gameState.systemType,
                playerLevel = gameState.playerLevel,
                seed = gameState.gameId.hashCode().toLong()
            )

            // Add to game state and NPC manager
            gameState = gameState.addNPC(tutorialGuide)
            npcManager.registerGeneratedNPC(tutorialGuide)

            // Add tutorial quest if not already present
            if (!gameState.activeQuests.containsKey("quest_survive_tutorial")) {
                val tutorialQuest = createTutorialQuest(tutorialGuide.name)
                gameState = gameState.addQuest(tutorialQuest)

                val questEvent = GameEvent.QuestUpdate(
                    questId = tutorialQuest.id,
                    questName = tutorialQuest.name,
                    status = QuestStatus.NEW
                )
                flowCollector.emit(questEvent)
                eventLog.add(questEvent)
            }

            // Trigger first story beat
            val firstBeat = MainStoryArc.getStoryBeatForLevel(1)
            if (firstBeat != null) {
                val beatEvent = GameEvent.NarratorText(firstBeat.narration)
                flowCollector.emit(beatEvent)
                eventLog.add(beatEvent)

                // Emit notification about tutorial guide's presence
                val guideNotice = GameEvent.SystemNotification("${tutorialGuide.name} materializes before you.")
                flowCollector.emit(guideNotice)
                eventLog.add(guideNotice)
            }
        }
    }

    /**
     * Create the tutorial quest with proper objectives.
     * Class selection is the PRIMARY focus - everything else is secondary.
     *
     * Flow:
     * 1. Choose your class (the foundational decision that shapes everything)
     * 2. Review your status and understand your abilities
     * 3. Test your new powers (optional combat or skill use)
     */
    private fun createTutorialQuest(guideName: String): Quest {
        return Quest(
            id = "quest_survive_tutorial",
            name = "System Integration",
            description = "The System requires you to choose a path. Your class defines who you will become.",
            type = QuestType.MAIN_STORY,
            giver = guideName,
            objectives = listOf(
                QuestObjective(
                    id = "tutorial_obj_class",
                    description = "Choose your class - this decision shapes your entire future",
                    type = ObjectiveType.TALK,
                    targetId = "class",
                    targetProgress = 1
                ),
                QuestObjective(
                    id = "tutorial_obj_stats",
                    description = "Review your status and understand your new abilities",
                    type = ObjectiveType.TALK,
                    targetId = "status",
                    targetProgress = 1
                ),
                QuestObjective(
                    id = "tutorial_obj_test",
                    description = "Test your abilities - use a skill or defeat an enemy",
                    type = ObjectiveType.TALK,
                    targetId = "test",
                    targetProgress = 1
                )
            ),
            rewards = QuestRewards(
                xp = 250L,
                unlockedLocationIds = listOf("threshold_settlement", "fringe_zones")
            )
        )
    }

    /**
     * Check if NPCs want to take autonomous actions
     */
    private suspend fun checkAutonomousNPCActions(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // Get all NPCs at the current location
        val npcsAtLocation = gameState.getNPCsAtCurrentLocation()

        if (npcsAtLocation.isEmpty()) return

        val recentEvents = eventLog.takeLast(5).map {
            when (it) {
                is GameEvent.NarratorText -> it.text
                is GameEvent.SystemNotification -> it.text
                is GameEvent.NPCDialogue -> "${it.npcName}: ${it.text}"
                else -> it.toString()
            }
        }

        // Check each NPC to see if they want to act autonomously
        // Only check one NPC per turn to avoid overwhelming the player
        val npc = npcsAtLocation.randomOrNull() ?: return

        val action = autonomousNPCAgent.shouldNPCActAutonomously(
            npc = npc,
            state = gameState,
            recentEvents = recentEvents,
            timeElapsed = 30 // Could track actual time
        )

        if (action != null) {
            when (action.actionType) {
                com.rpgenerator.core.agents.NPCActionType.APPROACH_PLAYER -> {
                    // NPC initiates conversation
                    val initiatedDialogue = action.dialogue
                        ?: autonomousNPCAgent.generateInitiatedDialogue(npc, gameState, action.reason)

                    val approachEvent = GameEvent.NPCDialogue(
                        npcId = npc.id,
                        npcName = npc.name,
                        text = initiatedDialogue
                    )
                    flowCollector.emit(approachEvent)
                    eventLog.add(approachEvent)
                }
                com.rpgenerator.core.agents.NPCActionType.GIVE_WARNING -> {
                    val warning = action.dialogue ?: "${npc.name} looks concerned about recent events."
                    val warningEvent = GameEvent.NPCDialogue(
                        npcId = npc.id,
                        npcName = npc.name,
                        text = warning
                    )
                    flowCollector.emit(warningEvent)
                    eventLog.add(warningEvent)
                }
                com.rpgenerator.core.agents.NPCActionType.REACT_TO_EVENT -> {
                    val reaction = action.dialogue ?: "${npc.name} reacts to recent events."
                    val reactionEvent = GameEvent.NPCDialogue(
                        npcId = npc.id,
                        npcName = npc.name,
                        text = reaction
                    )
                    flowCollector.emit(reactionEvent)
                    eventLog.add(reactionEvent)
                }
                else -> {
                    // Other action types (move, offer quest) can be implemented later
                }
            }
        }
    }

    /**
     * Check if Game Master wants to spawn an NPC or trigger an encounter
     */
    private suspend fun checkGameMasterEvents(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        playerInput: String
    ) {
        val recentEvents = eventLog.takeLast(5).map {
            when (it) {
                is GameEvent.NarratorText -> it.text
                is GameEvent.SystemNotification -> it.text
                else -> it.toString()
            }
        }

        // Check if a new NPC should appear
        val npcDecision = gameMasterAgent.shouldCreateNPC(playerInput, gameState, recentEvents)

        if (npcDecision.shouldCreate && npcDecision.template != null) {
            val newNPC = gameMasterAgent.createNPC(npcDecision.template)
            gameState = gameState.addNPC(newNPC)
            npcManager.registerGeneratedNPC(newNPC)

            val npcAppearance = GameEvent.SystemNotification("${newNPC.name} appears nearby.")
            flowCollector.emit(npcAppearance)
            eventLog.add(npcAppearance)
        }

        // Check if a random encounter should trigger
        val encounterDecision = gameMasterAgent.shouldTriggerEncounter(gameState, recentEvents)

        if (encounterDecision.shouldTrigger) {
            val encounterNotice = GameEvent.SystemNotification(encounterDecision.description)
            flowCollector.emit(encounterNotice)
            eventLog.add(encounterNotice)
        }
    }

    fun getState(): GameState = gameState
}
