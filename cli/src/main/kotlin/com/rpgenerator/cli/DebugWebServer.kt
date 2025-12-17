package com.rpgenerator.cli

import com.rpgenerator.core.api.Game
import com.rpgenerator.core.api.RPGClient
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.html.*

/**
 * Web-based debug dashboard that displays game state.
 */
class DebugWebServer(
    private val client: RPGClient,
    private val port: Int = 8080
) {
    private var server: NettyApplicationEngine? = null
    private var currentGame: Game? = null

    fun start(game: Game? = null) {
        currentGame = game

        server = embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    call.respondHtml {
                        head {
                            title { +"RPGenerator Debug Dashboard" }
                            style {
                                unsafe {
                                    raw("""
                                        body {
                                            font-family: 'Consolas', 'Monaco', monospace;
                                            background: #1e1e1e;
                                            color: #d4d4d4;
                                            padding: 20px;
                                            margin: 0;
                                        }
                                        .container {
                                            max-width: 1400px;
                                            margin: 0 auto;
                                        }
                                        h1 {
                                            color: #4ec9b0;
                                            border-bottom: 2px solid #4ec9b0;
                                            padding-bottom: 10px;
                                        }
                                        h2 {
                                            color: #dcdcaa;
                                            border-bottom: 1px solid #3e3e3e;
                                            padding-bottom: 8px;
                                            margin-top: 30px;
                                        }
                                        .section {
                                            background: #252526;
                                            padding: 15px;
                                            margin: 15px 0;
                                            border-radius: 5px;
                                            border: 1px solid #3e3e3e;
                                        }
                                        .stat-row {
                                            display: grid;
                                            grid-template-columns: 200px 1fr;
                                            margin: 8px 0;
                                            padding: 5px 0;
                                        }
                                        .stat-label {
                                            color: #9cdcfe;
                                            font-weight: bold;
                                        }
                                        .stat-value {
                                            color: #ce9178;
                                        }
                                        .refresh-btn {
                                            background: #0e639c;
                                            color: white;
                                            border: none;
                                            padding: 10px 20px;
                                            border-radius: 5px;
                                            cursor: pointer;
                                            font-size: 14px;
                                            margin-bottom: 20px;
                                        }
                                        .refresh-btn:hover {
                                            background: #1177bb;
                                        }
                                        pre {
                                            background: #1e1e1e;
                                            padding: 15px;
                                            border-radius: 5px;
                                            overflow-x: auto;
                                            border: 1px solid #3e3e3e;
                                        }
                                        .error {
                                            color: #f48771;
                                            background: #5a1d1d;
                                            padding: 10px;
                                            border-radius: 5px;
                                            border: 1px solid #f48771;
                                        }
                                        .no-game {
                                            color: #dcdcaa;
                                            font-style: italic;
                                            text-align: center;
                                            padding: 40px;
                                        }
                                    """)
                                }
                            }
                            script {
                                unsafe {
                                    raw("""
                                        function refresh() {
                                            location.reload();
                                        }
                                        // Auto-refresh every 5 seconds
                                        setInterval(refresh, 5000);
                                    """)
                                }
                            }
                        }
                        body {
                            div(classes = "container") {
                                h1 { +"🎮 RPGenerator Debug Dashboard" }

                                button(classes = "refresh-btn") {
                                    onClick = "refresh()"
                                    +"🔄 Refresh Now"
                                }

                                p {
                                    +"Auto-refreshes every 5 seconds | Port: $port"
                                }

                                if (currentGame == null) {
                                    div(classes = "no-game") {
                                        +"No active game. Start a game to see debug information."
                                    }
                                } else {
                                    try {
                                        val debugText = CoroutineScope(Dispatchers.IO).run {
                                            kotlinx.coroutines.runBlocking {
                                                client.getDebugView(currentGame!!)
                                            }
                                        }

                                        div(classes = "section") {
                                            pre {
                                                +debugText
                                            }
                                        }
                                    } catch (e: Exception) {
                                        div(classes = "error") {
                                            +"Error loading debug information: ${e.message}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                get("/api/debug") {
                    if (currentGame == null) {
                        call.respondText("No active game")
                    } else {
                        try {
                            val debugText = client.getDebugView(currentGame!!)
                            call.respondText(debugText)
                        } catch (e: Exception) {
                            call.respondText("Error: ${e.message}")
                        }
                    }
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("Starting web server on port $port...")
                server?.start(wait = false)
                println("Web server started successfully on http://localhost:$port")
            } catch (e: Exception) {
                println("Failed to start web server: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun updateGame(game: Game) {
        currentGame = game
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
