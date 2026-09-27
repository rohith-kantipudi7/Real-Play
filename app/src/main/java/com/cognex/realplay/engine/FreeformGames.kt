package com.cognex.realplay.engine

/**
 * Creative, NON-verified games for the "without verifier" mode (§ user request). Pure JVM.
 *
 * The camera can't judge these — they're honour-system challenges shown on screen, read aloud, and
 * timed for 30 s. Because nothing has to be measurable, they can be richer and ramp up in difficulty
 * far past what a verifier could check. [forLevel] returns the game for a 1-based level (looping
 * through the list), weaving in a real detected object name when one is available so it still feels
 * about THIS table.
 */
object FreeformGames {

    data class Game(val title: String, val instruction: String, val hints: List<String>)

    private data class Template(val title: String, val make: (String?) -> String, val hints: List<String>)

    // Ordered easiest → hardest. `obj` is a detected object name (may be null).
    private val templates: List<Template> = listOf(
        Template("Show & Tell", { o -> "Hold up ${o ?: "your favourite thing"} and show it to the camera!" },
            listOf("Bring it close", "Big and clear")),
        Template("Gather Up", { _ -> "Quickly gather three different things together in front of you." },
            listOf("Any three", "Pull them close")),
        Template("Colour Hunt", { _ -> "Find something red, something blue, and something yellow — line them up!" },
            listOf("One of each colour", "Left to right")),
        Template("Tower Time", { o -> "Stack your objects into the tallest tower you can${o?.let { ", start with $it" } ?: ""}!" },
            listOf("Balance carefully", "Higher!")),
        Template("Size Order", { _ -> "Line everything up from the smallest to the biggest." },
            listOf("Compare sizes", "Smallest on the left")),
        Template("Make a Shape", { _ -> "Arrange three things into a triangle, then a square!" },
            listOf("Spread them out", "Now four corners")),
        Template("Freeze Frame", { _ -> "Strike your best superhero pose and hold it super still!" },
            listOf("Big pose", "Don't move")),
        Template("Balancing Act", { o -> "Balance ${o ?: "an object"} on the back of your hand for as long as you can!" },
            listOf("Steady hand", "Keep it level")),
        Template("Speed Sort", { _ -> "Split your things into two groups any way you like — go fast!" },
            listOf("Two piles", "Race the clock")),
        Template("Grand Finale", { _ -> "Build your wildest creation with everything on the table — impress us!" },
            listOf("Be creative", "Use it all"))
    )

    /** The game for a 1-based [level], looping; [objectLabels] supplies a real object name if any. */
    fun forLevel(level: Int, objectLabels: List<String>): Game {
        val t = templates[((level - 1).coerceAtLeast(0)) % templates.size]
        val obj = objectLabels.firstOrNull { it.isNotBlank() }
        return Game(t.title, t.make(obj), t.hints)
    }
}
