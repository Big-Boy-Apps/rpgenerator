package com.rpgenerator.core.story

import com.rpgenerator.core.api.WorldSettings

/**
 * The foundational lore of the game world.
 * Now configurable - accepts WorldSettings instead of using hardcoded values.
 */
internal class WorldLore(private val settings: WorldSettings) {

    // Core lore comes from WorldSettings
    val coreConcept: String get() = settings.coreConcept
    val originStory: String get() = settings.originStory
    val currentState: String get() = settings.currentState
    val worldName: String get() = settings.worldName
    val themes: List<com.rpgenerator.core.api.WorldTheme> get() = settings.themes
    val rules: com.rpgenerator.core.api.WorldRules get() = settings.rules

    /**
     * Get lore entry by topic
     */
    fun getLoreEntry(topic: String): String? {
        val normalized = topic.lowercase()

        // Check core lore topics
        when (normalized) {
            "system", "power system", "core concept" -> return coreConcept
            "origin", "beginning", "how it started" -> return originStory
            "world", "current state", "now" -> return currentState
            "rules", "world rules" -> return formatWorldRules()
            "themes", "tone" -> return formatThemes()
        }

        // Check additional lore
        return settings.additionalLore[topic]
    }

    /**
     * Format world rules as readable text
     */
    private fun formatWorldRules(): String {
        return buildString {
            appendLine("World Rules:")
            appendLine("- Respawn: ${if (rules.hasRespawn) "Yes (${rules.respawnPenalty})" else "No - Permadeath"}")
            appendLine("- Safe Zones: ${if (rules.hasSafeZones) "Yes" else "No"}")
            appendLine("- Can Lose Levels: ${if (rules.canLoseLevels) "Yes" else "No"}")
            appendLine("- PvP: ${if (rules.pvpEnabled) "Enabled" else "Disabled"}")
            if (rules.specialMechanics.isNotEmpty()) {
                appendLine("\nSpecial Mechanics:")
                rules.specialMechanics.forEach { appendLine("- $it") }
            }
        }
    }

    /**
     * Format themes as readable text
     */
    private fun formatThemes(): String {
        if (themes.isEmpty()) return "No specific themes defined"
        return "World Themes: ${themes.joinToString(", ") { it.name.lowercase().replace('_', ' ') }}"
    }
}
