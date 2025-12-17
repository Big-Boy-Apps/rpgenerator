import com.rpgenerator.core.api.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    val dbFile = File.createTempFile("test_debug", ".db")
    dbFile.deleteOnExit()
    
    val client = RPGClient(dbFile.absolutePath)
    
    val mockLlm = object : LLMInterface {
        override fun startAgent(systemPrompt: String): AgentStream {
            return object : AgentStream {
                override suspend fun sendMessage(message: String): kotlinx.coroutines.flow.Flow<String> {
                    return kotlinx.coroutines.flow.flowOf("Test response")
                }
            }
        }
    }
    
    val config = GameConfig(systemType = SystemType.SYSTEM_INTEGRATION)
    val game = client.startGame(config, mockLlm)
    
    val initialState = game.getState()
    println("Initial XP: ${initialState.playerStats.experience}")
    
    val events = game.processInput("fight enemy").toList()
    println("Events received: ${events.size}")
    events.forEach { event ->
        println("  - ${event::class.simpleName}: ${when(event) {
            is GameEvent.NarratorText -> event.text
            is GameEvent.CombatLog -> event.text
            is GameEvent.StatChange -> "${event.statName}: ${event.oldValue} -> ${event.newValue}"
            is GameEvent.SystemNotification -> event.text
            else -> event.toString()
        }}")
    }
    
    val updatedState = game.getState()
    println("\nUpdated XP: ${updatedState.playerStats.experience}")
    
    client.close()
    dbFile.delete()
}
