package com.rpgenerator.core.story

/**
 * Hardcoded zone progression defining the world structure.
 * Zones unlock based on level and story progression.
 */
internal object ZoneProgression {

    data class ZoneDefinition(
        val id: String,
        val name: String,
        val levelRange: IntRange,
        val description: String,
        val theme: String,
        val dangers: List<String>,
        val resources: List<String>,
        val connectedZones: List<String> = emptyList()
    )

    // ========================
    // TUTORIAL ZONES (Level 1-3)
    // ========================

    val TUTORIAL_ZONE = ZoneDefinition(
        id = "tutorial_zone_alpha",
        name = "Tutorial Zone Alpha-7",
        levelRange = 1..3,
        description = """
            A pocket dimension designed by the System for teaching fundamentals. The environment
            is eerily perfect - soft grass, gentle lighting, no weather. Training dummies spawn
            endlessly. Danger is calibrated exactly to your level.

            Administrator Aria oversees this zone. Thousands like it exist, each training a new
            batch of integrated beings. Time moves differently here.
        """.trimIndent(),
        theme = "Safe training ground, artificial perfection",
        dangers = listOf(
            "Training constructs (Level 1-3)",
            "None (Safe Zone protections active)"
        ),
        resources = listOf(
            "Basic weapons and armor",
            "Healing items",
            "Skill scrolls (common tier only)"
        )
    )

    // ========================
    // STARTER ZONES (Level 3-10)
    // ========================

    val THRESHOLD = ZoneDefinition(
        id = "zone_threshold",
        name = "The Threshold",
        levelRange = 3..10,
        description = """
            The first real settlement - a fortified compound built on the ruins of what used to be
            a shopping mall. Concrete walls reinforced with scavenged metal. Guard towers. Farms
            growing System-mutated crops that mature in days instead of months.

            Warden Kade runs The Threshold with military discipline. It's crowded, loud, and smells
            like too many people in too little space. But it's safe. Mostly.
        """.trimIndent(),
        theme = "Frontier fort, desperate civilization",
        dangers = listOf(
            "Corrupted wildlife (Level 4-8)",
            "Random monster incursions",
            "PvP allowed in outer districts"
        ),
        resources = listOf(
            "Marketplace (basic equipment)",
            "Training grounds",
            "Quest board",
            "Faction recruiters"
        ),
        connectedZones = listOf("tutorial_zone_alpha", "zone_fringe_forest", "zone_ruined_highway")
    )

    val FRINGE_FOREST = ZoneDefinition(
        id = "zone_fringe_forest",
        name = "Fringe Forest",
        levelRange = 5..12,
        description = """
            The forest that grew where suburbs used to be. Trees the size of redwoods that sprouted
            in weeks. Bioluminescent plants. And creatures - oh, the creatures.

            Wolves the size of horses. Insects with too many eyes. Things that used to be deer
            before Integration twisted them. It's beautiful and deadly in equal measure.
        """.trimIndent(),
        theme = "Mutated nature, primal danger",
        dangers = listOf(
            "Dire Wolves (Level 6-10)",
            "Corrupted Treants (Level 8-12)",
            "Poisonous flora",
            "Environmental hazards"
        ),
        resources = listOf(
            "Rare herbs and alchemical ingredients",
            "Beast cores (crafting materials)",
            "Hidden treasure caches",
            "Secret clearings with powerful NPCs"
        ),
        connectedZones = listOf("zone_threshold", "zone_verdant_heart")
    )

    val RUINED_HIGHWAY = ZoneDefinition(
        id = "zone_ruined_highway",
        name = "The Ruined Highway",
        levelRange = 6..14,
        description = """
            What remains of Interstate 5, now a twisted maze of shattered overpasses and abandoned
            vehicles. Metal corroded unnaturally fast. Entire sections collapsed into sinkholes that
            definitely weren't there before Integration.

            Scavengers pick through wreckage looking for pre-Integration tech that still works. The
            Syndicate controls most salvage rights - for a fee.
        """.trimIndent(),
        theme = "Post-apocalyptic scavenging, urban decay",
        dangers = listOf(
            "Rust Golems (Level 7-11)",
            "Scavenger gangs (Level 6-12)",
            "Structural collapses",
            "Syndicate enforcers (if you violate salvage rights)"
        ),
        resources = listOf(
            "Pre-Integration technology",
            "Scrap metal (crafting)",
            "Abandoned supply caches",
            "Vehicle parts (rare)"
        ),
        connectedZones = listOf("zone_threshold", "zone_nexus_market")
    )

    // ========================
    // MID-TIER ZONES (Level 10-25)
    // ========================

    val NEXUS_MARKET = ZoneDefinition(
        id = "zone_nexus_market",
        name = "Nexus Market",
        levelRange = 10..30,
        description = """
            The Syndicate's crown jewel - a massive trading hub built in what used to be a stadium.
            Merchants from all factions set up stalls here under Selena Rourke's "neutral ground" rules.

            No fighting in the market. No stealing. No questions about where goods came from. The
            Syndicate's enforcers make sure everyone plays nice. Violate the rules, and you're banned
            for life - if you're lucky.
        """.trimIndent(),
        theme = "Mercantile hub, neutral ground, information trading",
        dangers = listOf(
            "Syndicate enforcers (Level 15-25, don't fight unless you break rules)",
            "Pickpockets and con artists",
            "Economic manipulation"
        ),
        resources = listOf(
            "Rare items and equipment",
            "Skill books (up to rare tier)",
            "Information brokers",
            "The Broker's mysterious shop"
        ),
        connectedZones = listOf("zone_ruined_highway", "zone_citadel", "zone_haven", "zone_observatory")
    )

    val HAVEN = ZoneDefinition(
        id = "zone_haven",
        name = "Haven",
        levelRange = 10..20,
        description = """
            The Remnant's settlement - deliberately built to look like pre-Integration Earth. Actual
            houses with lawns (even if the grass is mutated). A library. A school for children. They
            even have a coffee shop, though the coffee tastes strange.

            Marcus Chen runs Haven as a living museum of humanity. It's equal parts inspiring and
            heartbreaking - a desperate attempt to preserve a world that's gone.
        """.trimIndent(),
        theme = "Nostalgic preservation, stubborn humanity",
        dangers = listOf(
            "Minimal (Safe Zone protections)",
            "Ideological conflict with visitors",
            "Resource scarcity (they refuse to exploit System mechanics)"
        ),
        resources = listOf(
            "Pre-Integration cultural items",
            "Wisdom and philosophy training",
            "Community support",
            "Anti-Assimilation techniques"
        ),
        connectedZones = listOf("zone_nexus_market", "zone_fringe_forest")
    )

    val CITADEL = ZoneDefinition(
        id = "zone_citadel",
        name = "The Citadel",
        levelRange = 15..35,
        description = """
            Loyalist headquarters - a fortress built by System-granted construction skills. Walls of
            crystallized energy. Training grounds where Champions practice. A meritocracy where power
            equals authority.

            Champion Theron leads the Vanguard from here. It's the most militarized zone in human
            control, and they're proud of it. "Through strength, we endure."
        """.trimIndent(),
        theme = "Military strength, System meritocracy",
        dangers = listOf(
            "Training duels (optional PvP)",
            "Rank challenges (fight for position)",
            "Vanguard missions (high-difficulty)"
        ),
        resources = listOf(
            "Elite training programs",
            "High-tier equipment (earned through merit)",
            "Class advancement quests",
            "Vanguard recruitment"
        ),
        connectedZones = listOf("zone_nexus_market", "zone_proving_grounds")
    )

    val OBSERVATORY = ZoneDefinition(
        id = "zone_observatory",
        name = "The Observatory",
        levelRange = 15..30,
        description = """
            Seeker headquarters - more research facility than settlement. Dr. Voss and her team
            run experiments that should probably have ethics reviews. They've discovered System
            exploits, hidden mechanics, and several things that got people killed.

            Knowledge is power here. Literally - they'll trade discoveries for favors.
        """.trimIndent(),
        theme = "Scientific experimentation, dangerous knowledge",
        dangers = listOf(
            "Experimental creatures (Level 12-25)",
            "System anomalies",
            "Failed experiments escaping containment",
            "Dr. Voss's impatience"
        ),
        resources = listOf(
            "System knowledge databases",
            "Experimental skills and abilities",
            "Hidden class paths",
            "Dangerous secrets"
        ),
        connectedZones = listOf("zone_nexus_market", "zone_fractured_reality")
    )

    // ========================
    // HIGH-TIER ZONES (Level 25+)
    // ========================

    val PROVING_GROUNDS = ZoneDefinition(
        id = "zone_proving_grounds",
        name = "The Proving Grounds",
        levelRange = 25..45,
        description = """
            A System-generated arena where fighters test their limits. Challenges scale to your
            level. Rewards are legendary. So is the death rate.

            The Loyalists use it for advancement tests. The Syndicate runs betting pools on matches.
            Everyone respects those who survive the upper floors.
        """.trimIndent(),
        theme = "Combat trials, meritocratic advancement",
        dangers = listOf(
            "Arena champions (Level 25-40)",
            "Boss gauntlets",
            "Environmental hazards",
            "Permadeath possible on highest floors"
        ),
        resources = listOf(
            "Legendary equipment",
            "Title achievements",
            "Class evolution materials",
            "Unique skills"
        ),
        connectedZones = listOf("zone_citadel")
    )

    val FRACTURED_REALITY = ZoneDefinition(
        id = "zone_fractured_reality",
        name = "Fractured Reality",
        levelRange = 30..50,
        description = """
            A zone where System integration went WRONG. Physics doesn't work right. Gravity shifts.
            Time loops. Space folds on itself. The Seekers study it obsessively.

            Dangerous, disorienting, and filled with things that shouldn't exist. But the loot?
            Unmatched. If you can find your way out.
        """.trimIndent(),
        theme = "Reality distortion, cosmic horror",
        dangers = listOf(
            "Paradox creatures (Level 28-45)",
            "Reality tears",
            "Temporal loops",
            "Existential dread"
        ),
        resources = listOf(
            "Reality-warping items",
            "Paradox-tier equipment",
            "Forbidden knowledge",
            "Sanity damage (yes, it's a mechanic here)"
        ),
        connectedZones = listOf("zone_observatory")
    )

    /**
     * Get zone by ID
     */
    fun getZone(zoneId: String): ZoneDefinition? {
        return getAllZones().firstOrNull { it.id == zoneId }
    }

    /**
     * Get zones accessible at a given level
     */
    fun getZonesForLevel(level: Int): List<ZoneDefinition> {
        return getAllZones().filter { level in it.levelRange }
    }

    /**
     * Get all zones
     */
    fun getAllZones(): List<ZoneDefinition> {
        return listOf(
            TUTORIAL_ZONE,
            THRESHOLD,
            FRINGE_FOREST,
            RUINED_HIGHWAY,
            NEXUS_MARKET,
            HAVEN,
            CITADEL,
            OBSERVATORY,
            PROVING_GROUNDS,
            FRACTURED_REALITY
        )
    }

    /**
     * Check if zone is unlocked for player
     */
    fun isZoneUnlocked(zoneId: String, playerLevel: Int): Boolean {
        val zone = getZone(zoneId) ?: return false
        return playerLevel in zone.levelRange
    }
}
