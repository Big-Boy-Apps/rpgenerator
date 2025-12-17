package com.rpgenerator.core.domain

import com.rpgenerator.core.api.SystemType

internal class LocationManager {
    private val zones = mutableMapOf<String, Zone>()
    private val templateLocations = mutableMapOf<String, Location>()

    fun loadLocations(systemType: SystemType) {
        when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> loadSystemIntegrationLocations()
            else -> loadDefaultLocations()
        }
    }

    fun getLocation(locationId: String, gameState: GameState): Location? {
        return gameState.customLocations[locationId]
            ?: templateLocations[locationId]
    }

    fun getTemplateLocation(locationId: String): Location? {
        return templateLocations[locationId]
    }

    fun getZone(zoneId: String): Zone? {
        return zones[zoneId]
    }

    fun getConnectedLocations(locationId: String, gameState: GameState): List<Location> {
        val location = getLocation(locationId, gameState) ?: return emptyList()
        return location.connections.mapNotNull {
            getLocation(it, gameState)
        }
    }

    fun getAvailableLocations(gameState: GameState): List<Location> {
        val discovered = gameState.discoveredTemplateLocations.mapNotNull {
            templateLocations[it]
        }
        val custom = gameState.customLocations.values.toList()
        return discovered + custom
    }

    fun getStartingLocation(systemType: SystemType): Location {
        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> templateLocations["tutorial-grove"]
                ?: error("Tutorial grove not found")
            else -> templateLocations.values.first()
        }
    }

    private fun loadSystemIntegrationLocations() {
        addZone(Zone(
            id = "tutorial-zone",
            name = "Tutorial Zone",
            recommendedLevel = 1,
            description = "A protected area where newly integrated humans learn to survive",
            lore = "When the System integrated Earth, it created safe zones for the unprepared masses. Most didn't make it past the first wave."
        ))

        addZone(Zone(
            id = "verdant-wilderness",
            name = "Verdant Wilderness",
            recommendedLevel = 5,
            description = "Dense forests teeming with mutated beasts and rare herbs",
            lore = "The System's energy warped Earth's ecosystems. What were once ordinary animals became deadly predators, and plants gained strange properties."
        ))

        addZone(Zone(
            id = "contested-outpost",
            name = "Contested Outpost",
            recommendedLevel = 10,
            description = "A human settlement constantly under threat from beast hordes",
            lore = "Survivors banded together, building walls and organizing parties. But the beasts grow stronger each day."
        ))

        addZone(Zone(
            id = "nexus-shard",
            name = "Dimensional Nexus Shard",
            recommendedLevel = 15,
            description = "A fracture in reality where cosmic energies pool",
            lore = "The System integration didn't just change Earth - it punched holes to other dimensions. Power flows through these rifts, but so do horrors."
        ))

        addLocation(Location(
            id = "tutorial-grove",
            name = "Sanctified Grove",
            zoneId = "tutorial-zone",
            biome = Biome.TUTORIAL_ZONE,
            description = "A peaceful clearing surrounded by shimmering barriers. The air hums with protective energy.",
            danger = 1,
            connections = listOf("darkwood-path"),
            features = listOf("system_obelisk", "healing_spring"),
            lore = "The System placed these groves across Earth as starting points. Their barriers last one week - then you're on your own."
        ))

        addLocation(Location(
            id = "darkwood-path",
            name = "Darkwood Path",
            zoneId = "tutorial-zone",
            biome = Biome.FOREST,
            description = "A winding trail through dense forest. Shadows move between the trees.",
            danger = 2,
            connections = listOf("tutorial-grove", "goblin-camp", "hidden-glade"),
            features = listOf("beast_tracks", "abandoned_corpses"),
            lore = "The path to the outside world. Many who took it never returned."
        ))

        addLocation(Location(
            id = "goblin-camp",
            name = "Goblin War Camp",
            zoneId = "tutorial-zone",
            biome = Biome.SETTLEMENT,
            description = "Crude tents and weapon racks surround a blazing fire. The stench of blood fills the air.",
            danger = 3,
            connections = listOf("darkwood-path"),
            features = listOf("weapon_cache", "goblin_chieftain"),
            lore = "The System didn't just enhance Earth's creatures - it spawned entirely new species. Goblins were among the first."
        ))

        addLocation(Location(
            id = "hidden-glade",
            name = "Mystic Glade",
            zoneId = "tutorial-zone",
            biome = Biome.FOREST,
            description = "A serene clearing where crystalline flowers bloom. The air shimmers with mana.",
            danger = 1,
            connections = listOf("darkwood-path", "ancient-ruins"),
            features = listOf("mana_flowers", "spirit_wisps"),
            lore = "Places where Earth's original magic mingles with System energy. Valuable, but contested."
        ))

        addLocation(Location(
            id = "ancient-ruins",
            name = "Pre-System Ruins",
            zoneId = "verdant-wilderness",
            biome = Biome.RUINS,
            description = "Collapsed skyscrapers now overgrown with luminescent vines. Nature reclaimed civilization in days.",
            danger = 5,
            connections = listOf("hidden-glade", "beast-den", "outpost-gates"),
            features = listOf("scavengeable_tech", "mutant_nest"),
            lore = "A city that fell in the first hours. The corpses are long gone, but their equipment remains."
        ))

        addLocation(Location(
            id = "beast-den",
            name = "Crimson Beast Den",
            zoneId = "verdant-wilderness",
            biome = Biome.CAVE,
            description = "A network of caves stained red from countless kills. Bones litter the entrance.",
            danger = 7,
            connections = listOf("ancient-ruins"),
            features = listOf("alpha_beast", "bone_pile", "rare_catalyst"),
            lore = "Home to a pack of Class E beasts. Their alpha guards a Dao shard."
        ))

        addLocation(Location(
            id = "outpost-gates",
            name = "Sanctuary Outpost Gates",
            zoneId = "contested-outpost",
            biome = Biome.SETTLEMENT,
            description = "Reinforced walls built from scrap metal and concrete. Guards eye you warily from watchtowers.",
            danger = 3,
            connections = listOf("ancient-ruins", "outpost-market", "training-grounds"),
            features = listOf("guard_post", "quest_board"),
            lore = "The largest surviving human settlement in the region. Population: 847. Yesterday it was 851."
        ))

        addLocation(Location(
            id = "outpost-market",
            name = "Black Market",
            zoneId = "contested-outpost",
            biome = Biome.SETTLEMENT,
            description = "Makeshift stalls selling everything from System crystals to scavenged rations. Desperate merchants hawk their wares.",
            danger = 1,
            connections = listOf("outpost-gates"),
            features = listOf("merchant_npc", "rare_items", "information_broker"),
            lore = "Credits are worthless. Nexus coins and beast cores are the new currency."
        ))

        addLocation(Location(
            id = "training-grounds",
            name = "Combat Training Yard",
            zoneId = "contested-outpost",
            biome = Biome.SETTLEMENT,
            description = "An open area where survivors practice with weapons. The clash of steel rings out constantly.",
            danger = 2,
            connections = listOf("outpost-gates", "nexus-rift"),
            features = listOf("training_dummies", "instructor_npc", "sparring_arena"),
            lore = "Everyone fights now. There are no civilians anymore."
        ))

        addLocation(Location(
            id = "nexus-rift",
            name = "Pulsing Nexus Rift",
            zoneId = "nexus-shard",
            biome = Biome.COSMIC_VOID,
            description = "Reality tears open, revealing an impossible vista of swirling galaxies. Cosmic energy pours through.",
            danger = 15,
            connections = listOf("training-grounds"),
            features = listOf("dimensional_portal", "cosmic_horror", "dao_fragment"),
            lore = "A gateway to the wider multiverse. Step through at your own risk."
        ))
    }

    private fun loadDefaultLocations() {
        addZone(Zone(
            id = "starting-zone",
            name = "Starting Area",
            recommendedLevel = 1,
            description = "Where your journey begins",
            lore = "Every story starts somewhere."
        ))

        addLocation(Location(
            id = "start",
            name = "Starting Location",
            zoneId = "starting-zone",
            biome = Biome.FOREST,
            description = "A generic starting area",
            danger = 1,
            connections = emptyList(),
            features = emptyList(),
            lore = "The beginning of your adventure."
        ))
    }

    private fun addZone(zone: Zone) {
        zones[zone.id] = zone
    }

    private fun addLocation(location: Location) {
        templateLocations[location.id] = location
    }
}
