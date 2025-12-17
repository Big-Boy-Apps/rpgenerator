package com.rpgenerator.core.story

/**
 * The main story progression - hardcoded narrative beats that trigger at specific points.
 * These provide structure and direction to the player's journey.
 */
internal object MainStoryArc {

    /**
     * Story Beat - a fixed narrative moment
     */
    data class StoryBeat(
        val id: String,
        val title: String,
        val act: Int,
        val triggerLevel: Int,
        val triggerLocation: String?,
        val narration: String,
        val consequences: Map<String, String> = emptyMap()
    )

    /**
     * Main Quest - guides player progression
     */
    data class MainQuest(
        val id: String,
        val title: String,
        val act: Int,
        val levelRange: IntRange,
        val description: String,
        val objectives: List<String>,
        val reward: String
    )

    // ========================
    // ACT 1: THE INTEGRATION
    // ========================

    val TUTORIAL_STORY_BEATS = listOf(
        StoryBeat(
            id = "beat_first_words",
            title = "First Words",
            act = 1,
            triggerLevel = 1,
            triggerLocation = "tutorial_zone",
            narration = """
                As the disorientation of Integration fades, a figure materializes before you.
                She's dressed in what looks like a blend of modern business attire and fantasy
                armor - pragmatic boots, fitted jacket with glowing blue circuit-patterns, and an
                expression that's seen this confusion a thousand times before.

                "Deep breaths," she says, voice calm but not unkind. "I'm Administrator Aria. You're
                in a Tutorial Zone - a pocket dimension designed to teach you the basics without,
                you know, immediately dying. And before you ask: no, this isn't a dream. Yes, this
                is permanent. No, I can't send you back. Questions?"

                She doesn't wait for an answer. "Good. Because we have work to do. The System
                doesn't grade on effort - it grades on results. Let's make sure you survive long
                enough to see them."
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_first_monster",
            title = "First Blood",
            act = 1,
            triggerLevel = 2,
            triggerLocation = "tutorial_zone",
            narration = """
                You've killed your first monster - a training construct, really, but it fought back
                with real teeth and claws. Your hands are shaking. Aria watches you process this.

                "First kill's always weird," she says quietly. "These aren't real creatures - they're
                System constructs, spawned for training. But out there?" She gestures vaguely at the
                shimmering barrier surrounding the Tutorial. "Out there, everything that wants to kill
                you is very, VERY real."

                She pauses, then adds: "The System doesn't care if you're ready. It doesn't care if
                this is fair. It only cares if you adapt. So... are you going to adapt?"
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_the_choice",
            title = "The First Choice",
            act = 1,
            triggerLevel = 3,
            triggerLocation = "tutorial_zone",
            narration = """
                Aria calls you over to a shimmering portal. "You're ready to leave the Tutorial.
                Most people graduate at level 3, and you've hit it. But here's something they don't
                tell you up front: you have a choice."

                She points to two portals, each showing a different destination.

                "Safe Path: 'The Threshold' - a fortified settlement where thousands of survivors
                live. Warden Kade runs it tight. You'll be protected, trained properly, and have a
                community. But you'll also be weak compared to those who took risks early."

                "Risky Path: 'The Fringe' - barely-cleared zones where resources are rich and death
                is common. You'll level faster if you survive. Most don't. But those who do..."

                She meets your eyes. "I can't tell you which to choose. The System doesn't care about
                safe or risky - it only cares who levels. What do you care about?"
            """.trimIndent(),
            consequences = mapOf(
                "choice_safe_path" to "Starts in Threshold settlement",
                "choice_risky_path" to "Starts in Fringe zones"
            )
        )
    )

    val ACT_1_MAIN_QUESTS = listOf(
        MainQuest(
            id = "quest_survive_tutorial",
            title = "Survive the Tutorial",
            act = 1,
            levelRange = 1..3,
            description = "Learn the basics of the System and reach level 3",
            objectives = listOf(
                "Defeat your first training construct",
                "Allocate your first stat points",
                "Learn a basic skill",
                "Complete Administrator Aria's combat assessment"
            ),
            reward = "Tutorial graduation, choice of starting zone"
        ),

        MainQuest(
            id = "quest_first_week",
            title = "The First Week",
            act = 1,
            levelRange = 3..10,
            description = "Survive your first week in the real world and establish yourself",
            objectives = listOf(
                "Choose your path (Safe or Risky)",
                "Join or establish a settlement",
                "Reach level 10",
                "Complete your first real hunt"
            ),
            reward = "Settlement citizenship, access to faction quests"
        )
    )

    // ========================
    // ACT 2: UNDERSTANDING THE SYSTEM
    // ========================

    val ACT_2_STORY_BEATS = listOf(
        StoryBeat(
            id = "beat_not_everyone_equal",
            title = "Different Systems",
            act = 2,
            triggerLevel = 10,
            triggerLocation = null,
            narration = """
                You've been hearing rumors, but now you've seen proof: not everyone got the same
                System. Most people have access to basic classes - Warrior, Mage, Rogue. Standard stuff.

                But some people? They have unique classes. Hidden paths. Powers that shouldn't exist.
                One person you met can rewind time by 10 seconds. Another claims they can see "decision
                trees" - branching futures based on choices.

                The System categorizes people. Ranks them. Not everyone starts equal, and the gap only
                widens. The question burning in your mind: where do YOU rank? Are you common or unique?
                And what does that mean for your survival?
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_the_price",
            title = "The Price of Power",
            act = 2,
            triggerLevel = 15,
            triggerLocation = null,
            narration = """
                You've learned something disturbing: rapid leveling has a cost. Not experience points -
                something deeper. You've met people who leveled too fast, pushed too hard, and came back...
                wrong.

                They're still human, technically. But they think differently. Their emotions flatten.
                Their priorities shift. They start talking about "optimization" and "efficient resource
                allocation" when discussing whether to save someone's life.

                Dr. Voss calls it "System Assimilation" - the more you embrace the System's logic, the
                more it shapes your thinking. The Remnant calls it "losing your soul." The Loyalists
                call it "evolution."

                Whatever it is, you've felt it touching your thoughts. The urge to reduce everything
                to numbers. To optimize. To treat people as NPCs.

                You're not there yet. But you can feel which direction you're heading.
            """.trimIndent()
        )
    )

    val ACT_2_MAIN_QUESTS = listOf(
        MainQuest(
            id = "quest_faction_choice",
            title = "Choose Your Path",
            act = 2,
            levelRange = 10..15,
            description = "Decide which faction (if any) represents your values",
            objectives = listOf(
                "Meet representatives from each major faction",
                "Complete a quest for at least two factions",
                "Decide your alignment (or remain independent)",
                "Reach level 15"
            ),
            reward = "Faction reputation, access to faction resources"
        ),

        MainQuest(
            id = "quest_uncover_truth",
            title = "Hidden Mechanics",
            act = 2,
            levelRange = 15..25,
            description = "Discover the secrets the System doesn't advertise",
            objectives = listOf(
                "Find a hidden class path",
                "Unlock a secret skill",
                "Discover the truth about System Assimilation",
                "Reach level 25"
            ),
            reward = "Unique class evolution, expanded System knowledge"
        )
    )

    // ========================
    // ACT 3: THE FIRST CRISIS
    // ========================

    val ACT_3_STORY_BEATS = listOf(
        StoryBeat(
            id = "beat_convergence_warning",
            title = "The Convergence",
            act = 3,
            triggerLevel = 25,
            triggerLocation = null,
            narration = """
                System Announcement: CONVERGENCE EVENT IN 30 DAYS.

                The message appears to every integrated being simultaneously. No explanation. Just a
                countdown and coordinates. Speculation runs rampant - is it a mass PvP event? A raid
                boss? The final test?

                The Broker appears to you specifically, which has never happened before. Their smile
                is unsettling. "Convergence is when the System decides who stays and who... doesn't.
                Think of it as a performance review. For an entire species."

                "My advice? Get strong. Get allies. Get ready. Because in 30 days, everything changes.
                Again."

                They vanish before you can ask what they mean.
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_faction_war",
            title = "Lines in the Sand",
            act = 3,
            triggerLevel = 30,
            triggerLocation = null,
            narration = """
                The factions have stopped cooperating. The Convergence has everyone on edge, and old
                tensions have exploded into open conflict.

                Loyalists claim they're "preparing humanity" by enforcing System rules. The Remnant
                calls them collaborators and refuses to participate in what they see as a death game.
                The Syndicate sells weapons to both sides. The Seekers frantically search for a way
                to prevent Convergence entirely.

                Settlements are choosing sides. Friends are becoming enemies. And you're being forced
                to choose: which side of history do you want to be on?

                Because neutrality? That's not an option anymore.
            """.trimIndent(),
            consequences = mapOf(
                "faction_war_started" to "true",
                "neutrality_available" to "false"
            )
        )
    )

    val ACT_3_MAIN_QUESTS = listOf(
        MainQuest(
            id = "quest_prepare_convergence",
            title = "Prepare for Convergence",
            act = 3,
            levelRange = 25..40,
            description = "Get ready for the System's ultimate test",
            objectives = listOf(
                "Reach level 40",
                "Forge alliances or prepare to stand alone",
                "Acquire legendary-tier equipment",
                "Unlock your ultimate skill"
            ),
            reward = "Convergence readiness, faction trust"
        ),

        MainQuest(
            id = "quest_faction_conflict",
            title = "The War for Tomorrow",
            act = 3,
            levelRange = 35..50,
            description = "Navigate the faction war and choose your destiny",
            objectives = listOf(
                "Take a stand in the faction war",
                "Complete your faction's critical mission",
                "Survive a major battle",
                "Reach level 50"
            ),
            reward = "Faction leadership role, legendary reward"
        )
    )

    /**
     * Get story beat by level
     */
    fun getStoryBeatForLevel(level: Int): StoryBeat? {
        val allBeats = TUTORIAL_STORY_BEATS + ACT_2_STORY_BEATS + ACT_3_STORY_BEATS
        return allBeats.firstOrNull { it.triggerLevel == level }
    }

    /**
     * Get current main quest
     */
    fun getMainQuestForLevel(level: Int): MainQuest? {
        val allQuests = ACT_1_MAIN_QUESTS + ACT_2_MAIN_QUESTS + ACT_3_MAIN_QUESTS
        return allQuests.firstOrNull { level in it.levelRange }
    }

    /**
     * Get all story beats for an act
     */
    fun getStoryBeatsForAct(act: Int): List<StoryBeat> {
        return when (act) {
            1 -> TUTORIAL_STORY_BEATS
            2 -> ACT_2_STORY_BEATS
            3 -> ACT_3_STORY_BEATS
            else -> emptyList()
        }
    }

    /**
     * Get current act based on level
     */
    fun getCurrentAct(level: Int): Int {
        return when {
            level < 10 -> 1
            level < 25 -> 2
            level < 50 -> 3
            else -> 4
        }
    }
}
