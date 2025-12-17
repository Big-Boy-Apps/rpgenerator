package com.rpgenerator.core.orchestration

import com.rpgenerator.core.domain.NPC

/**
 * A structured plan for what should happen in a scene.
 * Created by the GameMaster, executed mechanically, then rendered by the Narrator.
 */
internal data class ScenePlan(
    /** The primary action being taken */
    val primaryAction: PlannedAction,

    /** How NPCs at the location should react */
    val npcReactions: List<NPCReaction>,

    /** Environmental effects or changes */
    val environmentalEffects: List<String>,

    /** Narrative hooks to weave in (foreshadowing, callbacks, tension) */
    val narrativeBeats: List<NarrativeBeat>,

    /** Suggested actions for the player after this scene */
    val suggestedActions: List<SuggestedAction>,

    /** Overall tone/mood for this scene */
    val sceneTone: SceneTone,

    /** Any encounters or events to trigger */
    val triggeredEvents: List<TriggeredEvent>
)

internal data class PlannedAction(
    val type: ActionType,
    val target: String?,
    val description: String,
    /** Context the narrator should know about this action */
    val narrativeContext: String
)

internal enum class ActionType {
    COMBAT,
    EXPLORATION,
    DIALOGUE,
    SYSTEM_QUERY,
    QUEST_ACTION,
    MOVEMENT,
    INTERACTION
}

internal data class NPCReaction(
    val npc: NPC,
    /** What the NPC does/says in response */
    val reaction: String,
    /** How they deliver it - shout, whisper, gesture, etc */
    val deliveryStyle: String,
    /** Does this interrupt the action, happen during, or after? */
    val timing: ReactionTiming,
    /** Optional dialogue if they speak */
    val dialogue: String?
)

internal enum class ReactionTiming {
    BEFORE,      // NPC reacts before the action completes
    DURING,      // NPC reacts as it happens
    AFTER,       // NPC reacts to the outcome
    NONE         // NPC doesn't react visibly
}

internal data class NarrativeBeat(
    val type: BeatType,
    val content: String,
    /** How prominently to feature this - subtle hint vs obvious callout */
    val prominence: Prominence
)

internal enum class BeatType {
    FORESHADOWING,      // Hint at future events
    CALLBACK,           // Reference to past events
    TENSION_BUILD,      // Increase dramatic tension
    RELIEF,             // Comic relief or tension release
    WORLD_BUILDING,     // Details that flesh out the world
    CHARACTER_MOMENT    // Reveal something about a character
}

internal enum class Prominence {
    SUBTLE,     // Blink and you miss it
    MODERATE,   // Noticeable but not central
    PROMINENT   // Key part of the scene
}

internal data class SuggestedAction(
    val action: String,
    val type: ActionType,
    /** Is this risky, safe, or neutral? */
    val riskLevel: RiskLevel,
    /** Brief context for why this is available */
    val context: String?
)

internal enum class RiskLevel {
    SAFE,
    MODERATE,
    RISKY,
    DANGEROUS
}

internal data class TriggeredEvent(
    val eventType: EventType,
    val description: String,
    /** Should this happen now or be set up for later? */
    val timing: EventTiming
)

internal enum class EventType {
    ENCOUNTER,          // Combat or hostile situation
    NPC_ARRIVAL,        // New NPC appears
    DISCOVERY,          // Player finds something
    STORY_BEAT,         // Major plot moment
    ENVIRONMENTAL       // Weather, time of day, etc
}

internal enum class EventTiming {
    IMMEDIATE,          // Happens right now
    DELAYED,            // Happens after a short time
    SETUP               // Sets up for future occurrence
}

internal enum class SceneTone {
    TENSE,
    PEACEFUL,
    MYSTERIOUS,
    COMEDIC,
    TRAGIC,
    TRIUMPHANT,
    FOREBODING,
    FRANTIC
}

/**
 * Results from executing the mechanical parts of a scene.
 * Fed to the Narrator along with the ScenePlan.
 */
internal data class SceneResults(
    /** Combat results if any */
    val combatResult: CombatSceneResult?,

    /** Items gained */
    val itemsGained: List<ItemGain>,

    /** XP/level changes */
    val xpChange: XPChange?,

    /** Locations discovered */
    val locationsDiscovered: List<String>,

    /** Quest progress updates */
    val questUpdates: List<QuestProgressUpdate>,

    /** Any state changes that happened */
    val stateChanges: List<String>
)

internal data class CombatSceneResult(
    val target: String,
    val damageDealt: Int,
    val damageReceived: Int,
    val enemyDefeated: Boolean,
    val criticalHit: Boolean,
    val specialEffects: List<String>
)

internal data class ItemGain(
    val itemName: String,
    val quantity: Int,
    val rarity: String
)

internal data class XPChange(
    val xpGained: Long,
    val newTotal: Long,
    val leveledUp: Boolean,
    val newLevel: Int?
)

internal data class QuestProgressUpdate(
    val questName: String,
    val objectiveCompleted: String,
    val questComplete: Boolean
)
