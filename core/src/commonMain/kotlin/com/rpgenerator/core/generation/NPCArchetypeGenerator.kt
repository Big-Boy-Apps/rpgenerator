package com.rpgenerator.core.generation

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import kotlinx.coroutines.flow.toList
import com.rpgenerator.core.util.currentTimeMillis

/**
 * NPC Archetype Generator - Role-based dynamic NPC creation
 *
 * Instead of hardcoded NPCs, we define ROLES that need to be filled:
 * - "tutorial_guide": The first System representative player meets
 * - "first_rival": Someone competing for the same goal
 * - "mysterious_merchant": Strange vendor with rare items
 *
 * Each playthrough generates DIFFERENT characters for these roles.
 */
internal class NPCArchetypeGenerator(private val llm: LLMInterface) {

    private val agentStream = llm.startAgent(
        """
        You are the NPC Archetype Generator.

        Your job is to create UNIQUE NPCs based on archetypal roles.
        Same role, different character each time.

        Key principles:
        1. AVOID CLICHÉS - Don't default to "wise old mentor" or "plucky sidekick"
        2. SUBVERT EXPECTATIONS - Tutorial guide doesn't have to be friendly
        3. PERSONALITY FIRST - Make them feel REAL, not generic
        4. CULTURAL DIVERSITY - Don't default to Western fantasy tropes
        5. CONTRADICTIONS - People are complex; add unexpected traits

        You will receive:
        - Role archetype
        - Player context
        - Pre-generated name (to avoid name bias)

        Generate a complete NPC personality and backstory.
        """.trimIndent()
    )

    /**
     * Generate the tutorial guide - first NPC player meets
     */
    suspend fun generateTutorialGuide(
        playerName: String,
        systemType: com.rpgenerator.core.api.SystemType,
        playerLevel: Int,
        seed: Long = currentTimeMillis()
    ): NPC {
        val name = NameGenerator.generateAdministratorName(seed)
        val culture = NameCulture.values().random()

        val prompt = """
            Generate a TUTORIAL GUIDE NPC for a ${systemType.name} System.

            Pre-assigned name: $name
            Cultural inspiration: $culture
            Player: $playerName (Level $playerLevel)

            This NPC's role:
            - Explain the System to newcomers
            - Provide the first quest
            - Set the tone for this System type

            But make them UNIQUE. Some ideas:
            - Exhausted administrator who's done this 10,000 times?
            - Overly enthusiastic AI who loves their job?
            - Bitter survivor forced to help?
            - Mysterious entity with hidden agenda?

            Generate:
            1. Personality traits (3-5, include contradictions)
            2. Speech pattern (how they talk)
            3. Motivations (what drives them)
            4. Backstory (2-3 sentences, keep some mystery)
            5. Initial greeting (what they say when they first appear)

            Format as JSON:
            {
                "traits": ["professional", "weary", "sardonic"],
                "speechPattern": "Clipped, formal, occasionally dry humor",
                "motivations": ["Process newcomers efficiently", "Maintain System stability"],
                "backstory": "Brief backstory here",
                "greeting": "What they say to player"
            }
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCFromResponse(
            response,
            name,
            NPCArchetype.TRAINER,
            "tutorial_zone_alpha",
            seed
        )
    }

    /**
     * Generate a merchant NPC
     */
    suspend fun generateMerchant(
        locationId: String,
        locationDanger: Int,
        playerLevel: Int,
        seed: Long = currentTimeMillis()
    ): NPC {
        val culture = NameCulture.values().random()
        val name = NameGenerator.generateTitledName("Merchant", culture, seed)

        val prompt = """
            Generate a MERCHANT NPC.

            Name: $name
            Location danger level: $locationDanger
            Player level: $playerLevel

            Make them interesting:
            - Not just "friendly shopkeeper"
            - What's their angle? Why are they here?
            - What's their story?

            High danger location? Maybe they're desperate, opportunistic, or incredibly brave.
            Low danger? Maybe they're bored, ambitious, or hiding from something.

            Generate personality, motivations, backstory, greeting (JSON format).
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCFromResponse(
            response,
            name,
            NPCArchetype.MERCHANT,
            locationId,
            seed
        )
    }

    /**
     * Generate a quest giver NPC
     */
    suspend fun generateQuestGiver(
        locationId: String,
        questType: String,
        playerContext: String,
        seed: Long = currentTimeMillis()
    ): NPC {
        val culture = NameCulture.values().random()
        val name = NameGenerator.generateName("", culture, NameGender.ANY, seed)

        val prompt = """
            Generate a QUEST GIVER NPC.

            Name: $name
            Quest type: $questType
            Context: $playerContext

            Why are they giving this quest?
            - Personal stake?
            - Desperate need?
            - Testing the player?
            - Hidden agenda?

            Make their motivations CLEAR and PERSONAL.

            Generate personality, motivations, backstory, greeting (JSON format).
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCFromResponse(
            response,
            name,
            NPCArchetype.QUEST_GIVER,
            locationId,
            seed
        )
    }

    /**
     * Generate a rival NPC - someone competing with player
     */
    suspend fun generateRival(
        locationId: String,
        playerLevel: Int,
        playerClass: PlayerClass,
        seed: Long = currentTimeMillis()
    ): NPC {
        val culture = NameCulture.values().random()
        val name = NameGenerator.generateName("", culture, NameGender.ANY, seed)

        val prompt = """
            Generate a RIVAL NPC.

            Name: $name
            Player level: $playerLevel
            Player class: ${playerClass.displayName}

            This person is competing with the player for:
            - Resources?
            - Status?
            - A specific goal?
            - Someone's approval?

            They should be:
            - Similar level (maybe slightly ahead?)
            - Different approach than player
            - Not purely antagonistic (complex relationship)

            Are they:
            - Arrogant and dismissive?
            - Respectful but determined?
            - Desperate and willing to cheat?
            - Actually friendly but competitive?

            Generate personality, motivations, backstory, greeting (JSON format).
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCFromResponse(
            response,
            name,
            NPCArchetype.WANDERER, // Rivals are often wanderers
            locationId,
            seed
        )
    }

    /**
     * Generate generic NPC for a role
     */
    suspend fun generateForRole(
        role: String,
        locationId: String,
        context: String,
        seed: Long = currentTimeMillis()
    ): NPC {
        val culture = NameCulture.values().random()
        val name = NameGenerator.generateName(role, culture, NameGender.ANY, seed)

        val archetype = mapRoleToArchetype(role)

        val prompt = """
            Generate an NPC for the role: $role

            Name: $name
            Location: $locationId
            Context: $context

            Make them unique and memorable.
            Avoid stereotypes and clichés.

            Generate personality, motivations, backstory, greeting (JSON format).
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")

        return parseNPCFromResponse(response, name, archetype, locationId, seed)
    }

    // ========================
    // Helper Functions
    // ========================

    private fun mapRoleToArchetype(role: String): NPCArchetype {
        return when (role.lowercase()) {
            "merchant", "trader", "vendor" -> NPCArchetype.MERCHANT
            "quest_giver", "questgiver" -> NPCArchetype.QUEST_GIVER
            "guard", "warden", "sentinel" -> NPCArchetype.GUARD
            "blacksmith", "armorer" -> NPCArchetype.BLACKSMITH
            "trainer", "mentor", "guide" -> NPCArchetype.TRAINER
            "healer", "medic", "alchemist" -> NPCArchetype.ALCHEMIST
            "rival", "competitor" -> NPCArchetype.WANDERER
            "innkeeper", "bartender" -> NPCArchetype.INNKEEPER
            "scholar", "mage", "researcher" -> NPCArchetype.SCHOLAR
            "noble", "aristocrat" -> NPCArchetype.NOBLE
            else -> NPCArchetype.VILLAGER
        }
    }

    private fun parseNPCFromResponse(
        response: String,
        name: String,
        archetype: NPCArchetype,
        locationId: String,
        seed: Long
    ): NPC {
        // TODO: Implement proper JSON parsing
        // For now, extract basic info

        val traitsMatch = Regex("\"traits\"\\s*:\\s*\\[([^\\]]+)\\]").find(response)
        val traits = traitsMatch?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().removeSurrounding("\"") }
            ?: listOf("professional", "mysterious")

        val speechMatch = Regex("\"speechPattern\"\\s*:\\s*\"([^\"]+)\"").find(response)
        val speechPattern = speechMatch?.groupValues?.get(1) ?: "Normal speech"

        val motivationsMatch = Regex("\"motivations\"\\s*:\\s*\\[([^\\]]+)\\]").find(response)
        val motivations = motivationsMatch?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().removeSurrounding("\"") }
            ?: listOf("Fulfill their role")

        val backstoryMatch = Regex("\"backstory\"\\s*:\\s*\"([^\"]+)\"").find(response)
        val backstory = backstoryMatch?.groupValues?.get(1) ?: "Their past is shrouded in mystery."

        val greetingMatch = Regex("\"greeting\"\\s*:\\s*\"([^\"]+)\"").find(response)
        val greeting = greetingMatch?.groupValues?.get(1) ?: "Greetings, newcomer."

        val personality = NPCPersonality(
            traits = traits,
            speechPattern = speechPattern,
            motivations = motivations
        )

        return NPC(
            id = "npc_${locationId}_${archetype.name.lowercase()}_$seed",
            name = name,
            archetype = archetype,
            locationId = locationId,
            personality = personality,
            lore = backstory,
            greetingContext = greeting
        )
    }
}
