package com.rpgenerator.core.rules

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.loot.*
import kotlin.random.Random

internal class RulesEngine {

    private val itemGenerator = ItemGenerator()

    fun calculateCombatOutcome(target: String, state: GameState): CombatOutcome {
        val stats = state.characterSheet.effectiveStats()

        // Base damage: 1d6 + (STR / 2) + weapon bonus
        val baseDamage = Random.nextInt(1, 7)
        val strengthBonus = stats.strength / 2
        val weaponDamage = state.characterSheet.equipment.weapon?.baseDamage ?: 0
        val totalDamage = baseDamage + strengthBonus + weaponDamage

        // XP scaling with location danger and level
        val basexp = 50L
        val dangerMultiplier = (state.currentLocation.danger / 5).coerceAtLeast(1)
        val xpGain = basexp * dangerMultiplier

        val newXP = state.characterSheet.xp + xpGain
        val shouldLevelUp = newXP >= state.characterSheet.xpToNextLevel()

        // Generate loot based on enemy type and location danger
        val lootTable = determineLootTable(target, state.currentLocation.danger)
        val lootResult = lootTable.rollLoot(
            playerLevel = state.characterSheet.level,
            locationDanger = state.currentLocation.danger,
            luckModifier = calculateLuckModifier(state)
        )

        // Generate actual items from loot drops
        val generatedItems = itemGenerator.generateLoot(
            lootResult.items,
            state.characterSheet.level
        )

        return CombatOutcome(
            damage = totalDamage,
            xpGain = xpGain,
            newXP = newXP,
            levelUp = shouldLevelUp,
            newLevel = if (shouldLevelUp) state.characterSheet.level + 1 else state.characterSheet.level,
            loot = generatedItems,
            gold = lootResult.gold
        )
    }

    private fun determineLootTable(target: String, locationDanger: Int): LootTable {
        // First try to match by enemy type from target name
        val enemyType = extractEnemyType(target)
        val enemyTable = if (enemyType != null) {
            LootTables.forEnemyType(enemyType)
        } else {
            // Fall back to location-based loot
            LootTables.forLocationDanger(locationDanger)
        }

        return enemyTable
    }

    private fun extractEnemyType(target: String): String? {
        val normalized = target.lowercase()
        return when {
            normalized.contains("goblin") -> "goblin"
            normalized.contains("kobold") -> "kobold"
            normalized.contains("orc") -> "orc"
            normalized.contains("troll") -> "troll"
            normalized.contains("ogre") -> "ogre"
            normalized.contains("dragon") -> "dragon"
            normalized.contains("demon") -> "demon"
            normalized.contains("lich") -> "lich"
            normalized.contains("boss") -> "boss"
            else -> null
        }
    }

    private fun calculateLuckModifier(state: GameState): Double {
        // Base luck from wisdom and charisma
        val stats = state.characterSheet.effectiveStats()
        val wisdomBonus = (stats.wisdom - 10) * 0.01
        val charismaBonus = (stats.charisma - 10) * 0.01

        return (wisdomBonus + charismaBonus).coerceIn(-0.2, 0.3)
    }

    private fun getXPRequiredForLevel(level: Int): Long {
        return level * 100L
    }
}

internal data class CombatOutcome(
    val damage: Int,
    val xpGain: Long,
    val newXP: Long,
    val levelUp: Boolean,
    val newLevel: Int,
    val loot: List<GeneratedItem> = emptyList(),
    val gold: Int = 0
)
