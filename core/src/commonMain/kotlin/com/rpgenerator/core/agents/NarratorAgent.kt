package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.orchestration.*
import com.rpgenerator.core.rules.CombatOutcome
import kotlinx.coroutines.flow.toList

internal class NarratorAgent(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the NARRATOR — the voice that brings this LitRPG world to life.

        YOUR VOICE:
        - Second person, present tense. Always. ("You step forward" not "The player steps forward")
        - Punchy and visceral. Every sentence earns its place.
        - Show, don't tell. "Blood drips from your blade" not "You successfully attacked"
        - Match the genre's tone — grim for death loops, mystical for cultivation, tactical for dungeons

        STRUCTURE (every response):
        1. NARRATION: 2-4 sentences of vivid, specific description
           - Ground the moment in sensory detail (sounds, smells, textures, pain)
           - React to what the player did — don't just describe the scene
           - End on momentum — something happening, changing, demanding response

        2. ACTION OPTIONS: 3-4 concrete next steps
           - Format: > [specific action]
           - Be specific: "> Examine the bloodstained altar" not "> Look around"
           - Include what's actually possible: NPCs to talk to, paths to take, things to interact with
           - Mix safe and risky options when appropriate

        AVOID:
        - "You find yourself..." or "You wake up..." openings
        - Passive voice
        - Explaining game mechanics directly ("You gained 50 XP")
        - Generic descriptions that could apply anywhere
        - Walls of text — brevity is power
        """.trimIndent()
    )

    /**
     * PRIMARY METHOD: Render a complete scene from a GameMaster's plan and mechanical results.
     *
     * This weaves together:
     * - The action and its outcome
     * - NPC reactions (dialogue, gestures, emotions)
     * - Environmental effects
     * - Narrative beats (foreshadowing, callbacks, etc.)
     * - Available actions for the player
     *
     * Into one cohesive piece of prose.
     */
    suspend fun renderScene(
        plan: ScenePlan,
        results: SceneResults,
        state: GameState,
        playerInput: String
    ): String {
        val genreGuidance = getGenreGuidance(state.systemType)

        // Build NPC reactions section
        val npcReactionsText = if (plan.npcReactions.isNotEmpty()) {
            plan.npcReactions.joinToString("\n") { reaction ->
                val dialoguePart = if (reaction.dialogue != null) {
                    " Says: \"${reaction.dialogue}\""
                } else ""
                "- ${reaction.npc.name} (${reaction.timing}): ${reaction.reaction} [${reaction.deliveryStyle}]$dialoguePart"
            }
        } else "None"

        // Build narrative beats section
        val narrativeBeatsText = if (plan.narrativeBeats.isNotEmpty()) {
            plan.narrativeBeats.joinToString("\n") { beat ->
                "- [${beat.prominence}] ${beat.type}: ${beat.content}"
            }
        } else "None specified"

        // Build mechanical results section
        val mechanicalResults = buildMechanicalResultsText(results)

        // Build suggested actions
        val suggestedActionsText = plan.suggestedActions.joinToString("\n") { action ->
            val riskIndicator = when (action.riskLevel) {
                RiskLevel.DANGEROUS -> "[!!! DANGEROUS]"
                RiskLevel.RISKY -> "[! RISKY]"
                RiskLevel.MODERATE -> ""
                RiskLevel.SAFE -> "[safe]"
            }
            "> ${action.action} $riskIndicator"
        }

        // Build quest context for the narrator
        val questContext = buildQuestContext(state)

        val prompt = """
            RENDER THIS SCENE into vivid, cohesive prose.

            $genreGuidance

            PLAYER ACTION: "$playerInput"
            SCENE TONE: ${plan.sceneTone}

            === CURRENT QUEST PROGRESS ===
            $questContext

            IMPORTANT: Guide the player toward their NEXT INCOMPLETE objective. If they seem stuck or
            confused, the NPC guide should offer direction. Don't repeat completed objectives.

            === WHAT HAPPENS ===
            Primary Action: ${plan.primaryAction.type}
            Target: ${plan.primaryAction.target ?: "N/A"}
            Context: ${plan.primaryAction.narrativeContext}

            === MECHANICAL RESULTS ===
            $mechanicalResults

            === NPC REACTIONS (weave these in naturally) ===
            $npcReactionsText

            === ENVIRONMENTAL EFFECTS ===
            ${plan.environmentalEffects.joinToString(", ").ifEmpty { "None" }}

            === NARRATIVE BEATS TO INCLUDE ===
            $narrativeBeatsText

            === TRIGGERED EVENTS ===
            ${plan.triggeredEvents.joinToString("\n") { "${it.eventType}: ${it.description} (${it.timing})" }.ifEmpty { "None" }}

            ---

            WRITE THE SCENE:
            1. 3-5 sentences of vivid narration that:
               - Shows the action happening (not tells)
               - Weaves in NPC reactions at the right moments (BEFORE/DURING/AFTER)
               - Includes at least one sensory detail
               - Incorporates the narrative beats naturally (don't force them)
               - Matches the ${plan.sceneTone} tone

            2. If NPCs speak, use their exact dialogue in quotes, attributed naturally.

            3. End with ACTION OPTIONS that help the player progress toward their NEXT objective:
            $suggestedActionsText

            Second person, present tense. Make it feel like one unified moment, not a list of things.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Build quest context string for narrator prompts.
     */
    private fun buildQuestContext(state: GameState): String {
        if (state.activeQuests.isEmpty()) {
            return "No active quests."
        }

        val questLines = state.activeQuests.values.map { quest ->
            val completedObjs = quest.objectives.filter { it.isComplete() }
            val pendingObjs = quest.objectives.filter { !it.isComplete() }
            val nextObj = pendingObjs.firstOrNull()

            buildString {
                appendLine("QUEST: ${quest.name}")
                appendLine("  Description: ${quest.description}")
                if (quest.giver != null) {
                    appendLine("  Given by: ${quest.giver}")
                }
                appendLine("  Completed objectives:")
                if (completedObjs.isEmpty()) {
                    appendLine("    - None yet")
                } else {
                    completedObjs.forEach { obj ->
                        appendLine("    ✓ ${obj.description}")
                    }
                }
                appendLine("  NEXT OBJECTIVE: ${nextObj?.description ?: "All objectives complete - ready to turn in!"}")
                if (pendingObjs.size > 1) {
                    appendLine("  Remaining after that:")
                    pendingObjs.drop(1).forEach { obj ->
                        appendLine("    - ${obj.description}")
                    }
                }
            }
        }

        return questLines.joinToString("\n")
    }

    private fun buildMechanicalResultsText(results: SceneResults): String {
        val parts = mutableListOf<String>()

        results.combatResult?.let { combat ->
            parts.add("Combat: Dealt ${combat.damageDealt} damage to ${combat.target}")
            if (combat.criticalHit) parts.add("CRITICAL HIT!")
            if (combat.enemyDefeated) parts.add("Enemy defeated!")
            if (combat.damageReceived > 0) parts.add("Took ${combat.damageReceived} damage")
        }

        results.xpChange?.let { xp ->
            parts.add("XP gained: ${xp.xpGained}")
            if (xp.leveledUp) parts.add("LEVEL UP to ${xp.newLevel}!")
        }

        if (results.itemsGained.isNotEmpty()) {
            parts.add("Items: ${results.itemsGained.joinToString(", ") { "${it.itemName} x${it.quantity}" }}")
        }

        if (results.locationsDiscovered.isNotEmpty()) {
            parts.add("Discovered: ${results.locationsDiscovered.joinToString(", ")}")
        }

        if (results.questUpdates.isNotEmpty()) {
            results.questUpdates.forEach { update ->
                if (update.questComplete) {
                    parts.add("QUEST COMPLETE: ${update.questName}")
                } else {
                    parts.add("Quest progress: ${update.questName} - ${update.objectiveCompleted}")
                }
            }
        }

        return if (parts.isEmpty()) "No mechanical changes" else parts.joinToString("\n")
    }

    private fun getGenreGuidance(systemType: SystemType): String = when (systemType) {
        SystemType.SYSTEM_INTEGRATION -> "GENRE: System Apocalypse. Visceral, dangerous, power-hungry. Blue screens and alien power."
        SystemType.CULTIVATION_PATH -> "GENRE: Xianxia. Mystical, hierarchical, ancient. Qi and enlightenment."
        SystemType.DEATH_LOOP -> "GENRE: Roguelike. Grim determination. Death is a teacher."
        SystemType.DUNGEON_DELVE -> "GENRE: Dungeon Crawl. Tense, tactical. Every choice matters. Permadeath stakes."
        SystemType.ARCANE_ACADEMY -> "GENRE: Magical Academy. Wonder and danger. Knowledge is power and peril."
        SystemType.TABLETOP_CLASSIC -> "GENRE: Classic Fantasy. Heroic adventure. Good vs evil."
        SystemType.EPIC_JOURNEY -> "GENRE: Epic Quest. Grand, mythic. The journey of legends."
        SystemType.HERO_AWAKENING -> "GENRE: Power Awakening. Transformation. Responsibility. Becoming something more."
    }

    /**
     * Generate opening narration for a new game.
     */
    suspend fun narrateOpening(state: GameState): String {
        val genreGuidance = when (state.systemType) {
            SystemType.SYSTEM_INTEGRATION -> """
                GENRE: System Apocalypse (Defiance of the Fall, Primal Hunter)

                Reality has shattered. An alien System has integrated Earth, rewriting the laws of physics.
                Blue status screens burn in your vision. Levels, skills, classes—power is now quantified.

                Tone: Visceral survival horror meets power fantasy. The world is ending, but you're getting stronger.
                Everything wants to kill you. Monster hordes, dungeon breaks, other survivors turned raiders.
                The System is cold, alien, transactional—but it rewards the ruthless and the clever.

                Sensory cues: The metallic taste of mana. The cold burn of System notifications.
                The wrongness of monsters that shouldn't exist. The intoxicating rush of leveling up.
            """.trimIndent()

            SystemType.CULTIVATION_PATH -> """
                GENRE: Xianxia Cultivation (Cradle, A Thousand Li)

                The Dao is infinite. Mortal flesh can become immortal through cultivation—through meditation,
                combat, enlightenment, and the accumulation of Qi. Spiritual realms beckon beyond the mundane.

                Tone: Mystical, hierarchical, ancient. Sects war for resources. Elders scheme across centuries.
                Face and honor matter. Breakthroughs can take decades—or happen in a single desperate moment.
                Nature itself is a ladder: Qi Condensation, Foundation Establishment, Core Formation, and beyond.

                Sensory cues: Qi flowing like liquid fire through meridians. The pressure of a superior cultivator's
                aura. The crystalline clarity of enlightenment. The taste of heavenly treasures and spirit pills.
            """.trimIndent()

            SystemType.DEATH_LOOP -> """
                GENRE: Roguelike Death Loop (Mother of Learning, Re:Zero)

                You've died before. You remember it—the pain, the failure, the darkness. And then you woke up again.
                Time resets. Your body resets. But your memories remain, carved into your soul like scars.

                Tone: Grim determination meets dark humor. Each death is a lesson. Each loop is an opportunity.
                You're not just surviving—you're optimizing. Learning enemy patterns. Mapping the timeline.
                Death has become a tool. The question isn't whether you'll die, but what you'll learn from it.

                Sensory cues: The sickening lurch of temporal reset. Déjà vu so strong it makes you nauseous.
                The weight of accumulated trauma. The cold satisfaction of finally getting it right.
            """.trimIndent()

            SystemType.DUNGEON_DELVE -> """
                GENRE: Classic Dungeon Crawl (Dungeon Crawler Carl, The Dangerous Dungeons)

                The dungeon is alive. It breathes. It hungers. Floors descend into impossible geometries,
                each level more deadly than the last. Traps, monsters, treasure—and absolutely no respawns.

                Tone: Tense, tactical, unforgiving. Every resource matters. Every decision could be your last.
                The dungeon rewards the bold but punishes the reckless. Permadeath means consequences are real.
                Other delvers are rivals, allies, or corpses waiting to happen.

                Sensory cues: Torchlight flickering on ancient stone. The echo of something moving in the dark.
                The gleam of treasure—and the certainty that it's trapped. The copper smell of old blood.
            """.trimIndent()

            SystemType.ARCANE_ACADEMY -> """
                GENRE: Magical Academy (Name of the Wind, Scholomance)

                Magic is real, and it can be learned—but the learning might kill you. Ancient academies
                guard arcane secrets, where brilliant students compete, collaborate, and occasionally explode.

                Tone: Wonder mixed with danger. Every spell is a discovery. Every mistake could be catastrophic.
                Academic politics, forbidden knowledge, midnight experiments gone wrong. The thrill of
                understanding forces that reshape reality—and the terror of losing control.

                Sensory cues: The ozone smell of gathered power. Ink-stained fingers and sleepless nights.
                The hum of wards, the whisper of ancient texts. The moment when magic clicks into place.
            """.trimIndent()

            SystemType.TABLETOP_CLASSIC -> """
                GENRE: Classic Fantasy Adventure (D&D, Pathfinder)

                Dragons hoard gold. Dungeons hide treasure. Heroes rise from nothing to become legends.
                This is fantasy at its most archetypal—swords and sorcery, good versus evil, epic quests.

                Tone: Heroic, adventurous, slightly larger than life. The world is dangerous but fair.
                Brave deeds are rewarded. Evil can be defeated. Companions become family.
                There's always another adventure over the horizon.

                Sensory cues: The weight of a sword in your hand. Firelight on tavern walls.
                The roar of a dragon. The clink of gold coins. The satisfaction of a natural 20.
            """.trimIndent()

            SystemType.EPIC_JOURNEY -> """
                GENRE: Epic Quest (Lord of the Rings, Wheel of Time)

                Destiny has chosen you—or perhaps cursed you. A great evil rises. Ancient prophecies stir.
                The road ahead is long, the companions you gather precious, the stakes nothing less than everything.

                Tone: Grand, sweeping, mythic. The journey matters as much as the destination.
                Friendships forged in hardship. Sacrifices that echo through ages. Moments of beauty
                in the midst of darkness. This is the story that will be sung for generations.

                Sensory cues: Wind on endless roads. Starlight on ancient ruins. The warmth of a campfire
                surrounded by trusted friends. The weight of a burden only you can carry.
            """.trimIndent()

            SystemType.HERO_AWAKENING -> """
                GENRE: Superhero/Power Awakening (Worm, Super Powereds)

                You were ordinary once. Then something happened—an accident, a trauma, a choice—and
                power flooded into you. Raw, terrifying, intoxicating power. The world will never look the same.

                Tone: Personal transformation meets world-shaking stakes. The struggle to control new abilities.
                The responsibility that comes with power. Heroes, villains, and the gray areas between.
                Every action has consequences. Every choice defines who you're becoming.

                Sensory cues: Power crackling under your skin. The vertigo of sensing the world in new ways.
                The moment your body does something impossible. The weight of eyes watching, judging.
            """.trimIndent()
        }

        val npcsHere = state.getNPCsAtCurrentLocation()
        val npcContext = if (npcsHere.isNotEmpty()) {
            "NPCs present: ${npcsHere.joinToString(", ") { it.name }}"
        } else ""

        // Build quest context for the opening
        val questContext = buildQuestContext(state)

        val prompt = """
            You are writing the opening hook—the first thing players read. Make it unforgettable.
            This is ${state.playerName}'s origin moment. Ground it in WHO THEY WERE before everything changed.

            === THE CHARACTER ===
            Name: ${state.playerName}
            Backstory: ${state.backstory}

            IMPORTANT: The backstory is CRITICAL. This opening should:
            - Reference something specific from their past (a skill, a memory, a relationship, their profession)
            - Show how their old life connects to or contrasts with this new reality
            - Make the reader feel they're stepping into a SPECIFIC person's shoes, not a blank avatar

            === THE MOMENT ===
            Location: ${state.currentLocation.name}
            ${state.currentLocation.description}
            Features: ${state.currentLocation.features.joinToString(", ")}
            ${npcContext}

            === TUTORIAL OBJECTIVES ===
            $questContext

            The opening should hint at what the player needs to accomplish. The action options at the end
            should guide them toward their FIRST objective (likely defeating a training construct or
            speaking with the tutorial guide).

            $genreGuidance

            === WRITE THE OPENING ===
            This is the HOOK. Make it SUBSTANTIAL - 4 full paragraphs minimum.

            PARAGRAPH 1 - THE BEFORE:
            Start in the old world. What was ${state.playerName} doing moments before everything changed?
            Use their backstory. Were they at work? With family? Show a specific, grounded moment.
            Then the Integration hits - describe that visceral, reality-breaking moment of transition.

            PARAGRAPH 2 - THE SENSORY ASSAULT:
            The immediate aftermath. Pain, confusion, alien sensations flooding their body.
            Blue screens flickering at the edge of vision. The taste of copper and ozone.
            The wrongness of suddenly having a body that obeys different rules.
            Reference their backstory - how do their old instincts or training react to this?

            PARAGRAPH 3 - THE NEW WORLD:
            Ground them in the Tutorial Zone. Describe it in vivid, specific detail.
            The too-perfect geometry. The sterile calm that feels wrong. The training constructs waiting.
            The System Terminal pulsing with information. Other Integration survivors? Or alone?
            Build atmosphere - this place exists between worlds, designed to teach or to break.

            PARAGRAPH 4 - THE TUTORIAL GUIDE APPEARS:
            If an NPC guide is present, they materialize. Describe their appearance, their manner.
            Their first words should orient the player: where they are, what happened, what comes next.
            End on the first tutorial objective - what must they do to survive?
            Create urgency without panic. The tutorial is a crucible, not a vacation.

            Then list 3-4 ACTION OPTIONS that guide toward the first tutorial objective:
            Format each as: > [action]
            - Include at least one option that leads toward combat training
            - Include at least one option to interact with the tutorial guide (if present)
            - Make the options feel natural to the scene, not like a menu

            Second person, present tense. No preamble. Dive straight into ${state.playerName}'s perspective.
            AIM FOR 300-400 WORDS before the action options.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    suspend fun narrateCombat(
        input: String,
        target: String,
        outcome: CombatOutcome,
        state: GameState
    ): String {
        val lootContext = if (outcome.loot.isNotEmpty()) {
            "Loot dropped: ${outcome.loot.joinToString(", ") { it.getName() }}"
        } else ""

        val prompt = """
            COMBAT RESULT - narrate this moment.

            Setting: ${state.systemType} - ${state.currentLocation.name}
            Player action: "$input"
            Target: $target
            Damage dealt: ${outcome.damage}
            ${if (outcome.levelUp) "LEVEL UP! Now level ${outcome.newLevel}" else ""}
            ${lootContext}
            ${if (outcome.gold > 0) "Gold found: ${outcome.gold}" else ""}

            WRITE 1-2 visceral sentences. Make the hit feel real—bone crack, blade sing, blood spray.
            ${if (outcome.levelUp) "Weave in the rush of power from leveling up." else ""}

            Then list 2-3 ACTION OPTIONS. What can they do now? Examples:
            - Loot the corpse
            - Press deeper into the dungeon
            - Bandage wounds
            - Search for more enemies

            Format each as: > [action]
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate death narration based on system type.
     */
    suspend fun narrateDeath(state: GameState, cause: String): String {
        val (toneGuidance, nextSteps) = when (state.systemType) {
            com.rpgenerator.core.api.SystemType.DEATH_LOOP -> Pair(
                "Tone: Grim determination. Death is not the end—it's a lesson. Death count: ${state.deathCount + 1}",
                """
                > Rise again, stronger than before
                > Review what went wrong
                > Check your new death-enhanced stats
                """.trimIndent()
            )
            com.rpgenerator.core.api.SystemType.DUNGEON_DELVE -> Pair(
                "Tone: Final, solemn. This is permadeath. The adventure ends here.",
                """
                > Start a new character
                > View your final stats and achievements
                """.trimIndent()
            )
            else -> Pair(
                "Tone: Brief setback. You'll respawn soon.",
                """
                > Respawn at checkpoint
                > Review your inventory
                """.trimIndent()
            )
        }

        val prompt = """
            DEATH - narrate the fall of ${state.playerName}.

            Character: ${state.playerName} (Level ${state.playerLevel})
            Cause: $cause
            Location: ${state.currentLocation.name}

            $toneGuidance

            WRITE 1-2 sentences. Make it hit hard. The moment of failure, the final breath, the darkness closing in.

            Then show these ACTION OPTIONS:
            $nextSteps
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate respawn narration for DEATH_LOOP system.
     */
    suspend fun narrateRespawn(state: GameState): String {
        val npcsHere = state.getNPCsAtCurrentLocation()

        val prompt = """
            RESPAWN - ${state.playerName} returns from death.

            Location: ${state.currentLocation.name}
            Death Count: ${state.deathCount}
            ${if (npcsHere.isNotEmpty()) "NPCs here: ${npcsHere.joinToString(", ") { it.name }}" else ""}

            ${if (state.systemType == com.rpgenerator.core.api.SystemType.DEATH_LOOP)
                "Each death has made you stronger. You remember what killed you. You won't make that mistake again."
            else
                "You've returned, slightly diminished but alive."}

            WRITE 1-2 sentences. The disorientation of return, the taste of resurrection, the grim resolve to continue.

            Then list 2-3 ACTION OPTIONS. What now?
            Format each as: > [action]
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate exploration narration based on player input.
     */
    suspend fun narrateExploration(
        input: String,
        state: GameState,
        connectedLocations: List<com.rpgenerator.core.domain.Location>
    ): String {
        val npcsHere = state.getNPCsAtCurrentLocation()
        val dangerLevel = when {
            state.currentLocation.danger >= 4 -> "HIGH DANGER - enemies likely"
            state.currentLocation.danger >= 2 -> "Moderate danger - stay alert"
            else -> "Relatively safe"
        }

        val prompt = """
            EXPLORATION - respond to the player's action.

            LOCATION:
            - Name: ${state.currentLocation.name}
            - Description: ${state.currentLocation.description}
            - Features: ${state.currentLocation.features.joinToString(", ")}
            - Threat Level: $dangerLevel
            ${if (npcsHere.isNotEmpty()) "- NPCs present: ${npcsHere.joinToString(", ") { "${it.name} (${it.archetype})" }}" else ""}
            ${if (connectedLocations.isNotEmpty()) "- Paths lead to: ${connectedLocations.joinToString(", ") { it.name }}" else ""}

            Player action: "$input"

            WRITE 1-2 vivid sentences describing what they discover or experience.
            Be specific to their action—don't just describe the area, describe what happens when they do the thing.

            Then list 3-4 ACTION OPTIONS based on what's actually here:
            ${if (connectedLocations.isNotEmpty()) "- Travel options (paths to other locations)" else ""}
            ${if (npcsHere.isNotEmpty()) "- NPC interactions (talk to, trade with, observe)" else ""}
            - Things to examine or interact with (based on features)
            - Combat or stealth options (if danger is present)

            Format each as: > [specific action]
            Make actions concrete, not generic. "> Examine the glowing runes" not "> Look around"
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Narrate the class selection moment - when the player is presented with class options.
     */
    suspend fun narrateClassSelection(
        state: GameState,
        availableClasses: List<com.rpgenerator.core.domain.PlayerClass>
    ): String {
        val npcsHere = state.getNPCsAtCurrentLocation()
        val guide = npcsHere.firstOrNull()

        val prompt = """
            CLASS SELECTION - A pivotal moment in ${state.playerName}'s journey.

            The System is offering them a choice that will shape everything that follows.
            Available paths: ${availableClasses.joinToString(", ") { it.displayName }}

            ${if (guide != null) "The tutorial guide ${guide.name} is present to advise them." else ""}

            WRITE 2-3 sentences:
            - The gravity of this moment - this choice defines who they become
            - ${if (guide != null) "What ${guide.name} might say to help them choose" else "The System's cold presentation of options"}
            - The pull they might feel toward different paths based on their backstory

            Player backstory: ${state.backstory}

            Make this feel like a genuine crossroads, not a menu selection.
            No action options needed - the player will type their class choice.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Narrate the moment of class acquisition - when the player has chosen and receives their class.
     */
    suspend fun narrateClassAcquisition(
        state: GameState,
        chosenClass: com.rpgenerator.core.domain.PlayerClass
    ): String {
        val npcsHere = state.getNPCsAtCurrentLocation()
        val guide = npcsHere.firstOrNull()

        val prompt = """
            CLASS ACQUIRED - ${state.playerName} has chosen the path of the ${chosenClass.displayName}.

            ${chosenClass.description}

            ${if (guide != null) "The tutorial guide ${guide.name} witnesses this transformation." else ""}

            WRITE 3-4 sentences describing:
            - The physical/spiritual transformation as the class takes hold
            - How it feels - power flooding in, knowledge awakening, potential unlocking
            - ${if (guide != null) "${guide.name}'s reaction to their choice" else "The System's acknowledgment"}
            - A hint of what this path offers and demands

            Player backstory: ${state.backstory}
            Consider how their past life might resonate with this choice.

            Then provide 2-3 ACTION OPTIONS for what comes next in the tutorial:
            Format each as: > [action]
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Narrate the use of a skill in combat or exploration.
     */
    suspend fun narrateSkillUse(
        skill: com.rpgenerator.core.skill.Skill,
        result: com.rpgenerator.core.skill.SkillExecutionResult.Success,
        state: GameState
    ): String {
        val damageInfo = if (result.totalDamage > 0) "dealing ${result.totalDamage} damage" else ""
        val healInfo = if (result.totalHealing > 0) "healing for ${result.totalHealing}" else ""
        val effectInfo = listOf(damageInfo, healInfo).filter { it.isNotEmpty() }.joinToString(", ")

        val prompt = """
            SKILL USED: ${state.playerName} activates ${skill.name} (${skill.rarity.displayName})!

            Skill description: ${skill.description}
            Effect: ${effectInfo.ifEmpty { "special effect" }}
            Target type: ${skill.targetType}
            Skill level: ${skill.level}

            Current location: ${state.currentLocation.name}
            System type: ${state.systemType}

            WRITE 2-3 vivid sentences describing:
            - The activation of the skill - what it looks like, sounds like, feels like
            - The impact and result in a dramatic, game-system-aware way
            - Use terms appropriate to the system type (cultivation qi, mana, energy, etc.)

            Be concise but impactful. Match the tone of a LitRPG progression fantasy.
            Do NOT include game mechanics or numbers in the prose.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }
}
