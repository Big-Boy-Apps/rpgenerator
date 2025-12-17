package com.rpgenerator.core.generation

import kotlin.random.Random

/**
 * Name Generator - Diverse, non-biased character names
 *
 * Problem: AI models have strong name biases (always "Aria", "Nova", "Zephyr")
 * Solution: Curated databases of real names from diverse cultures
 *
 * Categories:
 * - Cultural/regional diversity (East Asian, African, Nordic, etc.)
 * - Fantasy/sci-fi names that aren't overused
 * - Profession-appropriate names
 * - Gender-neutral options
 */
internal object NameGenerator {

    /**
     * Generate a name appropriate for the NPC's role and context
     */
    fun generateName(
        archetype: String,
        culture: NameCulture = NameCulture.MIXED,
        gender: NameGender = NameGender.ANY,
        seed: Long? = null
    ): String {
        val random = seed?.let { Random(it) } ?: Random.Default

        val namePool = when (culture) {
            NameCulture.MIXED -> MIXED_NAMES
            NameCulture.EAST_ASIAN -> EAST_ASIAN_NAMES
            NameCulture.AFRICAN -> AFRICAN_NAMES
            NameCulture.NORDIC -> NORDIC_NAMES
            NameCulture.ARABIC -> ARABIC_NAMES
            NameCulture.LATIN -> LATIN_NAMES
            NameCulture.SLAVIC -> SLAVIC_NAMES
            NameCulture.SYSTEM -> SYSTEM_NAMES // AI/System entities
        }

        return when (gender) {
            NameGender.MASCULINE -> namePool.masculine.random(random)
            NameGender.FEMININE -> namePool.feminine.random(random)
            NameGender.NEUTRAL -> namePool.neutral.random(random)
            NameGender.ANY -> (namePool.masculine + namePool.feminine + namePool.neutral).random(random)
        }
    }

    /**
     * Generate a System Administrator name (for tutorial NPCs)
     */
    fun generateAdministratorName(seed: Long? = null): String {
        val random = seed?.let { Random(it) } ?: Random.Default
        val prefix = listOf("Administrator", "Custodian", "Overseer", "Arbiter", "Moderator").random(random)
        val name = SYSTEM_NAMES.neutral.random(random)
        return "$prefix $name"
    }

    /**
     * Generate a title + name (e.g., "Warden Kade", "Merchant Chen")
     */
    fun generateTitledName(
        title: String,
        culture: NameCulture = NameCulture.MIXED,
        seed: Long? = null
    ): String {
        val name = generateName("", culture, NameGender.ANY, seed)
        return "$title $name"
    }
}

enum class NameCulture {
    MIXED,
    EAST_ASIAN,
    AFRICAN,
    NORDIC,
    ARABIC,
    LATIN,
    SLAVIC,
    SYSTEM
}

enum class NameGender {
    MASCULINE,
    FEMININE,
    NEUTRAL,
    ANY
}

private data class NamePool(
    val masculine: List<String>,
    val feminine: List<String>,
    val neutral: List<String>
)

// Real, diverse names - not AI-biased fantasy names
private val EAST_ASIAN_NAMES = NamePool(
    masculine = listOf(
        "Chen", "Wei", "Ming", "Jun", "Hiroshi", "Takeshi", "Jin", "Hiro",
        "Yuki", "Kenji", "Ryu", "Dae", "Sung", "Tao", "Feng"
    ),
    feminine = listOf(
        "Mei", "Yuki", "Sakura", "Hana", "Min", "Ji", "Soo", "Ling",
        "Xia", "Yuna", "Akari", "Rei", "Hye", "Lan", "Yue"
    ),
    neutral = listOf(
        "Sato", "Kim", "Park", "Li", "Wang", "Zhang", "Tanaka", "Yamamoto",
        "Choi", "Lin", "Wu", "Nakamura", "Suzuki", "Kang", "Huang"
    )
)

private val AFRICAN_NAMES = NamePool(
    masculine = listOf(
        "Kofi", "Kwame", "Jabari", "Zuberi", "Tendaji", "Amadi", "Chike",
        "Ade", "Thabo", "Sizwe", "Kato", "Mandla", "Sefu", "Tau"
    ),
    feminine = listOf(
        "Amara", "Zuri", "Nia", "Ayana", "Kesia", "Imani", "Sanaa",
        "Zola", "Thandi", "Nala", "Aisha", "Ife", "Kamaria", "Folami"
    ),
    neutral = listOf(
        "Mosi", "Jengo", "Bakari", "Simba", "Jabari", "Asha", "Makena",
        "Roho", "Amani", "Udo", "Zaire", "Kazi", "Juma", "Baraka"
    )
)

private val NORDIC_NAMES = NamePool(
    masculine = listOf(
        "Erik", "Lars", "Bjorn", "Gunnar", "Sven", "Ragnar", "Leif",
        "Magnus", "Tor", "Vidar", "Odin", "Baldur", "Haldor", "Einar"
    ),
    feminine = listOf(
        "Astrid", "Sigrid", "Ingrid", "Freya", "Helga", "Brynhild", "Thyra",
        "Eira", "Runa", "Liv", "Solveig", "Greta", "Sif", "Yrsa"
    ),
    neutral = listOf(
        "Storm", "Frost", "Berg", "Vale", "Nord", "Fjord", "Ulf",
        "Bjork", "Hagen", "Sten", "Tor", "Rask", "Byrne", "Thorne"
    )
)

private val ARABIC_NAMES = NamePool(
    masculine = listOf(
        "Omar", "Hassan", "Rashid", "Tariq", "Malik", "Jamal", "Khalil",
        "Karim", "Samir", "Faisal", "Nasir", "Idris", "Zahir", "Hakim"
    ),
    feminine = listOf(
        "Layla", "Yasmin", "Amina", "Fatima", "Zahra", "Leila", "Nadia",
        "Salma", "Zara", "Aisha", "Jamila", "Noor", "Safiya", "Rania"
    ),
    neutral = listOf(
        "Salem", "Nuri", "Rami", "Sami", "Rafi", "Aziz", "Farah",
        "Hadi", "Jibril", "Amin", "Halim", "Basim", "Latif", "Majid"
    )
)

private val LATIN_NAMES = NamePool(
    masculine = listOf(
        "Marco", "Carlos", "Diego", "Luis", "Miguel", "Rafael", "Mateo",
        "Santiago", "Alejandro", "Javier", "Pablo", "Felix", "Cesar", "Dante"
    ),
    feminine = listOf(
        "Sofia", "Isabel", "Carmen", "Lucia", "Elena", "Marina", "Valentina",
        "Gabriela", "Camila", "Rosa", "Luna", "Bianca", "Adriana", "Serena"
    ),
    neutral = listOf(
        "Cruz", "Reyes", "Santos", "Cortez", "Vega", "Rivera", "Moreno",
        "Silva", "Torres", "Mendez", "Vargas", "Ortiz", "Dominguez", "Leon"
    )
)

private val SLAVIC_NAMES = NamePool(
    masculine = listOf(
        "Ivan", "Dmitri", "Viktor", "Alexei", "Nikolai", "Sergei", "Boris",
        "Mikhail", "Yuri", "Andrei", "Pavel", "Gregor", "Stanislav", "Vasily"
    ),
    feminine = listOf(
        "Katya", "Natasha", "Olga", "Svetlana", "Tatiana", "Anya", "Irina",
        "Marina", "Vera", "Zoya", "Ludmila", "Galina", "Raisa", "Oksana"
    ),
    neutral = listOf(
        "Volkov", "Petrov", "Sokolov", "Kuznetsov", "Ivanov", "Popov", "Orlov",
        "Kozlov", "Novikov", "Morozov", "Lebedev", "Egorov", "Vasiliev", "Fedorov"
    )
)

private val SYSTEM_NAMES = NamePool(
    masculine = listOf(
        "Protocol", "Cipher", "Vector", "Matrix", "Nexus", "Vertex", "Qubit",
        "Binary", "Logic", "Helix", "Vortex", "Codex", "Index", "Syntax"
    ),
    feminine = listOf(
        "Aegis", "Echo", "Iris", "Sigma", "Delta", "Lyra", "Vera",
        "Prima", "Nova", "Astra", "Vega", "Ceres", "Rhea", "Theia"
    ),
    neutral = listOf(
        "Unit", "Core", "Node", "Grid", "Flux", "Axiom", "Beacon",
        "Oracle", "Prism", "Zenith", "Arbiter", "Warden", "Proxy", "Sentinel"
    )
)

private val MIXED_NAMES = NamePool(
    masculine = EAST_ASIAN_NAMES.masculine + AFRICAN_NAMES.masculine +
               NORDIC_NAMES.masculine + ARABIC_NAMES.masculine +
               LATIN_NAMES.masculine + SLAVIC_NAMES.masculine,
    feminine = EAST_ASIAN_NAMES.feminine + AFRICAN_NAMES.feminine +
               NORDIC_NAMES.feminine + ARABIC_NAMES.feminine +
               LATIN_NAMES.feminine + SLAVIC_NAMES.feminine,
    neutral = EAST_ASIAN_NAMES.neutral + AFRICAN_NAMES.neutral +
              NORDIC_NAMES.neutral + ARABIC_NAMES.neutral +
              LATIN_NAMES.neutral + SLAVIC_NAMES.neutral
)
