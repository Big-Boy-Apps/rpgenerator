package com.rpgenerator.cli

/**
 * Registry of all available LLM models across providers.
 * Based on November 2025 API availability.
 */
object ModelRegistry {

    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String,
        val contextWindow: String,
        val pricing: String,
        val recommended: Boolean = false
    )

    // Claude Models (Anthropic)
    val CLAUDE_MODELS = listOf(
        ModelInfo(
            id = "claude-sonnet-4-5-20250929",
            name = "Claude Sonnet 4.5",
            description = "Best coding model, strongest for complex agents (Latest, Recommended)",
            contextWindow = "200K (1M with beta header)",
            pricing = "$3/$15 per million tokens",
            recommended = true
        ),
        ModelInfo(
            id = "claude-haiku-4-5",
            name = "Claude Haiku 4.5",
            description = "Fast, low-latency model for real-time assistants",
            contextWindow = "200K",
            pricing = "$1/$5 per million tokens"
        ),
        ModelInfo(
            id = "claude-opus-4-1",
            name = "Claude Opus 4.1",
            description = "Upgrade focused on agentic tasks, coding, reasoning",
            contextWindow = "200K",
            pricing = "$15/$75 per million tokens"
        ),
        ModelInfo(
            id = "claude-sonnet-4",
            name = "Claude Sonnet 4",
            description = "Previous generation Sonnet model",
            contextWindow = "200K",
            pricing = "$3/$15 per million tokens"
        )
    )

    // OpenAI Models
    val OPENAI_MODELS = listOf(
        ModelInfo(
            id = "gpt-5.1-instant",
            name = "GPT-5.1 Instant",
            description = "Warmer, more intelligent, better at following instructions (Latest, Recommended)",
            contextWindow = "Adaptive context",
            pricing = "~50% tokens vs competitors",
            recommended = true
        ),
        ModelInfo(
            id = "gpt-5.1-thinking",
            name = "GPT-5.1 Thinking",
            description = "Advanced reasoning, adaptive thinking, 2-3x faster than GPT-5",
            contextWindow = "Adaptive context",
            pricing = "Token-efficient reasoning"
        ),
        ModelInfo(
            id = "gpt-5.1-codex-max",
            name = "GPT-5.1 Codex Max",
            description = "Frontier agentic coding model, works over millions of tokens",
            contextWindow = "Millions (compaction)",
            pricing = "Premium coding pricing"
        ),
        ModelInfo(
            id = "gpt-5",
            name = "GPT-5",
            description = "Previous 5-series model (August 2025)",
            contextWindow = "Large context",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "gpt-4.1",
            name = "GPT-4.1",
            description = "Major gains in coding and instruction following",
            contextWindow = "1M tokens",
            pricing = "Variable pricing"
        ),
        ModelInfo(
            id = "gpt-4.1-mini",
            name = "GPT-4.1 Mini",
            description = "Beats GPT-4o, 50% faster, 83% cheaper",
            contextWindow = "1M tokens",
            pricing = "83% cheaper than GPT-4o"
        ),
        ModelInfo(
            id = "gpt-4o",
            name = "GPT-4o",
            description = "Multimodal model integrating text and images",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "gpt-4o-mini",
            name = "GPT-4o Mini",
            description = "Replaced GPT-3.5 Turbo, fast and efficient",
            contextWindow = "128K tokens",
            pricing = "Lower cost"
        )
    )

    // Google Gemini Models
    val GEMINI_MODELS = listOf(
        ModelInfo(
            id = "gemini-3.0-pro",
            name = "Gemini 3.0 Pro",
            description = "Most powerful Gemini model available (Latest, Recommended)",
            contextWindow = "2M tokens",
            pricing = "Premium pricing",
            recommended = true
        ),
        ModelInfo(
            id = "gemini-3.0-deep-think",
            name = "Gemini 3.0 Deep Think",
            description = "Advanced reasoning and deep analysis",
            contextWindow = "2M tokens",
            pricing = "Premium pricing"
        ),
        ModelInfo(
            id = "gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            description = "Stable, contextual thinking, long-form generation",
            contextWindow = "2M tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            description = "Next-gen features, superior speed, native tool use",
            contextWindow = "1M tokens",
            pricing = "Lower cost"
        ),
        ModelInfo(
            id = "gemini-2.5-flash-lite",
            name = "Gemini 2.5 Flash-Lite",
            description = "Fast, low-cost, high-performance (GA)",
            contextWindow = "1M tokens",
            pricing = "Lowest cost"
        ),
        ModelInfo(
            id = "gemini-1.5-flash",
            name = "Gemini 1.5 Flash",
            description = "Previous generation fast model",
            contextWindow = "1M tokens",
            pricing = "Legacy pricing"
        ),
        ModelInfo(
            id = "gemini-1.5-pro",
            name = "Gemini 1.5 Pro",
            description = "Previous generation pro model",
            contextWindow = "2M tokens",
            pricing = "Legacy pricing"
        )
    )

    // xAI Grok Models
    val GROK_MODELS = listOf(
        ModelInfo(
            id = "grok-4-1-fast-reasoning",
            name = "Grok 4.1 Fast Reasoning",
            description = "Optimized for real-world tool use with reasoning (Latest, Recommended)",
            contextWindow = "128K tokens",
            pricing = "Standard pricing",
            recommended = true
        ),
        ModelInfo(
            id = "grok-4-1-fast-non-reasoning",
            name = "Grok 4.1 Fast Non-Reasoning",
            description = "Fast model without reasoning overhead",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "grok-4-fast-reasoning",
            name = "Grok 4 Fast Reasoning",
            description = "Previous gen with reasoning",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "grok-4-fast-non-reasoning",
            name = "Grok 4 Fast Non-Reasoning",
            description = "Previous gen without reasoning",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "grok-code-fast-1",
            name = "Grok Code Fast 1",
            description = "Specialized for code generation",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "grok-4",
            name = "Grok 4",
            description = "Standard Grok 4 model",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "grok-3",
            name = "Grok 3",
            description = "Previous generation model",
            contextWindow = "128K tokens",
            pricing = "Standard pricing"
        ),
        ModelInfo(
            id = "grok-3-mini",
            name = "Grok 3 Mini",
            description = "Smaller, faster Grok 3",
            contextWindow = "128K tokens",
            pricing = "Lower cost"
        ),
        ModelInfo(
            id = "grok-beta",
            name = "Grok Beta",
            description = "Beta version with function calling",
            contextWindow = "128K tokens",
            pricing = "Beta pricing"
        )
    )

    fun getDefaultModel(provider: String): String {
        return when (provider.lowercase()) {
            "claude", "anthropic" -> "claude-sonnet-4-5-20250929"
            "openai", "gpt" -> "gpt-5.1-instant"
            "gemini", "google" -> "gemini-3.0-pro"
            "grok", "xai" -> "grok-4-1-fast-reasoning"
            else -> ""
        }
    }

    fun getModelsByProvider(provider: String): List<ModelInfo> {
        return when (provider.lowercase()) {
            "claude", "anthropic" -> CLAUDE_MODELS
            "openai", "gpt" -> OPENAI_MODELS
            "gemini", "google" -> GEMINI_MODELS
            "grok", "xai" -> GROK_MODELS
            else -> emptyList()
        }
    }

    fun findModel(provider: String, modelId: String): ModelInfo? {
        return getModelsByProvider(provider).find { it.id == modelId }
    }
}
