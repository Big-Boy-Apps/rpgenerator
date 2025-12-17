package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * World settings that define the core lore and rules.
 * Intentionally minimal - factions, NPCs, and story beats should be generated dynamically.
 *
 * Philosophy: Define WHAT the world is, not WHO populates it or WHAT happens in it.
 */
@Serializable
data class WorldSettings(
    /** Name of this world/setting */
    val worldName: String,

    /** Core concept/premise (what is the power system/magic/System?) */
    val coreConcept: String,

    /** How did this world come to be? (origin event, creation myth, etc.) */
    val originStory: String,

    /** Current state of the world (dangers, opportunities, what daily life is like) */
    val currentState: String,

    /** Themes and tone of this world */
    val themes: List<WorldTheme> = emptyList(),

    /** Core world rules that affect gameplay */
    val rules: WorldRules = WorldRules(),

    /** Hints for dynamic content generation (NOT prescriptive) */
    val generationHints: GenerationHints = GenerationHints(),

    /** Additional lore topics (key = topic name, value = lore text) */
    val additionalLore: Map<String, String> = emptyMap()
)

/**
 * Themes that define the tone and feel of the world.
 * Used by AI to generate appropriate content.
 */
@Serializable
enum class WorldTheme {
    DARK_AND_GRITTY,      // Grimdark, survival horror
    HOPEFUL_ADVENTURE,     // Optimistic, heroic journey
    MYSTERIOUS,            // Unknown forces, cosmic horror
    POLITICAL_INTRIGUE,    // Factions, scheming, power plays
    EXPLORATION_FOCUSED,   // Discovery, mapping unknown
    COMBAT_FOCUSED,        // Action, battles, war
    CULTIVATION_JOURNEY,   // Personal growth, enlightenment
    TRAGIC,                // Loss, sacrifice, hard choices
    HUMOROUS,              // Lighthearted, comedic moments
    PHILOSOPHICAL          // Questions about meaning, identity
}

/**
 * Core rules that govern how the world works.
 * These affect actual gameplay mechanics.
 */
@Serializable
data class WorldRules(
    /** Can players respawn after death? */
    val hasRespawn: Boolean = true,

    /** Cost/penalty for respawning (if applicable) */
    val respawnPenalty: String? = "XP loss and item durability damage",

    /** Are there safe zones where combat is disabled? */
    val hasSafeZones: Boolean = true,

    /** Can players lose levels from death? */
    val canLoseLevels: Boolean = false,

    /** Is PvP enabled in this world? */
    val pvpEnabled: Boolean = true,

    /** Special world mechanics (e.g., "Time loops reset every 24 hours") */
    val specialMechanics: List<String> = emptyList()
)

/**
 * Hints for AI content generation.
 * These suggest what kind of content to generate, but don't prescribe specifics.
 */
@Serializable
data class GenerationHints(
    /** Suggested number of major power groups/factions (0 = none, 1 = united, 2+ = conflict) */
    val suggestedFactionCount: Int = 3,

    /** Types of conflicts that might arise (helps AI generate appropriate quests) */
    val conflictTypes: List<ConflictType> = listOf(ConflictType.FACTION_RIVALRY, ConflictType.SURVIVAL),

    /** Tone for NPC personalities (grim? hopeful? paranoid?) */
    val npcPersonalityTone: String = "varied - some hopeful, some cynical, all affected by circumstances",

    /** Is this world more linear or open-world in progression? */
    val progressionStyle: ProgressionStyle = ProgressionStyle.SEMI_LINEAR
)

@Serializable
enum class ConflictType {
    FACTION_RIVALRY,       // Groups competing for power
    SURVIVAL,              // Everyone vs environment
    MONSTER_INVASION,      // External threat
    INTERNAL_CORRUPTION,   // System/power corrupts users
    EXPLORATION_RACE,      // Competition for discovery
    RESOURCE_SCARCITY,     // Limited resources cause conflict
    IDEOLOGICAL,           // Philosophical differences
    COSMIC_MYSTERY         // Unknown forces manipulating events
}

@Serializable
enum class ProgressionStyle {
    LINEAR,          // Clear path, one zone after another
    SEMI_LINEAR,     // Suggested path but exploration allowed
    OPEN_WORLD       // Go anywhere, emergent narrative
}

