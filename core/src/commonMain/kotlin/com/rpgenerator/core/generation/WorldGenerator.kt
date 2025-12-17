package com.rpgenerator.core.generation

import com.rpgenerator.core.api.*
import com.rpgenerator.core.util.currentTimeMillis

/**
 * World Generator - Creates dynamic world settings based on SystemType
 *
 * Instead of hardcoded worlds, this generates unique world lore for each playthrough
 * (if enabled). Can also provide default templates for each SystemType.
 */
internal class WorldGenerator(private val llm: LLMInterface?) {

    private val agentStream = llm?.startAgent(
        """
        You are the World Architect - creator of unique game worlds.

        Your job is to generate complete, coherent world settings that feel REAL.

        Key principles:
        1. INTERNAL CONSISTENCY - Rules must make sense together
        2. AVOID CLICHÉS - Don't default to overused tropes
        3. MYSTERY - Leave room for discovery
        4. STAKES - Make the world feel dangerous and meaningful
        5. FACTIONS - Create compelling groups with conflicting goals

        You will receive a SystemType and generate a complete world.
        """.trimIndent()
    )

    /**
     * Generate a unique world based on SystemType
     */
    suspend fun generateWorld(
        systemType: SystemType,
        seed: Long = currentTimeMillis()
    ): WorldSettings {
        // If no LLM available, return default world
        if (llm == null || agentStream == null) {
            return getDefaultWorld(systemType)
        }

        val prompt = """
            Generate a unique world for SystemType: ${systemType.name}

            System Type Context:
            ${getSystemTypeDescription(systemType)}

            Generate a complete world with:
            1. World Name - Evocative, memorable
            2. Core Concept - What is the power system? (The "System", cultivation, magic, etc.)
            3. Origin Story - How did this world/power come to be?
            4. Current State - What's the situation now?
            5. Factions (3-5) - Groups with different philosophies and goals

            Make it UNIQUE. Same genre, different execution each time.

            Respond in JSON format:
            {
                "worldName": "Name of the world/setting",
                "coreConcept": "What is the fundamental power system",
                "originStory": "How it all began",
                "currentState": "The world's current situation",
                "factions": [
                    {
                        "id": "faction_id",
                        "name": "Faction Name",
                        "motto": "Short motto/slogan",
                        "description": "2-3 paragraphs describing the faction",
                        "personality": "How they act and think",
                        "goals": ["goal1", "goal2"]
                    }
                ]
            }
        """.trimIndent()

        // TODO: Implement proper JSON parsing from LLM response
        // For now, return a placeholder based on systemType
        return getDefaultWorld(systemType)
    }

    /**
     * Get default world template for a SystemType
     */
    fun getDefaultWorld(systemType: SystemType): WorldSettings {
        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> getDefaultSystemIntegrationWorld()
            SystemType.CULTIVATION_PATH -> getDefaultCultivationWorld()
            SystemType.ARCANE_ACADEMY -> getDefaultArcaneAcademyWorld()
            // Add more as needed
            else -> getDefaultSystemIntegrationWorld()
        }
    }

    private fun getSystemTypeDescription(systemType: SystemType): String {
        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> """
                System apocalypse style - a cosmic force integrates reality into a game-like
                structure with levels, skills, classes. Think Primal Hunter, Defiance of the Fall.
            """.trimIndent()
            SystemType.CULTIVATION_PATH -> """
                Xianxia cultivation - spiritual realms, dao comprehension, pill refining, breaking
                through bottlenecks. Journey from mortal to immortal.
            """.trimIndent()
            SystemType.DEATH_LOOP -> """
                Roguelike progression - death makes you stronger, loop mechanics, learning from
                failures. Each death unlocks new knowledge or power.
            """.trimIndent()
            SystemType.ARCANE_ACADEMY -> """
                Magic school progression - learn spells, advance through academic ranks, magical
                research, rival students, dangerous experiments.
            """.trimIndent()
            SystemType.DUNGEON_DELVE -> """
                Classic dungeon crawling - explore dangerous dungeons, loot treasure, face monsters,
                permadeath stakes. Risk vs reward gameplay.
            """.trimIndent()
            SystemType.TABLETOP_CLASSIC -> """
                D&D style traditional fantasy - classes, alignment, tabletop RPG mechanics,
                dungeons and dragons adventures.
            """.trimIndent()
            SystemType.EPIC_JOURNEY -> """
                Middle-earth inspired epic quest - fellowship dynamics, world-threatening evil,
                hobbits to heroes journey, ancient lore.
            """.trimIndent()
            SystemType.HERO_AWAKENING -> """
                Superhero origin story - discover powers, learn to control them, face villains,
                hero vs vigilante choices, secret identity.
            """.trimIndent()
        }
    }

    // ========================
    // Default World Templates
    // ========================

    private fun getDefaultSystemIntegrationWorld(): WorldSettings {
        return WorldSettings(
            worldName = "The Integrated Earth",
            themes = listOf(
                WorldTheme.DARK_AND_GRITTY,
                WorldTheme.MYSTERIOUS,
                WorldTheme.POLITICAL_INTRIGUE,
                WorldTheme.PHILOSOPHICAL
            ),
            rules = WorldRules(
                hasRespawn = true,
                respawnPenalty = "XP loss, item durability damage, and temporary stat debuff",
                hasSafeZones = true,
                canLoseLevels = false,
                pvpEnabled = true,
                specialMechanics = listOf(
                    "System Assimilation - rapid leveling can alter personality",
                    "Tutorial Zones - pocket dimensions for newcomers",
                    "Zone restructuring - Earth transformed into game-like areas"
                )
            ),
            generationHints = GenerationHints(
                suggestedFactionCount = 4,
                conflictTypes = listOf(
                    ConflictType.FACTION_RIVALRY,
                    ConflictType.SURVIVAL,
                    ConflictType.IDEOLOGICAL,
                    ConflictType.COSMIC_MYSTERY
                ),
                npcPersonalityTone = "Varied - survivors processing trauma differently. Some embrace the System, others resist, most just trying to survive.",
                progressionStyle = ProgressionStyle.SEMI_LINEAR
            ),
            coreConcept = """
                The System is an ancient, multiversal framework that integrates entire realities into a
                game-like structure governed by quantifiable rules. No one knows its true origin - some
                believe it's a cosmic AI, others think it's the universe's natural evolution, and a few
                whisper it's the remnant of a civilization that transcended mortality.

                What's certain: the System converts abstract concepts like "skill" and "strength" into
                measurable stats. It grants powers through levels, classes, and achievements. Death becomes
                less permanent for some. Reality itself bends to accommodate its rules.

                The System doesn't explain itself. It simply... IS.
            """.trimIndent(),
            originStory = """
                On October 15th, 2025, at 3:47 PM UTC, every human on Earth simultaneously received the
                same message: "INTEGRATION COMMENCING. PREPARE FOR SYSTEM INITIALIZATION."

                No warning. No explanation. No choice.

                Within minutes, 8 billion people were pulled into Tutorial Zones across countless pocket
                dimensions. Modern civilization collapsed instantly - planes fell from the sky, hospitals
                went dark, nuclear plants melted down with no one to stop them. The old world ended in
                an afternoon.

                The System offered only one explanation: "Your species has reached the threshold. You may
                now participate in the Grand Design." What threshold? What design? The System won't say.

                Some theorize Earth finally developed true AI, triggering a cosmic filter. Others believe
                humanity's conflicts attracted attention from higher powers. A few think we're just the
                latest in an infinite series of integrations spanning eternity.

                The truth remains unknown. All that matters now is survival.
            """.trimIndent(),
            currentState = """
                The Earth you knew is gone - transformed into something new, something OTHER.

                During Integration, the System restructured the planet into Zones - distinct areas with
                their own rules, dangers, and resources. Where New York City once stood, now sprawls the
                "Shattered Metropolis," a dungeon-city filled with monsters and treasure. The Amazon
                became the "Verdant Labyrinth," where trees grow a mile high and ancient creatures hunt.

                Not everyone survived the Tutorial. Estimates suggest 40% of humanity died in the first
                week - the elderly, the very young, those who panicked or refused to adapt. Another 20%
                fell in the following months. 3.2 billion people remain, scattered across the restructured
                Earth in settlements that cling to Safe Zones.

                Technology still works, but the System is more reliable. Magic is real. Skills can be
                purchased. Death can be overcome - at a price. The old rules don't apply anymore.

                Some settlements are trying to rebuild civilization. Others have embraced the chaos.
                Most are just trying to survive another day.
            """.trimIndent()
        )
    }

    private fun getDefaultCultivationWorld(): WorldSettings {
        return WorldSettings(
            worldName = "The Azure Realm",
            themes = listOf(WorldTheme.CULTIVATION_JOURNEY, WorldTheme.POLITICAL_INTRIGUE, WorldTheme.PHILOSOPHICAL),
            rules = WorldRules(
                hasRespawn = false,
                respawnPenalty = null,
                hasSafeZones = true,
                canLoseLevels = true,
                pvpEnabled = true,
                specialMechanics = listOf(
                    "Breakthrough tribulations - must survive heavenly trials to advance realms",
                    "Dao comprehension - understanding cosmic truths grants power",
                    "Pill refinement - alchemical elixirs accelerate cultivation"
                )
            ),
            generationHints = GenerationHints(
                suggestedFactionCount = 5,
                conflictTypes = listOf(ConflictType.FACTION_RIVALRY, ConflictType.RESOURCE_SCARCITY, ConflictType.IDEOLOGICAL),
                npcPersonalityTone = "Honor-bound but ruthless in pursuit of immortality. Ancient powers are mysterious and dangerous.",
                progressionStyle = ProgressionStyle.SEMI_LINEAR
            ),
            coreConcept = "Cultivation of spiritual energy to ascend through immortal realms",
            originStory = "Ancient cultivators discovered the Dao and created the path to immortality",
            currentState = "Countless sects compete for resources while ancient powers stir"
        )
    }

    private fun getDefaultArcaneAcademyWorld(): WorldSettings {
        return WorldSettings(
            worldName = "Arcanum Collegiate",
            themes = listOf(WorldTheme.MYSTERIOUS, WorldTheme.POLITICAL_INTRIGUE, WorldTheme.EXPLORATION_FOCUSED),
            rules = WorldRules(
                hasRespawn = true,
                respawnPenalty = "Magical backlash damages spell capacity temporarily",
                hasSafeZones = true,
                canLoseLevels = false,
                pvpEnabled = false,
                specialMechanics = listOf(
                    "Academic ranks - progress through student, adept, scholar, archmage",
                    "Spell research - discover new magic through experimentation",
                    "Forbidden magic - powerful but dangerous spells hidden in restricted sections"
                )
            ),
            generationHints = GenerationHints(
                suggestedFactionCount = 3,
                conflictTypes = listOf(ConflictType.IDEOLOGICAL, ConflictType.EXPLORATION_RACE),
                npcPersonalityTone = "Academic and competitive. Professors are stern, students are ambitious, rebels are innovative.",
                progressionStyle = ProgressionStyle.LINEAR
            ),
            coreConcept = "Structured magical education through academic ranks",
            originStory = "The First Archmage codified magic into teachable disciplines",
            currentState = "The academy trains the next generation while dark magic resurges"
        )
    }
}
