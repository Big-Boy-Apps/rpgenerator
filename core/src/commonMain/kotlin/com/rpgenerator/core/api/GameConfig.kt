package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * Configuration for starting a new game.
 */
@Serializable
data class GameConfig(
    val systemType: SystemType,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val characterCreation: CharacterCreationOptions = CharacterCreationOptions(),
    val playerPreferences: Map<String, String> = emptyMap(),

    /**
     * World settings (lore, factions, narrative structure).
     * If null, a default world will be generated based on systemType.
     */
    val worldSettings: WorldSettings? = null
)

/**
 * Type of LitRPG system that governs the game.
 */
@Serializable
enum class SystemType {
    /** Standard System apocalypse - powers, levels, skills (Defiance of the Fall, Primal Hunter style) */
    SYSTEM_INTEGRATION,

    /** Pure xianxia cultivation - spiritual realms, dao comprehension, enlightenment */
    CULTIVATION_PATH,

    /** Death makes you stronger - roguelike progression through failures */
    DEATH_LOOP,

    /** Classic dungeon crawling with permadeath stakes */
    DUNGEON_DELVE,

    /** Magic academy progression - learn spells, advance through ranks */
    ARCANE_ACADEMY,

    /** D&D style - classes, attributes, alignment, traditional tabletop RPG */
    TABLETOP_CLASSIC,

    /** Middle-earth inspired - hobbits to heroes, fellowship dynamics */
    EPIC_JOURNEY,

    /** Superhero origin story - discover and grow your powers */
    HERO_AWAKENING
}

/**
 * Game difficulty level.
 */
@Serializable
enum class Difficulty {
    EASY,
    NORMAL,
    HARD,
    NIGHTMARE
}
