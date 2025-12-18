package com.rpgenerator.cli

import com.rpgenerator.core.api.Game
import com.rpgenerator.core.api.RPGClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Web-based debug dashboard with embedded terminal and debug panels.
 */
class DebugWebServer(
    private val client: RPGClient,
    private val port: Int = 8080
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var currentGame: Game? = null

    // Terminal I/O - queue-based with tight polling
    private val terminalOutputQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val terminalInput = Channel<String>(Channel.BUFFERED)
    @Volatile private var webSocketConnected = false

    // Terminal log file for debugging
    private val terminalLogFile = java.io.File(".rpgenerator-debug/terminal.log").also {
        it.parentFile?.mkdirs()
        it.writeText("=== RPGenerator Terminal Log ===\nStarted: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n\n")
    }

    // Pre-game setup logs (before game is created)
    private val setupLogs = mutableListOf<LogEntry>()

    // Agent conversation tracking
    private val agentConversations = mutableMapOf<String, AgentConversation>()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun start(game: Game? = null) {
        currentGame = game

        server = embeddedServer(Netty, port = port) {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                // Main dashboard page
                get("/") {
                    call.respondText(buildDashboardHtml(), ContentType.Text.Html)
                }

                // WebSocket for terminal - only one connection allowed
                var activeSession: WebSocketSession? = null
                val sessionLock = Any()

                webSocket("/ws/terminal") {
                    synchronized(sessionLock) {
                        if (activeSession != null) {
                            return@webSocket
                        }
                        activeSession = this
                    }
                    webSocketConnected = true

                    val outputJob = launch {
                        while (isActive) {
                            val msg = terminalOutputQueue.poll()
                            if (msg != null) {
                                try {
                                    send(Frame.Text(json.encodeToString(TerminalMessage("output", msg))))
                                } catch (e: Exception) {
                                    return@launch
                                }
                            } else {
                                delay(5)
                            }
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val msg = json.decodeFromString<TerminalMessage>(text)
                                if (msg.type == "input") {
                                    terminalInput.send(msg.data)
                                }
                            }
                        }
                    } finally {
                        outputJob.cancel()
                        synchronized(sessionLock) {
                            if (activeSession == this) {
                                activeSession = null
                            }
                        }
                        webSocketConnected = false
                    }
                }

                // API endpoints
                get("/api/logs") {
                    val query = call.request.queryParameters["query"] ?: ""
                    val category = call.request.queryParameters["category"] ?: ""
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val logs = getFilteredLogs(query, category, limit)
                    call.respondText(json.encodeToString(logs), ContentType.Application.Json)
                }

                get("/api/tables") {
                    val tables = getTableList()
                    call.respondText(json.encodeToString(tables), ContentType.Application.Json)
                }

                get("/api/table/{name}") {
                    val tableName = call.parameters["name"] ?: ""
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val data = getTableData(tableName, limit)
                    call.respondText(json.encodeToString(data), ContentType.Application.Json)
                }

                post("/api/query") {
                    val body = call.receiveText()
                    val result = executeQuery(body)
                    call.respondText(json.encodeToString(result), ContentType.Application.Json)
                }

                get("/api/plot") {
                    val plotData = getPlotData()
                    call.respondText(json.encodeToString(plotData), ContentType.Application.Json)
                }

                get("/api/state") {
                    val state = getGameState()
                    call.respondText(state, ContentType.Application.Json)
                }

                get("/api/agents") {
                    val agents = getAgentData()
                    call.respondText(json.encodeToString(agents), ContentType.Application.Json)
                }

                get("/api/character") {
                    val character = getCharacterData()
                    call.respondText(json.encodeToString(character), ContentType.Application.Json)
                }

                // Text-to-Speech endpoint using Edge TTS
                post("/api/tts") {
                    val request = try {
                        json.decodeFromString<TTSRequest>(call.receiveText())
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid request")
                        return@post
                    }

                    val audioBytes = generateTTS(request.text, request.voice ?: "en-US-GuyNeural")
                    if (audioBytes != null) {
                        call.respondBytes(audioBytes, ContentType.Audio.MPEG)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "TTS generation failed")
                    }
                }

                // Get available TTS voices
                get("/api/tts/voices") {
                    val voices = listOf(
                        TTSVoice("en-US-GuyNeural", "Guy (US Male)", "en-US"),
                        TTSVoice("en-US-JennyNeural", "Jenny (US Female)", "en-US"),
                        TTSVoice("en-US-AriaNeural", "Aria (US Female)", "en-US"),
                        TTSVoice("en-US-DavisNeural", "Davis (US Male)", "en-US"),
                        TTSVoice("en-GB-RyanNeural", "Ryan (UK Male)", "en-GB"),
                        TTSVoice("en-GB-SoniaNeural", "Sonia (UK Female)", "en-GB"),
                        TTSVoice("en-AU-WilliamNeural", "William (AU Male)", "en-AU")
                    )
                    call.respondText(json.encodeToString(voices), ContentType.Application.Json)
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server?.start(wait = false)
            } catch (e: Exception) {
                println("Failed to start web server: ${e.message}")
            }
        }
    }

    /**
     * Write output to the web terminal.
     */
    fun writeToTerminal(text: String) {
        try {
            terminalLogFile.appendText(text)
        } catch (e: Exception) { /* ignore */ }
        terminalOutputQueue.offer(text)
    }

    /**
     * Read input from the web terminal (suspending).
     */
    suspend fun readFromTerminal(): String {
        val input = terminalInput.receive()
        // Log user input to file
        try {
            terminalLogFile.appendText("[USER INPUT]: $input\n")
        } catch (e: Exception) { /* ignore */ }
        return input
    }

    /**
     * Check if web terminal is connected.
     */
    fun isTerminalConnected(): Boolean = webSocketConnected

    /**
     * Log a setup event (before game is created).
     */
    fun logSetupEvent(
        eventType: String,
        category: String,
        text: String,
        importance: String = "NORMAL"
    ) {
        setupLogs.add(LogEntry(
            id = setupLogs.size + 1,
            timestamp = System.currentTimeMillis() / 1000,
            eventType = eventType,
            category = category,
            importance = importance,
            text = text,
            npcId = null,
            locationId = null,
            questId = null
        ))
    }

    /**
     * Clear setup logs (call when game starts).
     */
    fun clearSetupLogs() {
        setupLogs.clear()
    }

    /**
     * Log an agent conversation start.
     */
    fun logAgentStart(agentId: String, agentType: String, systemPrompt: String) {
        agentConversations[agentId] = AgentConversation(
            id = agentId,
            agentType = agentType,
            systemPrompt = systemPrompt,
            messages = mutableListOf(),
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * Update an agent's display name (e.g., after NPC name is generated).
     */
    fun updateAgentName(agentId: String, newName: String) {
        val existing = agentConversations[agentId] ?: return
        agentConversations[agentId] = existing.copy(agentType = newName)
    }

    /**
     * Log a message to/from an agent.
     */
    fun logAgentMessage(agentId: String, role: String, content: String) {
        agentConversations[agentId]?.messages?.add(
            AgentMessage(
                role = role,
                content = content,
                timestamp = System.currentTimeMillis()
            )
        )
        // Auto-update debug file
        writeAgentContextFile()
    }

    /**
     * Write agent context to a file for Claude Code to read.
     */
    private fun writeAgentContextFile() {
        try {
            val debugDir = java.io.File(".rpgenerator-debug")
            if (!debugDir.exists()) debugDir.mkdirs()

            val file = java.io.File(debugDir, "agents.md")
            file.writeText(buildAgentContextMarkdown())
        } catch (e: Exception) {
            // Ignore file write errors
        }
    }

    /**
     * Build markdown-formatted agent context for debugging.
     */
    private fun buildAgentContextMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# RPGenerator Agent Debug Context")
        sb.appendLine()
        sb.appendLine("*Auto-updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}*")
        sb.appendLine()

        if (agentConversations.isEmpty()) {
            sb.appendLine("No agent conversations yet.")
            return sb.toString()
        }

        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("| Agent | Type | Messages |")
        sb.appendLine("|-------|------|----------|")
        agentConversations.values.sortedByDescending { it.startTime }.forEach { agent ->
            sb.appendLine("| ${agent.id} | ${agent.agentType} | ${agent.messages.size} |")
        }
        sb.appendLine()

        sb.appendLine("## Agent Conversations")
        sb.appendLine()

        agentConversations.values.sortedByDescending { it.startTime }.forEach { agent ->
            sb.appendLine("### ${agent.agentType} (${agent.id})")
            sb.appendLine()
            sb.appendLine("**System Prompt:**")
            sb.appendLine("```")
            sb.appendLine(agent.systemPrompt)
            sb.appendLine("```")
            sb.appendLine()

            if (agent.messages.isNotEmpty()) {
                sb.appendLine("**Conversation:**")
                sb.appendLine()
                agent.messages.forEach { msg ->
                    val roleLabel = if (msg.role == "user") "→ USER" else "← ASSISTANT"
                    sb.appendLine("$roleLabel:")
                    sb.appendLine("```")
                    sb.appendLine(msg.content)
                    sb.appendLine("```")
                    sb.appendLine()
                }
            }
            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Get agent conversation data.
     */
    private fun getAgentData(): AgentsResponse {
        val conversations = agentConversations.values.sortedByDescending { it.startTime }
        return AgentsResponse(
            agents = conversations.map { conv ->
                AgentInfo(
                    id = conv.id,
                    agentType = conv.agentType,
                    systemPrompt = conv.systemPrompt.take(500) + if (conv.systemPrompt.length > 500) "..." else "",
                    messageCount = conv.messages.size,
                    startTime = conv.startTime,
                    messages = conv.messages.map { msg ->
                        AgentMessageInfo(
                            role = msg.role,
                            content = msg.content,
                            timestamp = msg.timestamp
                        )
                    }
                )
            },
            totalAgents = conversations.size
        )
    }

    private fun getFilteredLogs(query: String, category: String, limit: Int): LogsResponse {
        val game = currentGame
        val logs = mutableListOf<LogEntry>()

        // Always include setup logs first (most recent setup events)
        setupLogs.filter { log ->
            val matchesQuery = query.isEmpty() ||
                log.text.contains(query, ignoreCase = true) ||
                log.eventType.contains(query, ignoreCase = true)
            val matchesCategory = category.isEmpty() || log.category == category
            matchesQuery && matchesCategory
        }.forEach { logs.add(it) }

        // If game is active, also include game logs
        if (game != null) {
            try {
                val events = client.getRecentEvents(game, limit)
                events.forEach { event ->
                    val matchesQuery = query.isEmpty() ||
                        event.searchableText.contains(query, ignoreCase = true) ||
                        event.eventType.contains(query, ignoreCase = true)
                    val matchesCategory = category.isEmpty() || event.category == category

                    if (matchesQuery && matchesCategory) {
                        logs.add(LogEntry(
                            id = event.id + 10000, // Offset to avoid ID collision with setup logs
                            timestamp = event.timestamp,
                            eventType = event.eventType,
                            category = event.category,
                            importance = event.importance,
                            text = event.searchableText,
                            npcId = event.npcId,
                            locationId = event.locationId,
                            questId = event.questId
                        ))
                    }
                }
            } catch (e: Exception) {
                logs.add(LogEntry(
                    id = 0,
                    timestamp = System.currentTimeMillis() / 1000,
                    eventType = "ERROR",
                    category = "SYSTEM",
                    importance = "HIGH",
                    text = "Error fetching game logs: ${e.message}",
                    npcId = null,
                    locationId = null,
                    questId = null
                ))
            }
        }

        // Sort by timestamp descending (most recent first)
        logs.sortByDescending { it.timestamp }

        return if (logs.isEmpty()) {
            LogsResponse(listOf(LogEntry(
                id = 0,
                timestamp = System.currentTimeMillis() / 1000,
                eventType = "INFO",
                category = "SYSTEM",
                importance = "NORMAL",
                text = "No logs yet. Events will appear as you interact with the game.",
                npcId = null,
                locationId = null,
                questId = null
            )), 1)
        } else {
            LogsResponse(logs.take(limit), logs.size)
        }
    }

    private fun getTableList(): List<TableInfo> {
        return listOf(
            TableInfo("Game", "Game metadata and saves"),
            TableInfo("GameState", "Serialized game state"),
            TableInfo("GameEventLog", "Event history"),
            TableInfo("NPC", "Non-player characters"),
            TableInfo("Quest", "Active and completed quests"),
            TableInfo("CustomLocation", "Player-discovered locations"),
            TableInfo("PlotThread", "Story plot threads"),
            TableInfo("PlotGraph", "Plot graph structure"),
            TableInfo("PlotNode", "Plot graph nodes"),
            TableInfo("PlotEdge", "Plot graph edges"),
            TableInfo("AgentMemory", "Agent conversation memory"),
            TableInfo("AgentAction", "Agent decision log"),
            TableInfo("AgentProposal", "Agent proposals"),
            TableInfo("PlanningSession", "Planning sessions"),
            TableInfo("AgentConflict", "Agent conflicts"),
            TableInfo("DiscussionRound", "Discussion rounds"),
            TableInfo("DiscussionConsensus", "Consensus results")
        )
    }

    private fun getTableData(tableName: String, limit: Int): TableDataResponse {
        val game = currentGame

        val validTables = setOf(
            "Game", "GameState", "GameEventLog", "NPC", "Quest",
            "CustomLocation", "PlotThread", "PlotGraph", "PlotNode",
            "PlotEdge", "AgentMemory", "AgentAction", "AgentProposal",
            "PlanningSession", "AgentConflict", "DiscussionRound",
            "DiscussionConsensus", "AgentConsolidation", "ConsensusResult",
            "DiscussionHistory", "AgentArgument", "TrajectoryAnalysis"
        )

        if (tableName !in validTables) {
            return TableDataResponse(emptyList(), emptyList(), "Invalid table name")
        }

        return try {
            val result = client.getTableData(tableName, game?.id, limit)
            if (result.rows.isEmpty()) {
                TableDataResponse(result.columns.ifEmpty { listOf("info") },
                    listOf(listOf("No data in table" + (if (game != null) " for current game" else ""))),
                    null)
            } else {
                TableDataResponse(result.columns, result.rows, null)
            }
        } catch (e: Exception) {
            TableDataResponse(listOf("error"), listOf(listOf(e.message ?: "Unknown error")), null)
        }
    }

    private fun executeQuery(sql: String): QueryResponse {
        val trimmedSql = sql.trim().uppercase()
        if (!trimmedSql.startsWith("SELECT")) {
            return QueryResponse(false, null, "Only SELECT queries are allowed")
        }

        return try {
            val result = client.executeRawQuery(sql)
            if (result.rows.isEmpty()) {
                QueryResponse(true, QueryResult(listOf("info"), listOf(listOf("Query returned no results"))), null)
            } else {
                QueryResponse(true, QueryResult(result.columns, result.rows), null)
            }
        } catch (e: Exception) {
            QueryResponse(false, null, e.message ?: "Query failed")
        }
    }

    private fun getPlotData(): PlotDataResponse {
        val game = currentGame ?: return PlotDataResponse(
            threads = emptyList(),
            nodes = emptyList(),
            edges = emptyList(),
            activeNodes = emptyList(),
            error = "No active game"
        )

        return try {
            val threads = client.getPlotThreads(game)
            val nodes = client.getPlotNodes(game)
            val edges = client.getPlotEdges(game)

            PlotDataResponse(
                threads = threads.map { PlotThreadInfo(it.id, it.category, it.priority, it.status, it.threadJson) },
                nodes = nodes.map { PlotNodeInfo(it.id, it.threadId, it.tier, it.sequence, it.beatType, it.triggered, it.completed, it.abandoned, it.nodeJson) },
                edges = edges.map { PlotEdgeInfo(it.id, it.fromNodeId, it.toNodeId, it.edgeType, it.weight, it.disabled) },
                activeNodes = nodes.filter { it.triggered && !it.completed && !it.abandoned }.map { it.id },
                error = null
            )
        } catch (e: Exception) {
            PlotDataResponse(emptyList(), emptyList(), emptyList(), emptyList(), e.message)
        }
    }

    private fun getGameState(): String {
        val game = currentGame ?: return """{"error": "No active game"}"""

        return try {
            val state = kotlinx.coroutines.runBlocking { game.getState() }
            json.encodeToString(GameStateInfo(
                playerName = state.playerStats.name,
                level = state.playerStats.level,
                health = state.playerStats.health,
                maxHealth = state.playerStats.maxHealth,
                experience = state.playerStats.experience,
                location = state.location,
                stats = state.playerStats.stats
            ))
        } catch (e: Exception) {
            """{"error": "${e.message}"}"""
        }
    }

    private fun getCharacterData(): CharacterSheetData {
        val game = currentGame ?: return CharacterSheetData(
            error = "No active game"
        )

        return try {
            val state = kotlinx.coroutines.runBlocking { game.getState() }
            CharacterSheetData(
                name = state.playerStats.name,
                level = state.playerStats.level,
                health = state.playerStats.health,
                maxHealth = state.playerStats.maxHealth,
                energy = state.playerStats.energy,
                maxEnergy = state.playerStats.maxEnergy,
                experience = state.playerStats.experience,
                experienceToNext = state.playerStats.experienceToNextLevel,
                location = state.location,
                currentScene = state.currentScene,
                backstory = state.playerStats.backstory,
                stats = state.playerStats.stats,
                inventory = state.inventory.map { InventoryItem(it.name, it.quantity, it.rarity.name, it.description) },
                activeQuests = state.activeQuests.map { QuestInfo(it.name, it.description, it.status.name, it.objectives.map { obj -> QuestObjectiveInfo(obj.description, obj.completed) }) },
                npcsAtLocation = state.npcsAtLocation.map { NPCDisplayInfo(it.id, it.name, it.archetype, it.disposition, it.description) },
                error = null
            )
        } catch (e: Exception) {
            CharacterSheetData(error = e.message ?: "Unknown error")
        }
    }

    private fun buildDashboardHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RPGenerator Debug Dashboard</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css">
    <script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.min.js"></script>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
            background: #0d1117;
            color: #c9d1d9;
            height: 100vh;
            overflow: hidden;
        }

        .header {
            background: #161b22;
            border-bottom: 1px solid #30363d;
            padding: 12px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            height: 50px;
        }

        .header h1 {
            color: #58a6ff;
            font-size: 16px;
            font-weight: 600;
        }

        .game-status {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .status-badge {
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 11px;
            font-weight: 500;
        }

        .status-badge.active { background: #238636; color: #fff; }
        .status-badge.inactive { background: #6e7681; color: #fff; }

        .main-container {
            display: flex;
            height: calc(100vh - 50px);
        }

        /* Terminal Panel - Left Side */
        .terminal-panel {
            width: 55%;
            min-width: 400px;
            background: #0d1117;
            border-right: 1px solid #30363d;
            display: flex;
            flex-direction: column;
        }

        .terminal-header {
            padding: 10px 16px;
            background: #161b22;
            border-bottom: 1px solid #30363d;
            font-size: 13px;
            font-weight: 600;
            color: #8b949e;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .terminal-header .dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #6e7681;
        }

        .terminal-header .dot.connected { background: #3fb950; }

        #terminal-container {
            flex: 1;
            padding: 8px;
            overflow: hidden;
        }

        /* Debug Panel - Right Side */
        .debug-panel {
            flex: 1;
            display: flex;
            flex-direction: column;
            min-width: 400px;
        }

        .tabs {
            display: flex;
            background: #161b22;
            border-bottom: 1px solid #30363d;
            padding: 0 16px;
        }

        .tab {
            padding: 10px 16px;
            cursor: pointer;
            border-bottom: 2px solid transparent;
            color: #8b949e;
            transition: all 0.2s;
            font-size: 13px;
        }

        .tab:hover { color: #c9d1d9; }
        .tab.active {
            color: #58a6ff;
            border-bottom-color: #58a6ff;
        }

        .tab-content {
            display: none;
            flex: 1;
            overflow: auto;
            padding: 16px;
        }

        .tab-content.active { display: block; }

        /* Filters */
        .filters {
            display: flex;
            gap: 8px;
            margin-bottom: 12px;
            flex-wrap: wrap;
        }

        .filter-input {
            background: #21262d;
            border: 1px solid #30363d;
            border-radius: 4px;
            padding: 6px 10px;
            color: #c9d1d9;
            font-size: 12px;
            font-family: inherit;
        }

        .filter-input:focus {
            outline: none;
            border-color: #58a6ff;
        }

        .btn {
            background: #238636;
            color: #fff;
            border: none;
            border-radius: 4px;
            padding: 6px 12px;
            cursor: pointer;
            font-size: 12px;
            font-family: inherit;
        }

        .btn:hover { background: #2ea043; }
        .btn.secondary { background: #21262d; border: 1px solid #30363d; }

        /* Log entries */
        .log-entry {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            padding: 10px 12px;
            margin-bottom: 6px;
            font-size: 12px;
        }

        .log-header {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 6px;
        }

        .log-type {
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 10px;
            font-weight: 600;
            text-transform: uppercase;
        }

        .log-type.NARRATIVE { background: #1f6feb33; color: #58a6ff; }
        .log-type.COMBAT { background: #da363333; color: #f85149; }
        .log-type.SYSTEM { background: #a371f733; color: #a371f7; }
        .log-type.DIALOGUE { background: #23863633; color: #3fb950; }
        .log-type.EXPLORATION { background: #d2992233; color: #d29922; }
        .log-type.SETUP { background: #388bfd33; color: #388bfd; }
        .log-type.AI_CALL { background: #bc8cff33; color: #bc8cff; }

        .log-time { color: #6e7681; font-size: 10px; }
        .log-text { color: #c9d1d9; line-height: 1.5; word-break: break-word; }

        /* Agents Tab */
        .agents-layout {
            display: grid;
            grid-template-columns: 250px 1fr;
            gap: 16px;
            height: 100%;
        }

        .agent-list {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            overflow: hidden;
        }

        .agent-item {
            padding: 10px 12px;
            cursor: pointer;
            border-bottom: 1px solid #21262d;
            font-size: 12px;
        }

        .agent-item:hover { background: #21262d; }
        .agent-item.active { background: #1f6feb33; color: #58a6ff; }

        .agent-item-type {
            font-weight: 600;
            margin-bottom: 4px;
        }

        .agent-item-meta {
            font-size: 10px;
            color: #6e7681;
        }

        .agent-detail {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            overflow: auto;
            padding: 12px;
        }

        .agent-system-prompt {
            background: #21262d;
            padding: 12px;
            border-radius: 4px;
            margin-bottom: 16px;
            font-size: 11px;
            white-space: pre-wrap;
            word-break: break-word;
        }

        .agent-system-prompt-label {
            font-weight: 600;
            color: #a371f7;
            margin-bottom: 8px;
            font-size: 12px;
        }

        .agent-message {
            margin-bottom: 12px;
            padding: 10px;
            border-radius: 4px;
            font-size: 12px;
        }

        .agent-message.user {
            background: #1f6feb33;
            border-left: 3px solid #58a6ff;
        }

        .agent-message.assistant {
            background: #23863633;
            border-left: 3px solid #3fb950;
        }

        .agent-message-role {
            font-weight: 600;
            margin-bottom: 6px;
            font-size: 11px;
            text-transform: uppercase;
        }

        .agent-message-content {
            word-break: break-word;
            line-height: 1.5;
        }

        .agent-message-time {
            font-size: 10px;
            color: #6e7681;
            margin-top: 6px;
        }

        /* DB Tab */
        .db-layout {
            display: grid;
            grid-template-columns: 200px 1fr;
            gap: 16px;
            height: 100%;
        }

        .table-list {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            overflow: hidden;
        }

        .table-list-header {
            padding: 10px 12px;
            background: #21262d;
            font-weight: 600;
            font-size: 12px;
            border-bottom: 1px solid #30363d;
        }

        .table-item {
            padding: 8px 12px;
            cursor: pointer;
            border-bottom: 1px solid #21262d;
            font-size: 12px;
        }

        .table-item:hover { background: #21262d; }
        .table-item.active { background: #1f6feb33; color: #58a6ff; }

        .db-content {
            display: flex;
            flex-direction: column;
            gap: 12px;
            overflow: hidden;
        }

        .query-box {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
        }

        .query-header {
            padding: 8px 12px;
            background: #21262d;
            font-size: 12px;
            border-bottom: 1px solid #30363d;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .query-input {
            width: 100%;
            background: #0d1117;
            border: none;
            padding: 10px;
            color: #c9d1d9;
            font-family: inherit;
            font-size: 12px;
            resize: none;
            height: 60px;
        }

        .results-container {
            flex: 1;
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            overflow: auto;
        }

        .results-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 11px;
        }

        .results-table th {
            background: #21262d;
            padding: 8px;
            text-align: left;
            font-weight: 600;
            border-bottom: 1px solid #30363d;
            position: sticky;
            top: 0;
        }

        .results-table td {
            padding: 6px 8px;
            border-bottom: 1px solid #21262d;
            max-width: 200px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        /* Plan Tab */
        .plan-layout {
            display: grid;
            grid-template-columns: 1fr 280px;
            gap: 16px;
            height: 100%;
        }

        .plot-graph {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            padding: 12px;
            overflow: auto;
        }

        .plot-sidebar {
            display: flex;
            flex-direction: column;
            gap: 12px;
            overflow: auto;
        }

        .plot-card {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
        }

        .plot-card-header {
            padding: 10px 12px;
            background: #21262d;
            font-weight: 600;
            font-size: 12px;
            border-bottom: 1px solid #30363d;
        }

        .plot-card-content {
            padding: 12px;
            max-height: 200px;
            overflow-y: auto;
            font-size: 11px;
        }

        .thread-item {
            padding: 6px 10px;
            background: #21262d;
            border-radius: 3px;
            margin-bottom: 6px;
        }

        .priority-high { border-left: 3px solid #f85149; }
        .priority-medium { border-left: 3px solid #d29922; }
        .priority-low { border-left: 3px solid #3fb950; }

        .node-item {
            padding: 6px 8px;
            background: #21262d;
            border-radius: 3px;
            margin-bottom: 4px;
        }

        .node-active { border-left: 3px solid #58a6ff; }
        .node-completed { border-left: 3px solid #3fb950; opacity: 0.7; }
        .node-pending { border-left: 3px solid #6e7681; }

        .empty-state {
            text-align: center;
            padding: 30px;
            color: #6e7681;
            font-size: 12px;
        }

        .json-view {
            background: #0d1117;
            padding: 10px;
            border-radius: 3px;
            font-size: 10px;
            overflow-x: auto;
            white-space: pre-wrap;
            word-break: break-all;
        }

        .loading {
            text-align: center;
            padding: 30px;
            color: #6e7681;
        }

        /* Character Sheet */
        .character-sheet {
            max-width: 800px;
        }

        .char-header {
            display: flex;
            align-items: baseline;
            gap: 16px;
            margin-bottom: 16px;
        }

        .char-name {
            font-size: 24px;
            font-weight: 700;
            color: #58a6ff;
        }

        .char-level {
            font-size: 14px;
            color: #8b949e;
            background: #21262d;
            padding: 4px 12px;
            border-radius: 12px;
        }

        .char-bars {
            display: flex;
            flex-direction: column;
            gap: 8px;
            margin-bottom: 16px;
        }

        .stat-bar {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .stat-bar-label {
            width: 50px;
            font-size: 11px;
            font-weight: 600;
            color: #8b949e;
        }

        .stat-bar-track {
            flex: 1;
            height: 12px;
            background: #21262d;
            border-radius: 6px;
            overflow: hidden;
        }

        .stat-bar-fill {
            height: 100%;
            border-radius: 6px;
            transition: width 0.3s ease;
        }

        .hp-bar { background: linear-gradient(90deg, #f85149, #da3633); }
        .energy-bar { background: linear-gradient(90deg, #58a6ff, #388bfd); }
        .xp-bar { background: linear-gradient(90deg, #3fb950, #238636); }

        .stat-bar-text {
            width: 70px;
            font-size: 11px;
            color: #6e7681;
            text-align: right;
        }

        .char-location {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            padding: 10px 14px;
            margin-bottom: 16px;
            font-size: 12px;
            color: #8b949e;
        }

        .char-location::before {
            content: '📍 ';
        }

        .char-backstory {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            padding: 12px 14px;
            margin-bottom: 16px;
            font-size: 12px;
            color: #c9d1d9;
            line-height: 1.5;
            white-space: pre-wrap;
            max-height: 200px;
            overflow-y: auto;
        }

        .char-backstory:empty {
            display: none;
        }

        .char-backstory-label {
            font-weight: 600;
            color: #8b949e;
            margin-bottom: 8px;
            font-size: 11px;
            text-transform: uppercase;
        }

        .char-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 12px;
        }

        .char-section {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 4px;
            overflow: hidden;
        }

        .char-section-header {
            background: #21262d;
            padding: 8px 12px;
            font-weight: 600;
            font-size: 12px;
            border-bottom: 1px solid #30363d;
        }

        .char-section-content {
            padding: 10px 12px;
            font-size: 12px;
            max-height: 200px;
            overflow-y: auto;
        }

        .stat-row {
            display: flex;
            justify-content: space-between;
            padding: 4px 0;
            border-bottom: 1px solid #21262d;
        }

        .stat-row:last-child { border-bottom: none; }

        .stat-name { color: #8b949e; text-transform: capitalize; }
        .stat-value { font-weight: 600; color: #c9d1d9; }

        .inv-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 6px 0;
            border-bottom: 1px solid #21262d;
        }

        .inv-item:last-child { border-bottom: none; }

        .inv-item-name { color: #c9d1d9; }
        .inv-item-qty { color: #6e7681; font-size: 11px; }

        .rarity-COMMON { color: #8b949e; }
        .rarity-UNCOMMON { color: #3fb950; }
        .rarity-RARE { color: #58a6ff; }
        .rarity-EPIC { color: #a371f7; }
        .rarity-LEGENDARY { color: #d29922; }

        .quest-item {
            padding: 8px 0;
            border-bottom: 1px solid #21262d;
        }

        .quest-item:last-child { border-bottom: none; }

        .quest-name { font-weight: 600; color: #d29922; margin-bottom: 4px; }
        .quest-desc { font-size: 11px; color: #8b949e; margin-bottom: 6px; }

        .quest-obj {
            font-size: 11px;
            padding: 2px 0;
            color: #6e7681;
        }

        .quest-obj.completed { color: #3fb950; text-decoration: line-through; }
        .quest-obj::before { content: '○ '; }
        .quest-obj.completed::before { content: '✓ '; }

        /* NPCs */
        .npc-item {
            padding: 8px 0;
            border-bottom: 1px solid #21262d;
        }
        .npc-item:last-child { border-bottom: none; }
        .npc-name { font-weight: 600; color: #a371f7; margin-bottom: 4px; }
        .npc-archetype { font-size: 11px; color: #8b949e; margin-bottom: 4px; }
        .npc-desc { font-size: 11px; color: #6e7681; font-style: italic; }

        /* Resizer */
        .resizer {
            width: 4px;
            background: #30363d;
            cursor: col-resize;
            transition: background 0.2s;
        }

        .resizer:hover { background: #58a6ff; }

        /* Markdown rendering */
        .md-code-block {
            background: #0d1117;
            border: 1px solid #30363d;
            border-radius: 4px;
            padding: 10px;
            margin: 8px 0;
            overflow-x: auto;
            font-size: 12px;
        }

        .md-inline-code {
            background: #21262d;
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 11px;
        }

        .md-h2 { font-size: 18px; font-weight: 600; margin: 12px 0 8px 0; color: #c9d1d9; }
        .md-h3 { font-size: 15px; font-weight: 600; margin: 10px 0 6px 0; color: #c9d1d9; }
        .md-h4 { font-size: 13px; font-weight: 600; margin: 8px 0 4px 0; color: #c9d1d9; }

        .md-ul {
            margin: 8px 0;
            padding-left: 20px;
            list-style-type: disc;
        }

        .md-li {
            margin: 4px 0;
            display: list-item;
        }

        .agent-message-content strong { color: #58a6ff; }
        .agent-message-content em { color: #a371f7; font-style: italic; }
        .log-text strong { color: #58a6ff; }
        .log-text em { color: #a371f7; font-style: italic; }

        /* TTS Controls */
        .header-controls {
            display: flex;
            align-items: center;
            gap: 20px;
        }

        .tts-controls {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .tts-toggle {
            display: flex;
            align-items: center;
            gap: 6px;
            background: #21262d;
            border: 1px solid #30363d;
            border-radius: 6px;
            padding: 6px 12px;
            cursor: pointer;
            color: #8b949e;
            font-size: 12px;
            font-family: inherit;
            transition: all 0.2s;
        }

        .tts-toggle:hover {
            background: #30363d;
            color: #c9d1d9;
        }

        .tts-toggle.active {
            background: #238636;
            border-color: #238636;
            color: #fff;
        }

        .tts-toggle .tts-icon {
            font-size: 14px;
        }

        .tts-voice-select {
            width: 140px;
            opacity: 0.5;
            transition: opacity 0.2s;
        }

        .tts-voice-select.enabled {
            opacity: 1;
        }

        .tts-speaking {
            animation: pulse 1s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.6; }
        }

        .connection-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #6e7681;
            display: inline-block;
        }

        .connection-dot.connected {
            background: #3fb950;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>RPGenerator Debug Dashboard</h1>
        <div class="header-controls">
            <div class="tts-controls">
                <button class="tts-toggle" id="tts-toggle" onclick="toggleTTS()" title="Toggle Text-to-Speech">
                    <span class="tts-icon">🔇</span>
                    <span class="tts-label">TTS Off</span>
                </button>
                <select class="filter-input tts-voice-select" id="tts-voice" onchange="saveTTSVoice()">
                    <option value="en-US-GuyNeural">Guy (US Male)</option>
                </select>
            </div>
            <div class="game-status">
                <span class="connection-dot" id="connection-status" title="Disconnected"></span>
            </div>
        </div>
        <audio id="tts-audio" style="display:none;"></audio>
    </div>

    <div class="main-container">
        <!-- Terminal Panel -->
        <div class="terminal-panel" id="terminal-panel">
            <div class="terminal-header">
                <span class="dot" id="connection-dot"></span>
                <span>Game Terminal</span>
            </div>
            <div id="terminal-container"></div>
        </div>

        <div class="resizer" id="resizer"></div>

        <!-- Debug Panel -->
        <div class="debug-panel">
            <div class="tabs">
                <div class="tab active" data-tab="logs">Logs</div>
                <div class="tab" data-tab="character">Character</div>
                <div class="tab" data-tab="agents">Agents</div>
                <div class="tab" data-tab="db">Database</div>
                <div class="tab" data-tab="plan">Plan</div>
            </div>

            <!-- Logs Tab -->
            <div id="logs" class="tab-content active">
                <div class="filters">
                    <input type="text" class="filter-input" id="log-search" placeholder="Search..." style="flex: 1; min-width: 100px;">
                    <select class="filter-input" id="log-category">
                        <option value="">All</option>
                        <option value="SETUP">Setup</option>
                        <option value="AI_CALL">AI Calls</option>
                        <option value="NARRATIVE">Narrative</option>
                        <option value="COMBAT">Combat</option>
                        <option value="SYSTEM">System</option>
                        <option value="DIALOGUE">Dialogue</option>
                        <option value="EXPLORATION">Exploration</option>
                    </select>
                    <button class="btn" onclick="loadLogs()">Search</button>
                </div>
                <div id="logs-container">
                    <div class="loading">Loading logs...</div>
                </div>
            </div>

            <!-- Character Tab -->
            <div id="character" class="tab-content">
                <div class="character-sheet">
                    <div class="char-header">
                        <div class="char-name" id="char-name">Loading...</div>
                        <div class="char-level" id="char-level"></div>
                    </div>
                    <div class="char-bars">
                        <div class="stat-bar">
                            <div class="stat-bar-label">HP</div>
                            <div class="stat-bar-track">
                                <div class="stat-bar-fill hp-bar" id="hp-bar"></div>
                            </div>
                            <div class="stat-bar-text" id="hp-text">--/--</div>
                        </div>
                        <div class="stat-bar">
                            <div class="stat-bar-label">Energy</div>
                            <div class="stat-bar-track">
                                <div class="stat-bar-fill energy-bar" id="energy-bar"></div>
                            </div>
                            <div class="stat-bar-text" id="energy-text">--/--</div>
                        </div>
                        <div class="stat-bar">
                            <div class="stat-bar-label">XP</div>
                            <div class="stat-bar-track">
                                <div class="stat-bar-fill xp-bar" id="xp-bar"></div>
                            </div>
                            <div class="stat-bar-text" id="xp-text">--/--</div>
                        </div>
                    </div>
                    <div class="char-location" id="char-location"></div>
                    <div class="char-backstory" id="char-backstory"></div>
                    <div class="char-grid">
                        <div class="char-section">
                            <div class="char-section-header">Stats</div>
                            <div class="char-section-content" id="char-stats"></div>
                        </div>
                        <div class="char-section">
                            <div class="char-section-header">Inventory</div>
                            <div class="char-section-content" id="char-inventory"></div>
                        </div>
                        <div class="char-section">
                            <div class="char-section-header">Active Quests</div>
                            <div class="char-section-content" id="char-quests"></div>
                        </div>
                        <div class="char-section">
                            <div class="char-section-header">NPCs at Location</div>
                            <div class="char-section-content" id="char-npcs"></div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Agents Tab -->
            <div id="agents" class="tab-content">
                <div class="agents-layout">
                    <div class="agent-list">
                        <div class="table-list-header">AI Agents</div>
                        <div id="agent-list-items"></div>
                    </div>
                    <div class="agent-detail">
                        <div id="agent-conversation">
                            <div class="empty-state">Select an agent to view conversation</div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Database Tab -->
            <div id="db" class="tab-content">
                <div class="db-layout">
                    <div class="table-list">
                        <div class="table-list-header">Tables</div>
                        <div id="table-list-items"></div>
                    </div>
                    <div class="db-content">
                        <div class="query-box">
                            <div class="query-header">
                                <span>SQL Query</span>
                                <button class="btn" onclick="executeQuery()">Run</button>
                            </div>
                            <textarea class="query-input" id="sql-query" placeholder="SELECT * FROM Game LIMIT 10;"></textarea>
                        </div>
                        <div class="results-container">
                            <table class="results-table" id="query-results">
                                <thead><tr><th>Select a table</th></tr></thead>
                            </table>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Plan Tab -->
            <div id="plan" class="tab-content">
                <div class="plan-layout">
                    <div class="plot-graph" id="plot-visualization">
                        <div class="loading">Loading plot...</div>
                    </div>
                    <div class="plot-sidebar">
                        <div class="plot-card">
                            <div class="plot-card-header">Plot Threads</div>
                            <div class="plot-card-content" id="active-threads"></div>
                        </div>
                        <div class="plot-card">
                            <div class="plot-card-header">Active Nodes</div>
                            <div class="plot-card-content" id="active-nodes"></div>
                        </div>
                        <div class="plot-card">
                            <div class="plot-card-header">Game State</div>
                            <div class="plot-card-content" id="game-state"></div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script>
        // Terminal setup
        const term = new Terminal({
            theme: {
                background: '#0d1117',
                foreground: '#c9d1d9',
                cursor: '#58a6ff',
                cursorAccent: '#0d1117',
                selection: '#264f78',
                black: '#0d1117',
                red: '#f85149',
                green: '#3fb950',
                yellow: '#d29922',
                blue: '#58a6ff',
                magenta: '#a371f7',
                cyan: '#56d4dd',
                white: '#c9d1d9',
                brightBlack: '#6e7681',
                brightRed: '#f85149',
                brightGreen: '#3fb950',
                brightYellow: '#d29922',
                brightBlue: '#58a6ff',
                brightMagenta: '#a371f7',
                brightCyan: '#56d4dd',
                brightWhite: '#ffffff'
            },
            fontFamily: '"SF Mono", "Consolas", "Monaco", monospace',
            fontSize: 14,
            lineHeight: 1.2,
            cursorBlink: true,
            cursorStyle: 'bar'
        });

        const fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(document.getElementById('terminal-container'));
        fitAddon.fit();

        // WebSocket connection
        let ws = null;
        let inputBuffer = '';
        let connected = false;

        // Write queue for xterm.js
        const writeQueue = [];

        function processWriteQueue() {
            if (writeQueue.length > 0) {
                term.write(writeQueue.shift());
            }
            requestAnimationFrame(processWriteQueue);
        }
        requestAnimationFrame(processWriteQueue);

        function queueWrite(text) {
            writeQueue.push(text);
        }

        // Word wrap text at word boundaries, preserving ANSI escape codes
        function wordWrap(text, maxWidth) {
            if (!text || maxWidth < 10) return text;

            const lines = text.split('\n');
            const result = [];

            for (const line of lines) {
                if (stripAnsi(line).length <= maxWidth) {
                    result.push(line);
                    continue;
                }

                // Split into words, preserving ANSI codes attached to words
                const words = line.split(/( +)/);
                let currentLine = '';
                let currentLength = 0;

                for (const word of words) {
                    const wordLength = stripAnsi(word).length;

                    if (currentLength + wordLength <= maxWidth) {
                        currentLine += word;
                        currentLength += wordLength;
                    } else if (wordLength > maxWidth) {
                        // Word is longer than max width, force break
                        if (currentLine) {
                            result.push(currentLine);
                        }
                        currentLine = word;
                        currentLength = wordLength;
                    } else {
                        // Start new line
                        if (currentLine.trim()) {
                            result.push(currentLine);
                        }
                        currentLine = word.trimStart();
                        currentLength = stripAnsi(currentLine).length;
                    }
                }

                if (currentLine.trim()) {
                    result.push(currentLine);
                }
            }

            return result.join('\n');
        }

        // Strip ANSI escape codes to get actual text length
        function stripAnsi(str) {
            return str.replace(/\x1b\[[0-9;]*m/g, '');
        }

        // Convert markdown to ANSI escape codes for terminal display
        function markdownToAnsi(text) {
            if (!text) return '';
            let result = text;

            // Code blocks (```...```) - cyan background effect
            result = result.replace(/```(\w*)\n?([\s\S]*?)```/g, '\x1b[48;5;236m\x1b[36m$2\x1b[0m');

            // Inline code (`...`) - cyan
            result = result.replace(/`([^`]+)`/g, '\x1b[36m$1\x1b[0m');

            // Bold (**...**) - bright white bold
            result = result.replace(/\*\*([^*]+)\*\*/g, '\x1b[1m\x1b[97m$1\x1b[0m');

            // Italic (*...*) - magenta italic
            result = result.replace(/\*([^*]+)\*/g, '\x1b[3m\x1b[35m$1\x1b[0m');

            // Headers at start of line
            result = result.replace(/^### (.+)$/gm, '\x1b[1m\x1b[33m$1\x1b[0m');
            result = result.replace(/^## (.+)$/gm, '\x1b[1m\x1b[33m$1\x1b[0m');
            result = result.replace(/^# (.+)$/gm, '\x1b[1m\x1b[93m$1\x1b[0m');

            // Bullet points - green bullet
            result = result.replace(/^- (.+)$/gm, '\x1b[32m•\x1b[0m $1');
            result = result.replace(/^\* (.+)$/gm, '\x1b[32m•\x1b[0m $1');

            return result;
        }

        function connectWebSocket() {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            ws = new WebSocket(protocol + '//' + window.location.host + '/ws/terminal');

            ws.onopen = () => {
                connected = true;
                document.getElementById('connection-dot').classList.add('connected');
                document.getElementById('connection-status').classList.add('connected');
                document.getElementById('connection-status').title = 'Connected';
                queueWrite('\r\n\x1b[32m[Connected to game server]\x1b[0m\r\n\r\n');
            };

            ws.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    if (msg.type === 'output') {
                        const formatted = markdownToAnsi(msg.data);
                        const wrapped = wordWrap(formatted, term.cols - 2);
                        queueWrite(wrapped.replace(/\n/g, '\r\n'));

                        // Send narrative text to TTS
                        speakText(msg.data);
                    }
                } catch (e) {
                    queueWrite(event.data);
                }
            };

            ws.onclose = () => {
                connected = false;
                document.getElementById('connection-dot').classList.remove('connected');
                document.getElementById('connection-status').classList.remove('connected');
                document.getElementById('connection-status').title = 'Disconnected';
                queueWrite('\r\n\x1b[31m[Disconnected from server]\x1b[0m\r\n');
                setTimeout(connectWebSocket, 3000);
            };

            ws.onerror = () => {
                ws.close();
            };
        }

        // Terminal input handling
        term.onData((data) => {
            if (!connected) return;

            // Handle special keys
            if (data === '\r') { // Enter
                // Stop TTS when user sends input
                stopTTS();

                ws.send(JSON.stringify({ type: 'input', data: inputBuffer }));
                term.write('\r\n');
                inputBuffer = '';
            } else if (data === '\x7f') { // Backspace
                if (inputBuffer.length > 0) {
                    inputBuffer = inputBuffer.slice(0, -1);
                    term.write('\b \b');
                }
            } else if (data >= ' ' || data === '\t') { // Printable chars
                inputBuffer += data;
                term.write(data);
            }
        });

        // Tab switching
        document.querySelectorAll('.tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                tab.classList.add('active');
                document.getElementById(tab.dataset.tab).classList.add('active');

                if (tab.dataset.tab === 'logs') loadLogs();
                if (tab.dataset.tab === 'character') loadCharacter();
                if (tab.dataset.tab === 'agents') loadAgents();
                if (tab.dataset.tab === 'db') loadTables();
                if (tab.dataset.tab === 'plan') loadPlotData();
            });
        });

        // Resizer
        const resizer = document.getElementById('resizer');
        const terminalPanel = document.getElementById('terminal-panel');
        let isResizing = false;

        resizer.addEventListener('mousedown', (e) => {
            isResizing = true;
            document.body.style.cursor = 'col-resize';
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            const newWidth = e.clientX;
            if (newWidth > 300 && newWidth < window.innerWidth - 400) {
                terminalPanel.style.width = newWidth + 'px';
                fitAddon.fit();
            }
        });

        document.addEventListener('mouseup', () => {
            isResizing = false;
            document.body.style.cursor = '';
        });

        // Window resize
        window.addEventListener('resize', () => fitAddon.fit());

        // API functions
        async function loadLogs() {
            const query = document.getElementById('log-search').value;
            const category = document.getElementById('log-category').value;

            try {
                const res = await fetch('/api/logs?query=' + encodeURIComponent(query) + '&category=' + encodeURIComponent(category) + '&limit=100');
                const data = await res.json();

                if (data.logs.length === 0) {
                    document.getElementById('logs-container').innerHTML = '<div class="empty-state">No logs found</div>';
                    return;
                }

                let html = '';
                data.logs.forEach(log => {
                    const time = new Date(log.timestamp * 1000).toLocaleTimeString();
                    html += '<div class="log-entry"><div class="log-header">' +
                        '<span class="log-type ' + log.eventType + '">' + log.eventType + '</span>' +
                        '<span class="log-time">' + time + '</span></div>' +
                        '<div class="log-text">' + renderMarkdown(log.text || log.eventType) + '</div></div>';
                });
                document.getElementById('logs-container').innerHTML = html;
            } catch (e) {
                document.getElementById('logs-container').innerHTML = '<div class="empty-state">Error: ' + e.message + '</div>';
            }
        }

        async function loadCharacter() {
            try {
                const res = await fetch('/api/character');
                const data = await res.json();

                if (data.error) {
                    document.getElementById('char-name').textContent = 'No active game';
                    document.getElementById('char-level').textContent = '';
                    return;
                }

                // Header
                document.getElementById('char-name').textContent = data.name || 'Unknown';
                document.getElementById('char-level').textContent = 'Level ' + (data.level || 1);

                // HP bar
                const hpPct = data.maxHealth ? (data.health / data.maxHealth * 100) : 0;
                document.getElementById('hp-bar').style.width = hpPct + '%';
                document.getElementById('hp-text').textContent = (data.health || 0) + '/' + (data.maxHealth || 0);

                // Energy bar
                const energyPct = data.maxEnergy ? (data.energy / data.maxEnergy * 100) : 0;
                document.getElementById('energy-bar').style.width = energyPct + '%';
                document.getElementById('energy-text').textContent = (data.energy || 0) + '/' + (data.maxEnergy || 0);

                // XP bar
                const xpPct = data.experienceToNext ? (data.experience / data.experienceToNext * 100) : 0;
                document.getElementById('xp-bar').style.width = Math.min(xpPct, 100) + '%';
                document.getElementById('xp-text').textContent = (data.experience || 0) + '/' + (data.experienceToNext || 100);

                // Location
                document.getElementById('char-location').textContent = data.location || 'Unknown Location';

                // Backstory
                const backstoryEl = document.getElementById('char-backstory');
                if (data.backstory && data.backstory.trim()) {
                    backstoryEl.innerHTML = '<div class="char-backstory-label">Backstory</div>' + escapeHtml(data.backstory);
                } else {
                    backstoryEl.innerHTML = '';
                }

                // Stats
                let statsHtml = '';
                if (data.stats) {
                    Object.entries(data.stats).forEach(([name, value]) => {
                        statsHtml += '<div class="stat-row"><span class="stat-name">' + escapeHtml(name) + '</span><span class="stat-value">' + value + '</span></div>';
                    });
                }
                document.getElementById('char-stats').innerHTML = statsHtml || '<div class="empty-state">No stats</div>';

                // Inventory
                let invHtml = '';
                if (data.inventory && data.inventory.length > 0) {
                    data.inventory.forEach(item => {
                        invHtml += '<div class="inv-item"><span class="inv-item-name rarity-' + item.rarity + '">' + escapeHtml(item.name) + '</span><span class="inv-item-qty">x' + item.quantity + '</span></div>';
                    });
                }
                document.getElementById('char-inventory').innerHTML = invHtml || '<div class="empty-state">Empty</div>';

                // Quests
                let questsHtml = '';
                if (data.activeQuests && data.activeQuests.length > 0) {
                    data.activeQuests.forEach(quest => {
                        questsHtml += '<div class="quest-item"><div class="quest-name">' + escapeHtml(quest.name) + '</div>';
                        questsHtml += '<div class="quest-desc">' + escapeHtml(quest.description) + '</div>';
                        if (quest.objectives) {
                            quest.objectives.forEach(obj => {
                                const completedClass = obj.completed ? ' completed' : '';
                                questsHtml += '<div class="quest-obj' + completedClass + '">' + escapeHtml(obj.description) + '</div>';
                            });
                        }
                        questsHtml += '</div>';
                    });
                }
                document.getElementById('char-quests').innerHTML = questsHtml || '<div class="empty-state">No active quests</div>';

                // NPCs
                let npcsHtml = '';
                if (data.npcsAtLocation && data.npcsAtLocation.length > 0) {
                    data.npcsAtLocation.forEach(npc => {
                        npcsHtml += '<div class="npc-item">';
                        npcsHtml += '<div class="npc-name">' + escapeHtml(npc.name) + '</div>';
                        npcsHtml += '<div class="npc-archetype">' + escapeHtml(npc.archetype) + ' • ' + escapeHtml(npc.disposition) + '</div>';
                        if (npc.description) {
                            npcsHtml += '<div class="npc-desc">' + escapeHtml(npc.description) + '</div>';
                        }
                        npcsHtml += '</div>';
                    });
                }
                document.getElementById('char-npcs').innerHTML = npcsHtml || '<div class="empty-state">No NPCs here</div>';
            } catch (e) {
                document.getElementById('char-name').textContent = 'Error loading character';
            }
        }

        async function loadTables() {
            try {
                const res = await fetch('/api/tables');
                const tables = await res.json();

                let html = '';
                tables.forEach(t => {
                    html += '<div class="table-item" onclick="selectTable(\'' + t.name + '\')">' + t.name + '</div>';
                });
                document.getElementById('table-list-items').innerHTML = html;
            } catch (e) {}
        }

        async function selectTable(name) {
            document.querySelectorAll('.table-item').forEach(i => i.classList.remove('active'));
            event.target.classList.add('active');
            document.getElementById('sql-query').value = 'SELECT * FROM ' + name + ' LIMIT 50;';

            try {
                const res = await fetch('/api/table/' + name);
                const data = await res.json();
                renderResults(data.columns, data.rows);
            } catch (e) {}
        }

        async function executeQuery() {
            const sql = document.getElementById('sql-query').value;
            try {
                const res = await fetch('/api/query', { method: 'POST', body: sql });
                const data = await res.json();
                if (data.success) renderResults(data.result.columns, data.result.rows);
                else document.getElementById('query-results').innerHTML = '<thead><tr><th>Error: ' + data.error + '</th></tr></thead>';
            } catch (e) {}
        }

        function renderResults(columns, rows) {
            let html = '<thead><tr>';
            columns.forEach(c => html += '<th>' + escapeHtml(c) + '</th>');
            html += '</tr></thead><tbody>';
            rows.forEach(row => {
                html += '<tr>';
                row.forEach(cell => html += '<td title="' + escapeHtml(cell || '') + '">' + escapeHtml((cell || '').substring(0, 50)) + '</td>');
                html += '</tr>';
            });
            document.getElementById('query-results').innerHTML = html + '</tbody>';
        }

        async function loadPlotData() {
            try {
                const res = await fetch('/api/plot');
                const data = await res.json();

                if (data.error) {
                    document.getElementById('plot-visualization').innerHTML = '<div class="empty-state">' + data.error + '</div>';
                    return;
                }

                // Threads
                let threadsHtml = '';
                data.threads.forEach(t => {
                    const pc = t.priority === 'HIGH' ? 'priority-high' : t.priority === 'MEDIUM' ? 'priority-medium' : 'priority-low';
                    threadsHtml += '<div class="thread-item ' + pc + '"><strong>' + t.id + '</strong><br/><small>' + t.category + ' - ' + t.status + '</small></div>';
                });
                document.getElementById('active-threads').innerHTML = threadsHtml || '<div class="empty-state">No threads</div>';

                // Nodes
                let nodesHtml = '';
                data.nodes.slice(0, 15).forEach(n => {
                    const nc = n.completed ? 'node-completed' : n.triggered ? 'node-active' : 'node-pending';
                    nodesHtml += '<div class="node-item ' + nc + '">' + n.beatType + ' (T' + n.tier + ')</div>';
                });
                document.getElementById('active-nodes').innerHTML = nodesHtml || '<div class="empty-state">No nodes</div>';

                // Graph
                renderGraph(data);

                // Game state
                const stateRes = await fetch('/api/state');
                const state = await stateRes.json();
                document.getElementById('game-state').innerHTML = '<pre class="json-view">' + JSON.stringify(state, null, 2) + '</pre>';
            } catch (e) {}
        }

        function renderGraph(data) {
            if (!data.nodes.length) {
                document.getElementById('plot-visualization').innerHTML = '<div class="empty-state">No plot data</div>';
                return;
            }

            const tiers = {};
            data.nodes.forEach(n => {
                if (!tiers[n.tier]) tiers[n.tier] = [];
                tiers[n.tier].push(n);
            });

            let html = '<div style="display:flex;gap:30px;overflow-x:auto;">';
            Object.keys(tiers).sort().forEach(tier => {
                html += '<div style="min-width:150px;"><div style="font-weight:bold;margin-bottom:8px;color:#58a6ff;">Tier ' + tier + '</div>';
                tiers[tier].forEach(n => {
                    const bg = n.completed ? '#238636' : n.triggered ? '#1f6feb' : '#30363d';
                    html += '<div style="background:' + bg + ';padding:6px 10px;border-radius:4px;margin-bottom:6px;font-size:11px;">' + n.beatType + '</div>';
                });
                html += '</div>';
            });
            document.getElementById('plot-visualization').innerHTML = html + '</div>';
        }

        // Agents
        let agentsData = null;
        let selectedAgentIdx = null;

        async function loadAgents() {
            try {
                const res = await fetch('/api/agents');
                agentsData = await res.json();

                if (agentsData.agents.length === 0) {
                    document.getElementById('agent-list-items').innerHTML = '<div class="empty-state" style="padding:12px;">No agents yet</div>';
                    document.getElementById('agent-conversation').innerHTML = '<div class="empty-state">No agents yet</div>';
                    return;
                }

                let html = '';
                agentsData.agents.forEach((agent, idx) => {
                    const time = new Date(agent.startTime).toLocaleTimeString();
                    const activeClass = idx === selectedAgentIdx ? ' active' : '';
                    html += '<div class="agent-item' + activeClass + '" onclick="showAgent(' + idx + ')">' +
                        '<div class="agent-item-type">' + escapeHtml(agent.agentType) + '</div>' +
                        '<div class="agent-item-meta">' + agent.messageCount + ' messages - ' + time + '</div>' +
                        '</div>';
                });
                document.getElementById('agent-list-items').innerHTML = html;

                // Auto-refresh selected agent conversation
                if (selectedAgentIdx !== null && agentsData.agents[selectedAgentIdx]) {
                    showAgent(selectedAgentIdx);
                }
            } catch (e) {
                document.getElementById('agent-list-items').innerHTML = '<div class="empty-state" style="padding:12px;">Error loading agents</div>';
            }
        }

        function showAgent(idx) {
            const agent = agentsData.agents[idx];
            if (!agent) return;

            selectedAgentIdx = idx;

            // Highlight active item
            document.querySelectorAll('.agent-item').forEach((el, i) => {
                el.classList.toggle('active', i === idx);
            });

            let html = '<div class="agent-system-prompt-label">System Prompt</div>' +
                '<div class="agent-system-prompt">' + escapeHtml(agent.systemPrompt) + '</div>' +
                '<div class="agent-system-prompt-label">Conversation (' + agent.messageCount + ' messages)</div>';

            agent.messages.forEach(msg => {
                const time = new Date(msg.timestamp).toLocaleTimeString();
                const roleClass = msg.role === 'user' ? 'user' : 'assistant';
                html += '<div class="agent-message ' + roleClass + '">' +
                    '<div class="agent-message-role">' + msg.role + '</div>' +
                    '<div class="agent-message-content">' + renderMarkdown(msg.content) + '</div>' +
                    '<div class="agent-message-time">' + time + '</div>' +
                    '</div>';
            });

            document.getElementById('agent-conversation').innerHTML = html;
        }

        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text || '';
            return div.innerHTML;
        }

        function renderMarkdown(text) {
            if (!text) return '';
            let html = escapeHtml(text);

            // Code blocks (```...```)
            html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="md-code-block"><code>$2</code></pre>');

            // Inline code (`...`)
            html = html.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>');

            // Bold (**...**)
            html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

            // Italic (*...*)
            html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

            // Headers (# ... at start of line)
            html = html.replace(/^### (.+)$/gm, '<h4 class="md-h4">$1</h4>');
            html = html.replace(/^## (.+)$/gm, '<h3 class="md-h3">$1</h3>');
            html = html.replace(/^# (.+)$/gm, '<h2 class="md-h2">$1</h2>');

            // Bullet lists (- or * at start of line)
            html = html.replace(/^[\-\*] (.+)$/gm, '<li class="md-li">$1</li>');

            // Wrap consecutive <li> in <ul>
            html = html.replace(/(<li class="md-li">.*<\/li>\n?)+/g, '<ul class="md-ul">$&</ul>');

            // Line breaks (preserve newlines as <br> for non-block elements)
            html = html.replace(/\n/g, '<br>');

            // Clean up extra <br> after block elements
            html = html.replace(/<\/(pre|h2|h3|h4|ul)><br>/g, '</$1>');
            html = html.replace(/<br><(pre|h2|h3|h4|ul)/g, '<$1');

            return html;
        }

        // Init
        connectWebSocket();
        loadLogs();
        loadTables();

        // Auto-refresh active tab every 2 seconds
        let activeTab = 'logs';
        setInterval(() => {
            if (activeTab === 'logs') loadLogs();
            if (activeTab === 'character') loadCharacter();
            if (activeTab === 'agents') loadAgents();
        }, 2000);

        // Track active tab
        document.querySelectorAll('.tab').forEach(tab => {
            tab.addEventListener('click', () => {
                activeTab = tab.dataset.tab;
            });
        });

        // Enter to search
        document.getElementById('log-search').addEventListener('keypress', e => { if (e.key === 'Enter') loadLogs(); });
        document.getElementById('sql-query').addEventListener('keydown', e => { if (e.ctrlKey && e.key === 'Enter') executeQuery(); });

        // TTS functionality
        let ttsEnabled = localStorage.getItem('ttsEnabled') === 'true';
        let ttsVoice = localStorage.getItem('ttsVoice') || 'en-US-GuyNeural';
        const ttsQueue = [];
        let ttsPlaying = false;
        let ttsCancelled = false;
        const ttsAudio = document.getElementById('tts-audio');
        let lastSpokenText = '';

        // Load voices on init
        async function loadTTSVoices() {
            try {
                const res = await fetch('/api/tts/voices');
                const voices = await res.json();
                const select = document.getElementById('tts-voice');
                select.innerHTML = voices.map(v =>
                    '<option value="' + v.id + '"' + (v.id === ttsVoice ? ' selected' : '') + '>' + v.name + '</option>'
                ).join('');
            } catch (e) {
                console.error('Failed to load TTS voices:', e);
            }
        }

        function updateTTSUI() {
            const toggle = document.getElementById('tts-toggle');
            const voiceSelect = document.getElementById('tts-voice');
            if (ttsEnabled) {
                toggle.classList.add('active');
                toggle.querySelector('.tts-icon').textContent = '🔊';
                toggle.querySelector('.tts-label').textContent = 'TTS On';
                voiceSelect.classList.add('enabled');
            } else {
                toggle.classList.remove('active');
                toggle.querySelector('.tts-icon').textContent = '🔇';
                toggle.querySelector('.tts-label').textContent = 'TTS Off';
                voiceSelect.classList.remove('enabled');
            }
        }

        function toggleTTS() {
            ttsEnabled = !ttsEnabled;
            localStorage.setItem('ttsEnabled', ttsEnabled);
            updateTTSUI();
            if (!ttsEnabled) {
                // Stop current playback and clear queue
                ttsAudio.pause();
                ttsQueue.length = 0;
                ttsPlaying = false;
                document.getElementById('tts-toggle').classList.remove('tts-speaking');
            }
        }

        function saveTTSVoice() {
            ttsVoice = document.getElementById('tts-voice').value;
            localStorage.setItem('ttsVoice', ttsVoice);
        }

        function stopTTS() {
            ttsCancelled = true;
            ttsAudio.pause();
            ttsAudio.currentTime = 0;
            ttsQueue.length = 0;
            ttsPlaying = false;
            lastSpokenText = '';
            document.getElementById('tts-toggle').classList.remove('tts-speaking');
            // Reset cancel flag after a short delay
            setTimeout(() => { ttsCancelled = false; }, 100);
        }

        async function speakText(text) {
            if (!ttsEnabled || ttsCancelled || !text || text.trim().length < 50) return;

            // Clean text first
            const cleanText = text
                .replace(/\x1b\[[0-9;]*m/g, '') // Remove ANSI codes
                .replace(/\*\*([^*]+)\*\*/g, '$1') // Remove markdown bold
                .replace(/\*([^*]+)\*/g, '$1') // Remove markdown italic
                .replace(/`([^`]+)`/g, '$1') // Remove inline code
                .replace(/={3,}/g, '') // Remove separator lines
                .replace(/\[.*?\]/g, '') // Remove bracketed text
                .replace(/[\u{1F300}-\u{1F9FF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]|[\u{1F600}-\u{1F64F}]|[\u{1F680}-\u{1F6FF}]|[\u{1F1E0}-\u{1F1FF}]|[\u{2300}-\u{23FF}]|[\u{2B50}]|[\u{1FA00}-\u{1FAFF}]|[\u{FE00}-\u{FE0F}]|[\u{200D}]/gu, '') // Remove emojis
                .replace(/[ℹ️🔍⚔️🛡️💰📜⭐🎯✨🌟💫⚡🔥❄️💧🌿🎭🎪]/g, '') // Remove common game icons
                .trim();

            // Only read generated narrative content - skip all UI/system text
            const lowerText = cleanText.toLowerCase();

            // Skip system menus, prompts, and non-narrative text
            if (lowerText.includes('main menu') ||
                lowerText.includes('game setup') ||
                lowerText.includes('character creation') ||
                lowerText.includes('enter your') ||
                lowerText.includes('how would you like') ||
                lowerText.includes('generating') ||
                lowerText.includes('loading') ||
                lowerText.includes('connected') ||
                lowerText.includes('disconnected') ||
                lowerText.includes('debug') ||
                lowerText.includes('rpgenerator') ||
                lowerText.includes('using claude') ||
                lowerText.includes('using openai') ||
                lowerText.includes('using gemini') ||
                lowerText.includes('using grok') ||
                lowerText.includes('backstory:') ||
                lowerText.includes('keep this backstory') ||
                lowerText.includes('generate new') ||
                lowerText.includes('edit backstory') ||
                lowerText.match(/^[b]\.\s/i) || // "B. Back" options
                lowerText.match(/^>\s*$/) || // Just prompts
                cleanText.match(/^[\s\-=]+$/)) { // Separator lines
                return;
            }

            // Must look like narrative prose (has sentences with periods or story content)
            const hasSentences = cleanText.includes('. ') || cleanText.endsWith('.');
            const hasNarrativeLength = cleanText.length > 100;

            if (!hasSentences && !hasNarrativeLength) return;
            if (cleanText === lastSpokenText) return;

            lastSpokenText = cleanText;
            ttsQueue.push(cleanText);
            processQueue();
        }

        async function processQueue() {
            if (ttsPlaying || ttsQueue.length === 0 || ttsCancelled) return;

            ttsPlaying = true;
            const text = ttsQueue.shift();

            try {
                document.getElementById('tts-toggle').classList.add('tts-speaking');

                const response = await fetch('/api/tts', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ text: text, voice: ttsVoice })
                });

                if (response.ok) {
                    const blob = await response.blob();
                    const url = URL.createObjectURL(blob);
                    ttsAudio.src = url;

                    ttsAudio.onended = () => {
                        URL.revokeObjectURL(url);
                        ttsPlaying = false;
                        document.getElementById('tts-toggle').classList.remove('tts-speaking');
                        processQueue();
                    };

                    ttsAudio.onerror = () => {
                        URL.revokeObjectURL(url);
                        ttsPlaying = false;
                        document.getElementById('tts-toggle').classList.remove('tts-speaking');
                        processQueue();
                    };

                    await ttsAudio.play();
                } else {
                    ttsPlaying = false;
                    document.getElementById('tts-toggle').classList.remove('tts-speaking');
                    processQueue();
                }
            } catch (e) {
                console.error('TTS error:', e);
                ttsPlaying = false;
                document.getElementById('tts-toggle').classList.remove('tts-speaking');
                processQueue();
            }
        }

        // Initialize TTS
        loadTTSVoices();
        updateTTSUI();
    </script>
</body>
</html>
""".trimIndent()

    fun updateGame(game: Game) {
        currentGame = game
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    // Data classes
    @Serializable
    data class TerminalMessage(val type: String, val data: String, val seq: Long = 0)

    @Serializable
    data class LogEntry(
        val id: Int,
        val timestamp: Long,
        val eventType: String,
        val category: String,
        val importance: String,
        val text: String,
        val npcId: String?,
        val locationId: String?,
        val questId: String?
    )

    @Serializable
    data class LogsResponse(val logs: List<LogEntry>, val total: Int)

    @Serializable
    data class TableInfo(val name: String, val description: String)

    @Serializable
    data class TableDataResponse(
        val columns: List<String>,
        val rows: List<List<String?>>,
        val error: String?
    )

    @Serializable
    data class QueryResult(val columns: List<String>, val rows: List<List<String?>>)

    @Serializable
    data class QueryResponse(val success: Boolean, val result: QueryResult?, val error: String?)

    @Serializable
    data class PlotThreadInfo(
        val id: String,
        val category: String,
        val priority: String,
        val status: String,
        val threadJson: String
    )

    @Serializable
    data class PlotNodeInfo(
        val id: String,
        val threadId: String,
        val tier: Int,
        val sequence: Int,
        val beatType: String,
        val triggered: Boolean,
        val completed: Boolean,
        val abandoned: Boolean,
        val nodeJson: String
    )

    @Serializable
    data class PlotEdgeInfo(
        val id: String,
        val fromNodeId: String,
        val toNodeId: String,
        val edgeType: String,
        val weight: Double,
        val disabled: Boolean
    )

    @Serializable
    data class PlotDataResponse(
        val threads: List<PlotThreadInfo>,
        val nodes: List<PlotNodeInfo>,
        val edges: List<PlotEdgeInfo>,
        val activeNodes: List<String>,
        val error: String?
    )

    @Serializable
    data class GameStateInfo(
        val playerName: String,
        val level: Int,
        val health: Int,
        val maxHealth: Int,
        val experience: Long,
        val location: String,
        val stats: Map<String, Int>
    )

    @Serializable
    data class CharacterSheetData(
        val name: String? = null,
        val level: Int? = null,
        val health: Int? = null,
        val maxHealth: Int? = null,
        val energy: Int? = null,
        val maxEnergy: Int? = null,
        val experience: Long? = null,
        val experienceToNext: Long? = null,
        val location: String? = null,
        val currentScene: String? = null,
        val backstory: String? = null,
        val stats: Map<String, Int>? = null,
        val inventory: List<InventoryItem>? = null,
        val activeQuests: List<QuestInfo>? = null,
        val npcsAtLocation: List<NPCDisplayInfo>? = null,
        val error: String? = null
    )

    @Serializable
    data class NPCDisplayInfo(
        val id: String,
        val name: String,
        val archetype: String,
        val disposition: String,
        val description: String
    )

    @Serializable
    data class InventoryItem(
        val name: String,
        val quantity: Int,
        val rarity: String,
        val description: String
    )

    @Serializable
    data class QuestInfo(
        val name: String,
        val description: String,
        val status: String,
        val objectives: List<QuestObjectiveInfo>
    )

    @Serializable
    data class QuestObjectiveInfo(
        val description: String,
        val completed: Boolean
    )

    // Agent tracking data classes
    data class AgentConversation(
        val id: String,
        val agentType: String,
        val systemPrompt: String,
        val messages: MutableList<AgentMessage>,
        val startTime: Long
    )

    data class AgentMessage(
        val role: String,
        val content: String,
        val timestamp: Long
    )

    @Serializable
    data class AgentsResponse(
        val agents: List<AgentInfo>,
        val totalAgents: Int
    )

    @Serializable
    data class AgentInfo(
        val id: String,
        val agentType: String,
        val systemPrompt: String,
        val messageCount: Int,
        val startTime: Long,
        val messages: List<AgentMessageInfo>
    )

    @Serializable
    data class AgentMessageInfo(
        val role: String,
        val content: String,
        val timestamp: Long
    )

    // TTS data classes
    @Serializable
    data class TTSRequest(
        val text: String,
        val voice: String? = null
    )

    @Serializable
    data class TTSVoice(
        val id: String,
        val name: String,
        val locale: String
    )

    /**
     * Generate TTS audio using edge-tts.
     * Requires: pip install edge-tts
     */
    private fun generateTTS(text: String, voice: String): ByteArray? {
        return try {
            val tempFile = java.io.File.createTempFile("tts_", ".mp3")
            tempFile.deleteOnExit()

            // Clean text for TTS (remove ANSI codes, etc.)
            val cleanText = text
                .replace(Regex("\u001B\\[[0-9;]*m"), "") // Remove ANSI codes
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // Remove markdown bold
                .replace(Regex("\\*([^*]+)\\*"), "$1") // Remove markdown italic
                .replace(Regex("`([^`]+)`"), "$1") // Remove inline code
                .trim()

            if (cleanText.isBlank()) return null

            val process = ProcessBuilder(
                "edge-tts",
                "--voice", voice,
                "--rate", "+20%",
                "--text", cleanText,
                "--write-media", tempFile.absolutePath
            ).redirectErrorStream(true).start()

            val exitCode = process.waitFor()
            if (exitCode == 0 && tempFile.exists() && tempFile.length() > 0) {
                val bytes = tempFile.readBytes()
                tempFile.delete()
                bytes
            } else {
                val error = process.inputStream.bufferedReader().readText()
                System.err.println("edge-tts failed: $error")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            System.err.println("TTS error: ${e.message}")
            null
        }
    }
}
