package com.rpgenerator.core.story

import com.rpgenerator.core.agents.AgentProposal
import com.rpgenerator.core.agents.ConsensusEngine
import com.rpgenerator.core.agents.PlannerAgent
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.util.currentTimeMillis
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * StoryPlanningService - Orchestrates long-term narrative planning
 *
 * Called at game initialization to:
 * 1. Generate a unique "System Definition" - what makes THIS world unique
 * 2. Create initial plot threads based on SystemType + player backstory
 * 3. Populate the PlotGraph with story beats
 * 4. Provide narrative context to the Narrator
 */
internal class StoryPlanningService(
    private val llm: LLMInterface,
    private val consensusEngine: ConsensusEngine = ConsensusEngine()
) {
    private val plannerAgent by lazy { PlannerAgent(llm) }

    // Lazy agent streams - only created when first accessed
    private val systemDefinerStream by lazy {
        llm.startAgent(
            """
            You are the WORLD ARCHITECT - you define the unique characteristics of this world.

            Your job is to take a generic SystemType (like "System Apocalypse") and make it SPECIFIC.
            What makes THIS apocalypse different from all the others in fiction?

            You create:
            - The unique "flavor" of the System (how it communicates, its personality, its rules)
            - Key mysteries to unravel
            - The central conflict or threat
            - Thematic elements that will resonate throughout the story

            Be creative but coherent. Every choice should create story potential.
            """.trimIndent()
        )
    }

    // Agent for generating plot threads from different perspectives
    private val storyPerspectiveStream by lazy {
        llm.startAgent(
            """
            You are a STORY PERSPECTIVE AGENT - you propose plot threads from a specific viewpoint.

            You'll be given a perspective (character-focused, world-focused, conflict-focused, mystery-focused)
            and asked to propose plot threads that serve that perspective.

            Your proposals should:
            - Feel organic to the character's backstory
            - Create opportunities for meaningful choices
            - Have clear beats that can be triggered at specific levels
            - Include foreshadowing opportunities
            """.trimIndent()
        )
    }

    /**
     * Initialize the story at game start.
     * Returns the complete narrative foundation.
     */
    suspend fun initializeStory(
        gameId: String,
        systemType: SystemType,
        playerName: String,
        backstory: String,
        startingLocation: Location
    ): StoryFoundation {
        // 1. Generate unique System definition
        val systemDefinition = generateSystemDefinition(systemType, playerName, backstory)

        // 2. Get plot proposals from multiple perspectives
        val proposals = generatePlotProposals(
            gameId = gameId,
            systemType = systemType,
            systemDefinition = systemDefinition,
            playerName = playerName,
            backstory = backstory,
            startingLocation = startingLocation
        )

        // 3. Use consensus engine to resolve any conflicts
        val initialState = createMinimalState(gameId, playerName, backstory, startingLocation, systemType)
        val consensusResult = consensusEngine.resolveProposals(proposals, initialState)

        // 4. Build the plot graph from accepted nodes
        var plotGraph = PlotGraph(gameId)
        consensusResult.acceptedNodes.forEach { node ->
            plotGraph = plotGraph.addNode(node)
        }
        consensusResult.acceptedEdges.forEach { edge ->
            plotGraph = plotGraph.addEdge(edge)
        }

        // 5. Convert to PlotThreads for easier access
        val plotThreads = extractPlotThreads(consensusResult.acceptedNodes)

        return StoryFoundation(
            systemDefinition = systemDefinition,
            plotGraph = plotGraph,
            plotThreads = plotThreads,
            initialForeshadowing = generateInitialForeshadowing(systemDefinition, plotThreads),
            narratorContext = buildNarratorContext(systemDefinition, plotThreads)
        )
    }

    /**
     * Generate a unique System definition based on SystemType and player context.
     */
    private suspend fun generateSystemDefinition(
        systemType: SystemType,
        playerName: String,
        backstory: String
    ): SystemDefinition {
        val baseDescription = when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> "System Apocalypse - Earth integrated into alien power system"
            SystemType.CULTIVATION_PATH -> "Xianxia Cultivation - Path to immortality through Qi"
            SystemType.DEATH_LOOP -> "Roguelike Death Loop - Death is a teacher, time resets"
            SystemType.DUNGEON_DELVE -> "Dungeon Crawl - Descend, survive, conquer"
            SystemType.ARCANE_ACADEMY -> "Magical Academy - Learn magic, survive politics"
            SystemType.TABLETOP_CLASSIC -> "Classic Fantasy - Heroes vs evil, swords and sorcery"
            SystemType.EPIC_JOURNEY -> "Epic Quest - Destiny calls, the road awaits"
            SystemType.HERO_AWAKENING -> "Power Awakening - Ordinary becomes extraordinary"
        }

        val prompt = """
            DEFINE THIS WORLD'S UNIQUE IDENTITY

            Base Type: $baseDescription
            Player: $playerName
            Backstory: $backstory

            Create a UNIQUE take on this genre. Not generic - SPECIFIC.

            Answer in JSON format:
            {
                "systemName": "The name THIS System calls itself (not just 'The System')",
                "systemPersonality": "How does the System communicate? Cold? Sarcastic? Cryptic? Hungry?",
                "uniqueMechanic": "What's ONE thing that makes THIS System different from others in fiction?",
                "centralMystery": "The big question that drives the overarching narrative",
                "primaryThreat": "The main danger players will face throughout their journey",
                "thematicCore": "The central theme - what is this story ABOUT beyond the mechanics?",
                "worldState": "Brief description of the current state of the world/setting",
                "keyFactions": [
                    {"name": "Faction name", "role": "Their role in the story", "stance": "ally/enemy/neutral/complex"}
                ],
                "narrativeHooks": ["Hook 1 tied to player backstory", "Hook 2 for future conflict", "Hook 3 mystery element"]
            }

            Make the systemName evocative. "The Axiom" or "INTEGRATION PROTOCOL 7" or "The Weave" - not just "System".
            The uniqueMechanic should be something that creates interesting story possibilities.
            The centralMystery should be something the player will slowly uncover across their journey.
            The narrativeHooks should connect to the player's backstory: "$backstory"
        """.trimIndent()

        val response = systemDefinerStream.sendMessage(prompt).toList().joinToString("")
        return parseSystemDefinition(response, systemType)
    }

    /**
     * Generate plot proposals from multiple story perspectives.
     */
    private suspend fun generatePlotProposals(
        gameId: String,
        systemType: SystemType,
        systemDefinition: SystemDefinition,
        playerName: String,
        backstory: String,
        startingLocation: Location
    ): List<AgentProposal> {
        val perspectives = listOf(
            "CHARACTER" to "Focus on personal growth, relationships, and internal conflicts based on the player's backstory",
            "WORLD" to "Focus on discovering world secrets, faction politics, and the nature of the System itself",
            "CONFLICT" to "Focus on escalating threats, rivalries, and confrontations that test the player",
            "MYSTERY" to "Focus on clues, revelations, and uncovering hidden truths about the world and System"
        )

        val proposals = mutableListOf<AgentProposal>()

        for ((perspectiveName, perspectiveDesc) in perspectives) {
            val proposal = generatePerspectiveProposal(
                perspective = perspectiveName,
                perspectiveDescription = perspectiveDesc,
                gameId = gameId,
                systemType = systemType,
                systemDefinition = systemDefinition,
                playerName = playerName,
                backstory = backstory,
                startingLocation = startingLocation
            )
            proposals.add(proposal)
        }

        return proposals
    }

    /**
     * Generate plot proposal from a specific story perspective.
     */
    private suspend fun generatePerspectiveProposal(
        perspective: String,
        perspectiveDescription: String,
        gameId: String,
        systemType: SystemType,
        systemDefinition: SystemDefinition,
        playerName: String,
        backstory: String,
        startingLocation: Location
    ): AgentProposal {
        val prompt = """
            GENERATE PLOT THREADS FROM THE $perspective PERSPECTIVE

            Your focus: $perspectiveDescription

            === WORLD CONTEXT ===
            System: ${systemDefinition.systemName}
            Type: $systemType
            Central Mystery: ${systemDefinition.centralMystery}
            Primary Threat: ${systemDefinition.primaryThreat}
            Theme: ${systemDefinition.thematicCore}

            === PLAYER CONTEXT ===
            Name: $playerName
            Backstory: $backstory
            Starting Location: ${startingLocation.name}

            === YOUR TASK ===
            Propose 2-3 plot threads that serve the $perspective perspective.
            Each thread should span multiple tier progressions (levels 1-100+).

            Respond in JSON:
            {
                "threads": [
                    {
                        "id": "unique_thread_id",
                        "name": "Thread Name",
                        "description": "What this arc is about",
                        "category": "MAIN_STORY|NPC_RELATIONSHIP|FACTION_CONFLICT|MYSTERY|etc",
                        "priority": "CRITICAL|HIGH|MEDIUM|LOW",
                        "beats": [
                            {
                                "id": "beat_id",
                                "title": "Beat Title",
                                "description": "What happens",
                                "beatType": "REVELATION|CONFRONTATION|CHOICE|LOSS|VICTORY|BETRAYAL|REUNION|TRANSFORMATION|ESCALATION",
                                "triggerLevel": 5,
                                "foreshadowing": "Hints to drop before this beat",
                                "consequences": "What changes after"
                            }
                        ]
                    }
                ],
                "confidence": 0.8,
                "reasoning": "Why these threads serve the $perspective perspective"
            }

            Make threads that:
            - Connect to the player's backstory
            - Tie into the System's central mystery or threat
            - Have clear beats at levels 5, 15, 30, 50, 75, 100 (roughly)
            - Create opportunities for meaningful player choice
        """.trimIndent()

        val response = storyPerspectiveStream.sendMessage(prompt).toList().joinToString("")
        return parseProposalResponse(response, perspective, gameId)
    }

    /**
     * Extract PlotThreads from accepted PlotNodes for easier access.
     */
    private fun extractPlotThreads(nodes: List<PlotNode>): List<PlotThread> {
        // Group nodes by thread ID
        val nodesByThread = nodes.groupBy { it.threadId }

        return nodesByThread.map { (threadId, threadNodes) ->
            val sortedNodes = threadNodes.sortedBy { it.position.sequence }
            val firstNode = sortedNodes.first()

            PlotThread(
                id = threadId,
                name = firstNode.beat.title.substringBefore(":").ifEmpty { threadId },
                description = firstNode.beat.description,
                category = inferCategory(firstNode),
                priority = inferPriority(firstNode),
                triggerConditions = PlotTrigger(minLevel = firstNode.beat.triggerLevel),
                plannedBeats = sortedNodes.map { it.beat },
                status = PlotThreadStatus.PENDING,
                createdAtLevel = 1
            )
        }
    }

    /**
     * Generate initial foreshadowing hints to weave into early narrative.
     */
    private suspend fun generateInitialForeshadowing(
        systemDefinition: SystemDefinition,
        plotThreads: List<PlotThread>
    ): List<ForeshadowingHint> {
        val earlyBeats = plotThreads
            .flatMap { it.plannedBeats }
            .filter { it.triggerLevel in 5..30 && it.foreshadowing != null }
            .take(5)

        return earlyBeats.map { beat ->
            ForeshadowingHint(
                targetBeatId = beat.id,
                hint = beat.foreshadowing ?: "",
                subtlety = if (beat.triggerLevel < 15) Subtlety.SUBTLE else Subtlety.MODERATE,
                canAppearFrom = 1,
                mustAppearBy = beat.triggerLevel - 3
            )
        }
    }

    /**
     * Build context string for the Narrator to use.
     */
    private fun buildNarratorContext(
        systemDefinition: SystemDefinition,
        plotThreads: List<PlotThread>
    ): NarratorContext {
        val activeThreadSummaries = plotThreads
            .filter { it.priority in listOf(PlotPriority.CRITICAL, PlotPriority.HIGH) }
            .take(3)
            .map { "${it.name}: ${it.description}" }

        val upcomingBeats = plotThreads
            .flatMap { thread -> thread.plannedBeats.map { thread.name to it } }
            .filter { (_, beat) -> beat.triggerLevel <= 10 }
            .sortedBy { (_, beat) -> beat.triggerLevel }
            .take(3)
            .map { (threadName, beat) -> "Level ${beat.triggerLevel}: ${beat.title} ($threadName)" }

        return NarratorContext(
            systemName = systemDefinition.systemName,
            systemPersonality = systemDefinition.systemPersonality,
            centralMystery = systemDefinition.centralMystery,
            primaryThreat = systemDefinition.primaryThreat,
            thematicCore = systemDefinition.thematicCore,
            activeThreads = activeThreadSummaries,
            upcomingBeats = upcomingBeats,
            currentForeshadowing = systemDefinition.narrativeHooks.take(2)
        )
    }

    // ========================
    // Parsing Helpers
    // ========================

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun parseSystemDefinition(response: String, systemType: SystemType): SystemDefinition {
        return try {
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = response.substring(jsonStart, jsonEnd)
                json.decodeFromString<SystemDefinitionJson>(jsonStr).toSystemDefinition(systemType)
            } else {
                createDefaultSystemDefinition(systemType)
            }
        } catch (e: Exception) {
            createDefaultSystemDefinition(systemType)
        }
    }

    private fun parseProposalResponse(response: String, perspective: String, gameId: String): AgentProposal {
        return try {
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = response.substring(jsonStart, jsonEnd)
                val parsed = json.decodeFromString<ProposalJson>(jsonStr)

                val nodes = mutableListOf<PlotNode>()
                val edges = mutableListOf<PlotEdge>()
                val ratings = mutableMapOf<String, Float>()

                parsed.threads.forEachIndexed { threadIndex, thread ->
                    var prevNodeId: String? = null

                    thread.beats.forEachIndexed { beatIndex, beat ->
                        val nodeId = "${thread.id}_${beat.id}"
                        val node = PlotNode(
                            id = nodeId,
                            beat = PlotBeat(
                                id = beat.id,
                                title = beat.title,
                                description = beat.description,
                                beatType = parseBeatType(beat.beatType),
                                triggerLevel = beat.triggerLevel,
                                foreshadowing = beat.foreshadowing,
                                consequences = beat.consequences
                            ),
                            threadId = thread.id,
                            position = GraphPosition(
                                tier = beat.triggerLevel / 25, // Rough tier mapping
                                sequence = beatIndex,
                                branch = 0
                            )
                        )
                        nodes.add(node)
                        ratings[nodeId] = parsed.confidence

                        // Create dependency edge from previous beat
                        if (prevNodeId != null) {
                            edges.add(PlotEdge(
                                id = "dep_${prevNodeId}_$nodeId",
                                fromNodeId = prevNodeId!!,
                                toNodeId = nodeId,
                                edgeType = EdgeType.DEPENDENCY
                            ))
                        }
                        prevNodeId = nodeId
                    }
                }

                AgentProposal(
                    agentId = "perspective_$perspective",
                    agentType = perspective,
                    proposedNodes = nodes,
                    proposedEdges = edges,
                    nodeRatings = ratings,
                    reasoning = parsed.reasoning
                )
            } else {
                createEmptyProposal(perspective)
            }
        } catch (e: Exception) {
            createEmptyProposal(perspective)
        }
    }

    private fun parseBeatType(type: String): PlotBeatType {
        return try {
            PlotBeatType.valueOf(type.uppercase())
        } catch (e: Exception) {
            PlotBeatType.ESCALATION
        }
    }

    private fun inferCategory(node: PlotNode): PlotCategory {
        return when {
            node.beat.involvedNPCs.isNotEmpty() -> PlotCategory.NPC_RELATIONSHIP
            node.beat.beatType == PlotBeatType.REVELATION -> PlotCategory.MYSTERY
            node.beat.beatType == PlotBeatType.CONFRONTATION -> PlotCategory.FACTION_CONFLICT
            else -> PlotCategory.MAIN_STORY
        }
    }

    private fun inferPriority(node: PlotNode): PlotPriority {
        return when {
            node.beat.triggerLevel <= 10 -> PlotPriority.CRITICAL
            node.beat.triggerLevel <= 30 -> PlotPriority.HIGH
            node.beat.triggerLevel <= 60 -> PlotPriority.MEDIUM
            else -> PlotPriority.LOW
        }
    }

    private fun createDefaultSystemDefinition(systemType: SystemType): SystemDefinition {
        return SystemDefinition(
            systemType = systemType,
            systemName = when (systemType) {
                SystemType.SYSTEM_INTEGRATION -> "The Axiom"
                SystemType.CULTIVATION_PATH -> "The Eternal Dao"
                SystemType.DEATH_LOOP -> "The Recursion"
                SystemType.DUNGEON_DELVE -> "The Labyrinth"
                SystemType.ARCANE_ACADEMY -> "The Collegium"
                SystemType.TABLETOP_CLASSIC -> "The Realm"
                SystemType.EPIC_JOURNEY -> "The Weave"
                SystemType.HERO_AWAKENING -> "The Catalyst"
            },
            systemPersonality = "Cold and calculating, but occasionally cryptic",
            uniqueMechanic = "The System seems to learn from each integrated being",
            centralMystery = "Why was Earth chosen for Integration?",
            primaryThreat = "The Integration is just the first wave",
            thematicCore = "What does it mean to be human in a gamified reality?",
            worldState = "30 days since Integration. Chaos reigns, but survivors adapt.",
            keyFactions = listOf(
                FactionSummary("Loyalists", "Those who embrace the System", "complex"),
                FactionSummary("Remnant", "Those who resist the System", "complex"),
                FactionSummary("Seekers", "Those who study the System", "neutral")
            ),
            narrativeHooks = listOf(
                "Something from your past has followed you into this new world",
                "The System knows more about you than it should",
                "Not everyone who enters the Tutorial emerges unchanged"
            )
        )
    }

    private fun createEmptyProposal(perspective: String): AgentProposal {
        return AgentProposal(
            agentId = "perspective_$perspective",
            agentType = perspective,
            proposedNodes = emptyList(),
            proposedEdges = emptyList(),
            nodeRatings = emptyMap(),
            reasoning = "Failed to generate proposal"
        )
    }

    private fun createMinimalState(
        gameId: String,
        playerName: String,
        backstory: String,
        location: Location,
        systemType: SystemType
    ): GameState {
        // Create minimal state for consensus engine evaluation
        val minimalStats = Stats(
            strength = 10,
            dexterity = 10,
            constitution = 10,
            intelligence = 10,
            wisdom = 10,
            charisma = 10
        )

        val minimalSheet = CharacterSheet(
            level = 1,
            xp = 0L,
            baseStats = minimalStats,
            resources = Resources.fromStats(minimalStats)
        )

        val defaultWorldSettings = com.rpgenerator.core.api.WorldSettings(
            worldName = "World",
            coreConcept = "System Integration",
            originStory = "The System arrived and changed everything",
            currentState = "Chaos and adaptation"
        )

        return GameState(
            gameId = gameId,
            playerName = playerName,
            backstory = backstory,
            systemType = systemType,
            worldSettings = defaultWorldSettings,
            characterSheet = minimalSheet,
            currentLocation = location
        )
    }
}

// ========================
// Data Classes
// ========================

/**
 * The complete narrative foundation for a game.
 */
@Serializable
internal data class StoryFoundation(
    val systemDefinition: SystemDefinition,
    val plotGraph: PlotGraph,
    val plotThreads: List<PlotThread>,
    val initialForeshadowing: List<ForeshadowingHint>,
    val narratorContext: NarratorContext
)

/**
 * Unique definition of this game's System.
 */
@Serializable
internal data class SystemDefinition(
    val systemType: SystemType,
    val systemName: String,
    val systemPersonality: String,
    val uniqueMechanic: String,
    val centralMystery: String,
    val primaryThreat: String,
    val thematicCore: String,
    val worldState: String,
    val keyFactions: List<FactionSummary>,
    val narrativeHooks: List<String>
)

@Serializable
internal data class FactionSummary(
    val name: String,
    val role: String,
    val stance: String
)

/**
 * A hint to foreshadow a future event.
 */
@Serializable
internal data class ForeshadowingHint(
    val targetBeatId: String,
    val hint: String,
    val subtlety: Subtlety,
    val canAppearFrom: Int,
    val mustAppearBy: Int
)

@Serializable
internal enum class Subtlety {
    SUBTLE,     // Blink and you miss it
    MODERATE,   // Noticeable if paying attention
    OBVIOUS     // Clear setup
}

/**
 * Context for the Narrator to maintain story coherence.
 */
@Serializable
internal data class NarratorContext(
    val systemName: String,
    val systemPersonality: String,
    val centralMystery: String,
    val primaryThreat: String,
    val thematicCore: String,
    val activeThreads: List<String>,
    val upcomingBeats: List<String>,
    val currentForeshadowing: List<String>
)

// ========================
// JSON Parsing Classes
// ========================

@Serializable
private data class SystemDefinitionJson(
    val systemName: String = "The System",
    val systemPersonality: String = "Cold and calculating",
    val uniqueMechanic: String = "Standard integration",
    val centralMystery: String = "Unknown",
    val primaryThreat: String = "Survival",
    val thematicCore: String = "Power",
    val worldState: String = "Chaos",
    val keyFactions: List<FactionJson> = emptyList(),
    val narrativeHooks: List<String> = emptyList()
) {
    fun toSystemDefinition(systemType: SystemType) = SystemDefinition(
        systemType = systemType,
        systemName = systemName,
        systemPersonality = systemPersonality,
        uniqueMechanic = uniqueMechanic,
        centralMystery = centralMystery,
        primaryThreat = primaryThreat,
        thematicCore = thematicCore,
        worldState = worldState,
        keyFactions = keyFactions.map { FactionSummary(it.name, it.role, it.stance) },
        narrativeHooks = narrativeHooks
    )
}

@Serializable
private data class FactionJson(
    val name: String = "Unknown",
    val role: String = "Unknown",
    val stance: String = "neutral"
)

@Serializable
private data class ProposalJson(
    val threads: List<ThreadJson> = emptyList(),
    val confidence: Float = 0.5f,
    val reasoning: String = ""
)

@Serializable
private data class ThreadJson(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "MAIN_STORY",
    val priority: String = "MEDIUM",
    val beats: List<BeatJson> = emptyList()
)

@Serializable
private data class BeatJson(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val beatType: String = "ESCALATION",
    val triggerLevel: Int = 1,
    val foreshadowing: String? = null,
    val consequences: String? = null
)
