package com.rpgenerator.cli

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.RPGClient
import com.rpgenerator.core.persistence.DriverFactory
import java.io.File

fun main(args: Array<String>) {
    // Show help if requested
    if (args.contains("--help") || args.contains("-h")) {
        printHelp()
        return
    }

    // Show all models if requested
    if (args.contains("--list-models") || args.contains("--models")) {
        ModelSelector.showAllModels()
        return
    }

    // Create data directory for game saves
    val dataDir = File(System.getProperty("user.home"), ".rpgenerator")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }

    val databasePath = File(dataDir, "games.db").absolutePath

    // Initialize client with SqlDriver
    val driver = DriverFactory(databasePath).createDriver()
    val client = RPGClient(driver)

    // Choose LLM implementation based on flags or auto-detect
    val llm: LLMInterface = selectLLM(args)

    // Check if image generation is enabled
    val imageGenerator: ImageGenerator = if (args.contains("--image-gen") || args.contains("--images")) {
        val generator = StableDiffusionGenerator()
        println("🎨 Image generation enabled (checking service...)")
        generator
    } else {
        NoOpImageGenerator()
    }

    // Check if debug web server should auto-start
    val debugMode = args.contains("--debug")
    if (debugMode) {
        println("🔍 Debug mode enabled - Web dashboard will start automatically")
    }

    println()

    // Start the terminal UI
    val terminal = RPGTerminal(client, llm, imageGenerator, debugMode)
    try {
        terminal.run()
    } finally {
        client.close()
    }
}

private fun selectLLM(args: Array<String>): LLMInterface {
    // Check for explicit model specification: --model=model-id
    val modelArg = args.firstOrNull { it.startsWith("--model=") }
    val explicitModel = modelArg?.substringAfter("=")

    return when {
        // Explicit LLM selection via flags
        args.contains("--claude") -> {
            val model = explicitModel ?: if (args.contains("--select-model")) {
                ModelSelector.selectModel("claude") ?: ModelRegistry.getDefaultModel("claude")
            } else {
                ModelRegistry.getDefaultModel("claude")
            }
            println("🤖 Using Claude AI: $model")
            tryCreateLLM({ ClaudeLLM(model = model!!) }, "ANTHROPIC_API_KEY")
        }
        args.contains("--openai") || args.contains("--gpt") -> {
            val model = explicitModel ?: if (args.contains("--select-model")) {
                ModelSelector.selectModel("openai") ?: ModelRegistry.getDefaultModel("openai")
            } else {
                ModelRegistry.getDefaultModel("openai")
            }
            println("🤖 Using OpenAI GPT: $model")
            tryCreateLLM({ OpenAILLM(model = model!!) }, "OPENAI_API_KEY")
        }
        args.contains("--gemini") || args.contains("--google") -> {
            val model = explicitModel ?: if (args.contains("--select-model")) {
                ModelSelector.selectModel("gemini") ?: ModelRegistry.getDefaultModel("gemini")
            } else {
                ModelRegistry.getDefaultModel("gemini")
            }
            println("🤖 Using Google Gemini: $model")
            tryCreateLLM({ GeminiLLM(model = model!!) }, "GOOGLE_API_KEY")
        }
        args.contains("--grok") || args.contains("--xai") -> {
            val model = explicitModel ?: if (args.contains("--select-model")) {
                ModelSelector.selectModel("grok") ?: ModelRegistry.getDefaultModel("grok")
            } else {
                ModelRegistry.getDefaultModel("grok")
            }
            println("🤖 Using Grok (xAI): $model")
            tryCreateLLM({ GrokLLM(model = model!!) }, "XAI_API_KEY")
        }
        args.contains("--claude-code") || args.contains("--claude-cli") -> {
            println("🤖 Using Claude Code CLI (requires Claude Pro)")
            tryCreateLLM({ ClaudeCodeLLM() }, "Claude Code CLI")
        }
        args.contains("--codex") || args.contains("--copilot") -> {
            println("🤖 Using GitHub Copilot / Codex")
            tryCreateLLM({ CodexLLM() }, "GITHUB_TOKEN or OPENAI_API_KEY")
        }
        args.contains("--mock") -> {
            println("🤖 Using SimpleLLM (mock)")
            SimpleLLM()
        }
        else -> {
            // Auto-detect based on available API keys
            autoDetectLLM(args.contains("--select-model"))
        }
    }
}

private fun tryCreateLLM(creator: () -> LLMInterface, requiredEnvVar: String): LLMInterface {
    return try {
        creator()
    } catch (e: Exception) {
        println("⚠️  Failed to initialize: ${e.message}")
        println("⚠️  Make sure $requiredEnvVar environment variable is set")
        println("⚠️  Falling back to SimpleLLM (mock)")
        SimpleLLM()
    }
}

private fun autoDetectLLM(selectModel: Boolean = false): LLMInterface {
    // Check for API keys in priority order: Claude > OpenAI > Gemini > Grok > Mock
    return when {
        System.getenv("ANTHROPIC_API_KEY")?.isNotEmpty() == true -> {
            val model = if (selectModel) {
                ModelSelector.selectModel("claude") ?: ModelRegistry.getDefaultModel("claude")
            } else {
                ModelRegistry.getDefaultModel("claude")
            }
            println("🤖 Using Claude AI: $model (detected ANTHROPIC_API_KEY)")
            tryCreateLLM({ ClaudeLLM(model = model) }, "ANTHROPIC_API_KEY")
        }
        System.getenv("OPENAI_API_KEY")?.isNotEmpty() == true -> {
            val model = if (selectModel) {
                ModelSelector.selectModel("openai") ?: ModelRegistry.getDefaultModel("openai")
            } else {
                ModelRegistry.getDefaultModel("openai")
            }
            println("🤖 Using OpenAI GPT: $model (detected OPENAI_API_KEY)")
            tryCreateLLM({ OpenAILLM(model = model) }, "OPENAI_API_KEY")
        }
        System.getenv("GOOGLE_API_KEY")?.isNotEmpty() == true -> {
            val model = if (selectModel) {
                ModelSelector.selectModel("gemini") ?: ModelRegistry.getDefaultModel("gemini")
            } else {
                ModelRegistry.getDefaultModel("gemini")
            }
            println("🤖 Using Google Gemini: $model (detected GOOGLE_API_KEY)")
            tryCreateLLM({ GeminiLLM(model = model) }, "GOOGLE_API_KEY")
        }
        System.getenv("XAI_API_KEY")?.isNotEmpty() == true -> {
            val model = if (selectModel) {
                ModelSelector.selectModel("grok") ?: ModelRegistry.getDefaultModel("grok")
            } else {
                ModelRegistry.getDefaultModel("grok")
            }
            println("🤖 Using Grok (xAI): $model (detected XAI_API_KEY)")
            tryCreateLLM({ GrokLLM(model = model) }, "XAI_API_KEY")
        }
        else -> {
            println("🤖 Using SimpleLLM (mock)")
            println("💡 Tip: Set an API key to use real AI:")
            println("   - ANTHROPIC_API_KEY for Claude")
            println("   - OPENAI_API_KEY for GPT")
            println("   - GOOGLE_API_KEY for Gemini")
            println("   - XAI_API_KEY for Grok")
            SimpleLLM()
        }
    }
}

private fun printHelp() {
    println("""
        RPGenerator CLI - LitRPG Adventure Engine

        Usage: rpgenerator [OPTIONS]

        LLM Provider Options:
          --claude, --anthropic    Use Claude API (requires ANTHROPIC_API_KEY)
          --claude-code            Use Claude Code CLI (requires Claude Pro subscription)
          --openai, --gpt          Use OpenAI GPT API (requires OPENAI_API_KEY)
          --codex, --copilot       Use GitHub Copilot/Codex (requires GitHub Copilot subscription)
          --gemini, --google       Use Google Gemini (requires GOOGLE_API_KEY - FREE tier available)
          --grok, --xai            Use Grok/xAI (requires XAI_API_KEY)
          --mock                   Use mock LLM (no API/subscription required)

        Model Selection:
          --select-model           Interactively choose which model to use
          --model=MODEL_ID         Use specific model (e.g., --model=claude-sonnet-4-5)
          --list-models, --models  Show all available models and exit

        UI Options:
          --image-gen, --images    Enable AI image generation for scenes (requires local service)
          --debug                  Auto-start web debug dashboard on http://localhost:8080

        Other Options:
          -h, --help               Show this help message

        Environment Variables:
          ANTHROPIC_API_KEY        API key for Claude API (https://console.anthropic.com)
          OPENAI_API_KEY           API key for OpenAI GPT (https://platform.openai.com/api-keys)
          GOOGLE_API_KEY           API key for Gemini (https://aistudio.google.com/app/apikey)
          XAI_API_KEY              API key for Grok (https://console.x.ai)
          GITHUB_TOKEN             GitHub token for Copilot access (optional)

        If no option is specified, the CLI will auto-detect which LLM to use
        based on available environment variables.

        Examples:
          rpgenerator                           # Auto-detect LLM with default model
          rpgenerator --claude                  # Use Claude API with default model
          rpgenerator --claude-code             # Use Claude Code CLI (Claude Pro subscription)
          rpgenerator --codex                   # Use GitHub Copilot/Codex
          rpgenerator --gemini                  # Use Gemini (FREE tier available!)
          rpgenerator --claude --select-model   # Choose Claude model interactively
          rpgenerator --openai --model=gpt-5.1-instant  # Use specific OpenAI model
          rpgenerator --list-models             # Show all available models
          rpgenerator --mock                    # Use mock (no API calls)
          ANTHROPIC_API_KEY=sk-... rpgenerator  # Set key inline

        Recommended Models (Nov 2025):
          Claude:  claude-sonnet-4-5-20250929 (best for complex narratives)
          OpenAI:  gpt-5.1-instant (latest, warmer & more intelligent)
          Gemini:  gemini-3.0-pro (most powerful Gemini)
          Grok:    grok-4-1-fast-reasoning (latest xAI model)
    """.trimIndent())
}
