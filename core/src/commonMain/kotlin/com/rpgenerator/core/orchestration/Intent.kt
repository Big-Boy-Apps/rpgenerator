package com.rpgenerator.core.orchestration

import kotlinx.serialization.Serializable

internal enum class Intent {
    COMBAT,
    NPC_DIALOGUE,
    EXPLORATION,
    SYSTEM_QUERY,
    QUEST_ACTION,
    CLASS_SELECTION,
    // Skill-related intents
    SKILL_MENU,         // View/manage skills
    USE_SKILL,          // Use a specific skill in combat
    SKILL_EVOLUTION,    // Evolve a max-level skill
    SKILL_FUSION,       // Fuse skills together
    // Menu intents
    STATUS_MENU,        // View detailed character status
    INVENTORY_MENU      // View/manage inventory
}

@Serializable
internal data class SystemResponse(
    val intent: Intent,
    val target: String? = null,
    val context: String
)
