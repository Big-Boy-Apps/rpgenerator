package com.rpgenerator.core.events

import kotlinx.serialization.Serializable

/**
 * Categories for game events to enable better filtering and context selection.
 */
@Serializable
enum class EventCategory {
    /**
     * Combat-related events (attacks, damage, kills).
     */
    COMBAT,

    /**
     * Dialogue and NPC interactions.
     */
    DIALOGUE,

    /**
     * Exploration, discovery, and movement.
     */
    EXPLORATION,

    /**
     * Quest-related events (start, progress, completion).
     */
    QUEST,

    /**
     * Inventory, equipment, and item usage.
     */
    INVENTORY,

    /**
     * Character progression (level up, stat changes, skills).
     */
    CHARACTER_PROGRESSION,

    /**
     * General narrative and scene descriptions.
     */
    NARRATIVE,

    /**
     * System notifications and meta events.
     */
    SYSTEM
}

/**
 * Importance weight for events to help with context selection.
 * Higher values = more important for AI context.
 */
@Serializable
enum class EventImportance(val weight: Int) {
    /**
     * Low importance - minor flavor text, trivial actions.
     */
    LOW(1),

    /**
     * Normal importance - standard game events.
     */
    NORMAL(3),

    /**
     * High importance - significant events, major decisions.
     */
    HIGH(5),

    /**
     * Critical importance - plot-critical events, major milestones.
     */
    CRITICAL(10)
}
