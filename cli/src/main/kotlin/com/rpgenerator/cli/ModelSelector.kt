package com.rpgenerator.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.terminal.Terminal

object ModelSelector {

    private val terminal = Terminal()

    /**
     * Interactive model selection for a given provider.
     * Returns the selected model ID or null if cancelled.
     */
    fun selectModel(provider: String): String? {
        val models = ModelRegistry.getModelsByProvider(provider)
        if (models.isEmpty()) {
            return ModelRegistry.getDefaultModel(provider)
        }

        terminal.println()
        terminal.println((bold + cyan)("=== Select ${provider.uppercase()} Model ==="))
        terminal.println()

        models.forEachIndexed { index, model ->
            val prefix = if (model.recommended) "${green("✓")} ${yellow("[RECOMMENDED]")}" else "  "
            terminal.println("${green("${index + 1}.")} $prefix ${bold(model.name)}")
            terminal.println("    ${white(model.description)}")
            terminal.println("    Context: ${cyan(model.contextWindow)} | Pricing: ${magenta(model.pricing)}")
            terminal.println()
        }

        terminal.println("${green("0.")} Use default (${ModelRegistry.getDefaultModel(provider)})")
        terminal.println()
        terminal.print(brightBlue("Select model (0-${models.size}): "))

        val choice = readLine()?.toIntOrNull() ?: 0

        return when {
            choice == 0 -> ModelRegistry.getDefaultModel(provider)
            choice in 1..models.size -> models[choice - 1].id
            else -> {
                terminal.println(red("Invalid choice. Using default model."))
                ModelRegistry.getDefaultModel(provider)
            }
        }
    }

    /**
     * Show all available models for all providers.
     */
    fun showAllModels() {
        terminal.println()
        terminal.println((bold + cyan)("=== Available Models ==="))

        listOf("claude", "openai", "gemini", "grok").forEach { provider ->
            terminal.println()
            terminal.println((bold + yellow)("${provider.uppercase()} Models:"))
            terminal.println()

            val models = ModelRegistry.getModelsByProvider(provider)
            models.forEach { model ->
                val marker = if (model.recommended) green("★") else " "
                terminal.println("$marker ${bold(model.name)} (${model.id})")
                terminal.println("  ${white(model.description)}")
                terminal.println("  Context: ${cyan(model.contextWindow)} | ${magenta(model.pricing)}")
                terminal.println()
            }
        }
    }
}
