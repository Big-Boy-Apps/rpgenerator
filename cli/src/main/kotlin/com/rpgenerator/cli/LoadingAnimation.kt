package com.rpgenerator.cli

import kotlinx.coroutines.*

/**
 * ASCII art loading animations for the game.
 */
object LoadingAnimation {

    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    private val swordFrames = listOf(
        """
        ⚔️  Generating...
        """,
        """
        ⚔️  Generating..
        """,
        """
        ⚔️  Generating.
        """,
        """
        ⚔️  Generating
        """
    )

    private val wizardFrames = listOf(
        """
        🧙 Conjuring your adventure...
        """,
        """
        🧙‍♂️ Conjuring your adventure...
        """,
        """
        🧙 Conjuring your adventure...
        """,
        """
        🧙‍♀️ Conjuring your adventure...
        """
    )

    private val scrollFrames = listOf(
        """
        📜 Reading ancient texts...
        """,
        """
        📜 Reading ancient texts..
        """,
        """
        📜 Reading ancient texts.
        """,
        """
        📜 Reading ancient texts
        """
    )

    private val crystalFrames = listOf(
        """
        🔮 Scrying the future...
        """,
        """
        ✨ Scrying the future...
        """,
        """
        💫 Scrying the future...
        """,
        """
        ⭐ Scrying the future...
        """
    )

    private val framesets = listOf(swordFrames, wizardFrames, scrollFrames, crystalFrames)

    /**
     * Show a loading animation. Returns a job that can be cancelled.
     */
    fun show(message: String = "Loading..."): Job {
        val frames = framesets.random()
        return CoroutineScope(Dispatchers.Default).launch {
            var frameIndex = 0
            while (isActive) {
                print("\r${frames[frameIndex % frames.size].trim()}   ")
                frameIndex++
                delay(200)
            }
            print("\r" + " ".repeat(60) + "\r") // Clear the line
        }
    }

    /**
     * Show a simple spinner animation.
     */
    fun showSpinner(message: String = "Thinking..."): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            var frameIndex = 0
            while (isActive) {
                print("\r${spinnerFrames[frameIndex % spinnerFrames.size]} $message   ")
                frameIndex++
                delay(100)
            }
            print("\r" + " ".repeat(60) + "\r") // Clear the line
        }
    }

    /**
     * Show loading with a custom animation.
     */
    fun showCustom(frames: List<String>, delayMs: Long = 200): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            var frameIndex = 0
            while (isActive) {
                print("\r${frames[frameIndex % frames.size]}   ")
                frameIndex++
                delay(delayMs)
            }
            print("\r" + " ".repeat(80) + "\r") // Clear the line
        }
    }

    /**
     * Play a "terminal beep" sound (system bell).
     */
    fun beep() {
        print("\u0007")
        System.out.flush()
    }

    /**
     * Show ASCII art banner.
     */
    fun showBanner(text: String) {
        println("""

            ═══════════════════════════════════════════════════════
                        $text
            ═══════════════════════════════════════════════════════

        """.trimIndent())
    }

    /**
     * Show a fancy separator.
     */
    fun showSeparator() {
        println("─".repeat(60))
    }

    /**
     * Show "System is processing" animation.
     */
    fun showThinking(): Job {
        val thinkingFrames = listOf(
            "⠋ System is processing.  ",
            "⠙ System is processing.. ",
            "⠹ System is processing...",
            "⠸ System is processing.. ",
            "⠼ System is processing.  ",
            "⠴ System is processing.. ",
            "⠦ System is processing...",
            "⠧ System is processing.. "
        )
        return showCustom(thinkingFrames, 100)
    }

    /**
     * Show simple loading spinner.
     */
    fun showCreating(): Job {
        val frames = listOf(
            "⠋ Loading...",
            "⠙ Loading...",
            "⠹ Loading...",
            "⠸ Loading...",
            "⠼ Loading...",
            "⠴ Loading...",
            "⠦ Loading...",
            "⠧ Loading...",
            "⠇ Loading...",
            "⠏ Loading..."
        )
        return showCustom(frames, 80)
    }
}
