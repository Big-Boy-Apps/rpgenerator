package com.rpgenerator.core.tools

import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.test.MockLLMInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameToolsImplTest {

    private lateinit var locationManager: LocationManager
    private lateinit var rulesEngine: RulesEngine
    private lateinit var locationGenerator: LocationGeneratorAgent
    private lateinit var gameTools: GameToolsImpl
    private lateinit var testGameState: GameState

    @BeforeEach
    fun setup() {
        locationManager = LocationManager().apply {
            loadLocations(SystemType.SYSTEM_INTEGRATION)
        }
        rulesEngine = RulesEngine()

        val mockLLM = MockLLMInterface()
        locationGenerator = LocationGeneratorAgent(mockLLM)
        val questGenerator = com.rpgenerator.core.agents.QuestGeneratorAgent(mockLLM)

        gameTools = GameToolsImpl(locationManager, rulesEngine, locationGenerator, questGenerator)

        // Create a test game state
        val startingLocation = locationManager.getStartingLocation(SystemType.SYSTEM_INTEGRATION)
        testGameState = GameState(
            gameId = "test-game",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = CharacterSheet(
                level = 3,
                xp = 250,
                baseStats = Stats(
                    strength = 10,
                    dexterity = 8,
                    constitution = 12,
                    intelligence = 7,
                    wisdom = 6,
                    charisma = 5,
                    defense = 0
                ),
                resources = Resources(
                    currentHP = 120,
                    maxHP = 120,
                    currentMana = 50,
                    maxMana = 70,
                    currentEnergy = 100,
                    maxEnergy = 100
                ),
                inventory = Inventory(maxSlots = 20),
                equipment = Equipment(),
                skills = emptyList(),
                statusEffects = emptyList()
            ),
            currentLocation = startingLocation,
            discoveredTemplateLocations = setOf(startingLocation.id),
            customLocations = emptyMap()
        )
    }

    @Test
    fun `test generateLocation creates valid location`() = runBlocking {
        // Given
        val parentLocation = testGameState.currentLocation
        val discoveryContext = "search for a hidden cave"

        // When
        val generatedLocation = gameTools.generateLocation(
            parentLocation = parentLocation,
            discoveryContext = discoveryContext,
            state = testGameState
        )

        // Then
        assertNotNull(generatedLocation, "Location should be generated")
        generatedLocation?.let { location ->
            assertEquals("Hidden Cave", location.name)
            assertEquals(Biome.CAVE, location.biome)
            assertEquals(4, location.danger)
            assertTrue(location.features.contains("stalactites"))
            assertTrue(location.features.contains("underground_stream"))
            assertTrue(location.features.contains("bat_colony"))
            assertTrue(location.description.isNotEmpty())
            assertTrue(location.lore.isNotEmpty())
            assertEquals(parentLocation.zoneId, location.zoneId)
            assertTrue(location.connections.contains(parentLocation.id))
        }
    }

    @Test
    fun `test generateLocation inherits zone from parent`() = runBlocking {
        // Given
        val parentLocation = testGameState.currentLocation
        val discoveryContext = "explore the surrounding area"

        // When
        val generatedLocation = gameTools.generateLocation(
            parentLocation = parentLocation,
            discoveryContext = discoveryContext,
            state = testGameState
        )

        // Then
        assertNotNull(generatedLocation)
        generatedLocation?.let { location ->
            assertEquals(parentLocation.zoneId, location.zoneId)
        }
    }

    @Test
    fun `test generateLocation connects to parent location`() = runBlocking {
        // Given
        val parentLocation = testGameState.currentLocation
        val discoveryContext = "look for secret passages"

        // When
        val generatedLocation = gameTools.generateLocation(
            parentLocation = parentLocation,
            discoveryContext = discoveryContext,
            state = testGameState
        )

        // Then
        assertNotNull(generatedLocation)
        generatedLocation?.let { location ->
            assertTrue(
                location.connections.contains(parentLocation.id),
                "Generated location should connect back to parent"
            )
        }
    }

    @Test
    fun `test generateLocation has appropriate danger level for player`() = runBlocking {
        // Given
        val parentLocation = testGameState.currentLocation
        val discoveryContext = "investigate dangerous area"
        val playerLevel = testGameState.playerLevel

        // When
        val generatedLocation = gameTools.generateLocation(
            parentLocation = parentLocation,
            discoveryContext = discoveryContext,
            state = testGameState
        )

        // Then
        assertNotNull(generatedLocation)
        generatedLocation?.let { location ->
            // Danger should be within reasonable range of player level (±3)
            assertTrue(
                location.danger >= playerLevel - 3 && location.danger <= playerLevel + 3,
                "Danger level ${location.danger} should be close to player level $playerLevel"
            )
        }
    }

    @Test
    fun `test generateLocation creates unique IDs`() = runBlocking {
        // Given
        val parentLocation = testGameState.currentLocation
        val discoveryContext = "search the area"

        // When
        val location1 = gameTools.generateLocation(parentLocation, discoveryContext, testGameState)
        Thread.sleep(10) // Ensure different timestamps
        val location2 = gameTools.generateLocation(parentLocation, discoveryContext, testGameState)

        // Then
        assertNotNull(location1)
        assertNotNull(location2)
        assertNotEquals(location1?.id, location2?.id, "Generated locations should have unique IDs")
    }

    @Test
    fun `test generateLocation with different discovery contexts`() = runBlocking {
        // Given
        val parentLocation = testGameState.currentLocation
        val context1 = "search for water source"
        val context2 = "look for ancient ruins"

        // When
        val location1 = gameTools.generateLocation(parentLocation, context1, testGameState)
        val location2 = gameTools.generateLocation(parentLocation, context2, testGameState)

        // Then
        assertNotNull(location1)
        assertNotNull(location2)
        // Both should be valid locations with appropriate properties
        location1?.let { loc ->
            assertTrue(loc.name.isNotEmpty())
            assertTrue(loc.description.isNotEmpty())
            assertTrue(loc.features.isNotEmpty())
        }
        location2?.let { loc ->
            assertTrue(loc.name.isNotEmpty())
            assertTrue(loc.description.isNotEmpty())
            assertTrue(loc.features.isNotEmpty())
        }
    }

    @Test
    fun `test getPlayerStatus returns correct information`() {
        // When
        val status = gameTools.getPlayerStatus(testGameState)

        // Then
        assertEquals(3, status.level)
        assertEquals(250, status.xp)
        assertEquals(400, status.xpToNextLevel) // (level + 1) * 100
        assertEquals(testGameState.currentLocation.name, status.locationName)
        assertEquals(testGameState.currentLocation.danger, status.locationDanger)
    }

    @Test
    fun `test getCurrentLocation returns complete details`() {
        // When
        val locationDetails = gameTools.getCurrentLocation(testGameState)

        // Then
        assertEquals(testGameState.currentLocation.id, locationDetails.id)
        assertEquals(testGameState.currentLocation.name, locationDetails.name)
        assertEquals(testGameState.currentLocation.description, locationDetails.description)
        assertEquals(testGameState.currentLocation.danger, locationDetails.danger)
        assertEquals(testGameState.currentLocation.features, locationDetails.features)
        assertEquals(testGameState.currentLocation.lore, locationDetails.lore)
    }

    @Test
    fun `test getConnectedLocations includes custom locations`() = runBlocking {
        // Given - add a custom location connected to current location
        val customLocation = Location(
            id = "custom-test-location",
            name = "Test Custom Location",
            zoneId = testGameState.currentLocation.zoneId,
            biome = Biome.FOREST,
            description = "A test location",
            danger = 2,
            connections = listOf(testGameState.currentLocation.id),
            features = emptyList(),
            lore = "Test lore"
        )

        // Update current location to connect to custom location
        val updatedCurrentLocation = testGameState.currentLocation.copy(
            connections = testGameState.currentLocation.connections + customLocation.id
        )

        val updatedState = testGameState
            .addCustomLocation(customLocation)
            .moveToLocation(updatedCurrentLocation)

        // When
        val connected = gameTools.getConnectedLocations(updatedState)

        // Then
        assertTrue(connected.any { it.id == customLocation.id }, "Should include custom location")
    }

    @Test
    fun `test analyzeIntent detects exploration with location generation`() {
        // Given
        val input = "search for hidden caves"
        val recentEvents = emptyList<com.rpgenerator.core.api.GameEvent>()

        // When
        val analysis = gameTools.analyzeIntent(input, testGameState, recentEvents)

        // Then
        assertEquals(com.rpgenerator.core.orchestration.Intent.EXPLORATION, analysis.intent)
        assertTrue(analysis.shouldGenerateLocation, "Should trigger location generation")
        assertEquals(input, analysis.locationGenerationContext)
    }

    @Test
    fun `test analyzeIntent detects combat intent`() {
        // Given
        val input = "attack the goblin"
        val recentEvents = emptyList<com.rpgenerator.core.api.GameEvent>()

        // When
        val analysis = gameTools.analyzeIntent(input, testGameState, recentEvents)

        // Then
        assertEquals(com.rpgenerator.core.orchestration.Intent.COMBAT, analysis.intent)
        assertEquals("goblin", analysis.target)
    }
}
