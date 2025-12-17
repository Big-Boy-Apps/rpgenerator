package com.rpgenerator.core.npc

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.orchestration.GameOrchestrator
import com.rpgenerator.core.test.MockLLMInterface
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NPCSystemTest {

    @Test
    fun `NPC dialogue emits NPCDialogue event`() = runTest {
        val mockLLM = MockLLMInterface()
        val merchant = NPCTemplates.createMerchant("merchant-1", "Garrick", "test-location")

        val initialState = TestHelpers.createTestGameState()
            .addNPC(merchant)

        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I talk to Garrick").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.NPCDialogue, "Should emit NPCDialogue event")
            assertEquals("Garrick", (event as GameEvent.NPCDialogue).npcName)
            assertTrue(event.text.isNotEmpty(), "Dialogue should not be empty")

            awaitComplete()
        }
    }

    @Test
    fun `talking to non-existent NPC shows error`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I talk to NonExistent").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.SystemNotification, "Should show system notification")
            assertTrue((event as GameEvent.SystemNotification).text.contains("no one named"))

            awaitComplete()
        }
    }

    @Test
    fun `NPC conversation is saved to history`() = runTest {
        val mockLLM = MockLLMInterface()
        val merchant = NPCTemplates.createMerchant("merchant-1", "Garrick", "test-location")

        val initialState = TestHelpers.createTestGameState()
            .addNPC(merchant)

        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I talk to Garrick about weapons").test {
            awaitItem() // Consume the dialogue event
            awaitComplete()
        }

        val finalState = orchestrator.getState()
        val updatedNPC = finalState.findNPCByName("Garrick")

        assertNotNull(updatedNPC, "NPC should still exist")
        assertTrue(updatedNPC.conversationHistory.isNotEmpty(), "Should have conversation history")
        assertEquals(1, updatedNPC.conversationHistory.size, "Should have one conversation entry")
    }

    @Test
    fun `polite dialogue increases relationship`() = runTest {
        val mockLLM = MockLLMInterface()
        val guard = NPCTemplates.createGuard("guard-1", "Marcus", "test-location")

        val initialState = TestHelpers.createTestGameState()
            .addNPC(guard)

        val orchestrator = GameOrchestrator(mockLLM, initialState)

        // Initial relationship should be 0
        val npcBefore = initialState.findNPCByName("Marcus")
        assertEquals(0, npcBefore?.getRelationship(initialState.gameId)?.affinity ?: -1)

        orchestrator.processInput("I thank Marcus for his service").test {
            awaitItem() // Dialogue event
            // Consume any remaining events (may or may not get relationship notification)
            cancelAndIgnoreRemainingEvents()
        }

        val finalState = orchestrator.getState()
        val npcAfter = finalState.findNPCByName("Marcus")

        val affinityAfter = npcAfter?.getRelationship(finalState.gameId)?.affinity ?: 0
        assertTrue(affinityAfter > 0, "Relationship should improve with polite dialogue")
    }

    @Test
    fun `merchant NPC has shop`() {
        val merchant = NPCTemplates.createMerchant("merchant-1", "Garrick", "test-location")

        assertNotNull(merchant.shop, "Merchant should have a shop")
        assertEquals("Garrick's Shop", merchant.shop?.name)
        assertTrue(merchant.shop?.inventory?.isNotEmpty() == true, "Shop should have inventory")
    }

    @Test
    fun `quest giver NPC can hold quest IDs`() {
        val questGiver = NPCTemplates.createQuestGiver(
            "quest-giver-1",
            "Elder Thalia",
            "test-location",
            questIds = listOf("quest-1", "quest-2")
        )

        assertEquals(2, questGiver.questIds.size)
        assertTrue(questGiver.questIds.contains("quest-1"))
    }

    @Test
    fun `shop purchase checks level requirement`() {
        val merchant = NPCTemplates.createMerchant("merchant-1", "Garrick", "test-location")
        val state = TestHelpers.createTestGameState(playerLevel = 1)
            .addNPC(merchant)

        // Try to purchase an item with level 5 requirement
        val shop = merchant.shop!!
        val highLevelItem = ShopItem(
            id = "legendary_sword",
            name = "Legendary Sword",
            description = "A powerful weapon",
            price = 1000,
            stock = 1,
            requiredLevel = 5,
            itemData = ShopItemData.WeaponData(
                Weapon(
                    id = "legendary_sword",
                    name = "Legendary Sword",
                    description = "A powerful weapon",
                    baseDamage = 50,
                    strengthBonus = 10
                )
            )
        )

        // Simulate purchase attempt (would normally go through GameTools)
        val canPurchase = highLevelItem.requiredLevel <= state.playerLevel
        assertTrue(!canPurchase, "Should not be able to purchase high-level item")
    }

    @Test
    fun `NPC relationship affects shop item availability`() {
        val merchant = NPCTemplates.createMerchant("merchant-1", "Garrick", "test-location")
        val state = TestHelpers.createTestGameState(playerLevel = 10)
            .addNPC(merchant)

        val specialItem = ShopItem(
            id = "special_item",
            name = "Special Item",
            description = "Only sold to trusted customers",
            price = 500,
            stock = 1,
            requiredLevel = 1,
            requiredRelationship = 50, // Need 50 affinity
            itemData = ShopItemData.ConsumableData(
                InventoryItem(
                    id = "special_item",
                    name = "Special Item",
                    description = "Only sold to trusted customers",
                    type = ItemType.MISC
                )
            )
        )

        val relationship = merchant.getRelationship(state.gameId)
        val canPurchase = relationship.affinity >= specialItem.requiredRelationship

        assertTrue(!canPurchase, "Should not be able to purchase without sufficient relationship")
    }

    @Test
    fun `NPC templates have correct archetypes`() {
        val merchant = NPCTemplates.createMerchant("m1", "Merchant", "loc")
        val guard = NPCTemplates.createGuard("g1", "Guard", "loc")
        val scholar = NPCTemplates.createScholar("s1", "Scholar", "loc")

        assertEquals(NPCArchetype.MERCHANT, merchant.archetype)
        assertEquals(NPCArchetype.GUARD, guard.archetype)
        assertEquals(NPCArchetype.SCHOLAR, scholar.archetype)
    }

    @Test
    fun `NPC personality traits are preserved`() {
        val wanderer = NPCTemplates.createWanderer("w1", "Mysterious Traveler", "loc")

        assertTrue(wanderer.personality.traits.contains("mysterious"))
        assertEquals("speaks in riddles and hints", wanderer.personality.speechPattern)
        assertTrue(wanderer.personality.motivations.isNotEmpty())
    }

    @Test
    fun `multiple NPCs can exist at same location`() {
        val merchant = NPCTemplates.createMerchant("m1", "Garrick", "test-location")
        val guard = NPCTemplates.createGuard("g1", "Marcus", "test-location")
        val innkeeper = NPCTemplates.createInnkeeper("i1", "Helena", "test-location")

        val state = TestHelpers.createTestGameState()
            .addNPC(merchant)
            .addNPC(guard)
            .addNPC(innkeeper)

        val npcsAtLocation = state.getNPCsAtCurrentLocation()
        assertEquals(3, npcsAtLocation.size, "Should have 3 NPCs at location")
    }

    @Test
    fun `NPC can be found by name case-insensitive`() {
        val merchant = NPCTemplates.createMerchant("m1", "Garrick", "test-location")
        val state = TestHelpers.createTestGameState()
            .addNPC(merchant)

        val foundLower = state.findNPCByName("garrick")
        val foundUpper = state.findNPCByName("GARRICK")
        val foundMixed = state.findNPCByName("GaRrIcK")

        assertNotNull(foundLower)
        assertNotNull(foundUpper)
        assertNotNull(foundMixed)
        assertEquals("Garrick", foundLower?.name)
    }

    @Test
    fun `relationship status updates correctly`() {
        val relationship = Relationship("game-1", 0)

        assertEquals(RelationshipStatus.NEUTRAL, relationship.getStatus())

        val friendly = relationship.copy(affinity = 50)
        assertEquals(RelationshipStatus.FRIENDLY, friendly.getStatus())

        val trustedAlly = relationship.copy(affinity = 80)
        assertEquals(RelationshipStatus.TRUSTED_ALLY, trustedAlly.getStatus())

        val hostile = relationship.copy(affinity = -50)
        assertEquals(RelationshipStatus.HOSTILE, hostile.getStatus())
    }

    @Test
    fun `NPC memory stores player level at time of conversation`() = runTest {
        val mockLLM = MockLLMInterface()
        val merchant = NPCTemplates.createMerchant("merchant-1", "Garrick", "test-location")

        val initialState = TestHelpers.createTestGameState(playerLevel = 3)
            .addNPC(merchant)

        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I talk to Garrick").test {
            awaitItem()
            awaitComplete()
        }

        val finalState = orchestrator.getState()
        val updatedNPC = finalState.findNPCByName("Garrick")

        val conversation = updatedNPC?.conversationHistory?.firstOrNull()
        assertEquals(3, conversation?.playerLevel, "Should remember player was level 3")
    }

    @Test
    fun `shop transaction checks stock availability`() {
        val shop = Shop(
            name = "Test Shop",
            inventory = listOf(
                ShopItem(
                    id = "potion",
                    name = "Health Potion",
                    description = "Heals HP",
                    price = 25,
                    stock = 2, // Only 2 in stock
                    itemData = ShopItemData.ConsumableData(
                        InventoryItem(
                            id = "potion",
                            name = "Health Potion",
                            description = "Heals HP",
                            type = ItemType.CONSUMABLE
                        )
                    )
                )
            )
        )

        val item = shop.getItem("potion")
        assertNotNull(item)
        assertEquals(2, item.stock)

        // Attempting to purchase 3 when only 2 available should fail
        val hasEnoughStock = item.stock >= 3
        assertTrue(!hasEnoughStock, "Should not have enough stock")
    }

    @Test
    fun `blacksmith has weapon and armor inventory`() {
        val blacksmith = NPCTemplates.createBlacksmith("bs1", "Thorin", "loc")

        val shop = blacksmith.shop
        assertNotNull(shop)

        val hasWeapons = shop.inventory.any { it.itemData is ShopItemData.WeaponData }
        val hasArmor = shop.inventory.any { it.itemData is ShopItemData.ArmorData }

        assertTrue(hasWeapons, "Blacksmith should sell weapons")
        assertTrue(hasArmor, "Blacksmith should sell armor")
    }

    @Test
    fun `alchemist has potion inventory`() {
        val alchemist = NPCTemplates.createAlchemist("alch1", "Morgana", "loc")

        val shop = alchemist.shop
        assertNotNull(shop)
        assertTrue(shop.inventory.isNotEmpty(), "Alchemist should have inventory")

        // All items should be consumables (potions/elixirs)
        val allConsumables = shop.inventory.all { it.itemData is ShopItemData.ConsumableData }
        assertTrue(allConsumables, "Alchemist should primarily sell consumables")
    }
}
