package com.dervarex.unweird.intellij

import java.util.Locale

/**
 * unweird's shortcut patterns ("Ctrl+Shift+S"), checked the way {@code KeyPress.Shortcut.parse} reads them.
 * The library accepts any word as a key, so a typo like "Ctl+S" silently becomes plain "S"; this finds those.
 */
object ShortcutSyntax {

    /** What's wrong with a pattern, and a corrected pattern if there's an obvious one. */
    data class Problem(val message: String, val suggestion: String?)

    private val MODIFIERS = mapOf(
        "ctrl" to "Ctrl", "control" to "Ctrl", "shift" to "Shift", "alt" to "Alt", "option" to "Alt",
        "meta" to "Meta", "cmd" to "Meta", "super" to "Meta", "win" to "Meta",
    )

    private val ALIASES = mapOf("esc" to "Escape", "return" to "Enter", "del" to "Delete")

    private val NAMED_KEYS = listOf(
        "Enter", "Escape", "Tab", "Space", "Backspace", "Delete", "Insert", "Home", "End", "PageUp", "PageDown",
        "Left", "Right", "Up", "Down", "Plus", "Minus",
    )

    /** Every key name in its usual spelling: letters, digits, F1..F25 and the named keys. */
    val KEYS: List<String> = ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } +
            (1..25).map { "F$it" } + NAMED_KEYS

    private val KEYS_BY_LOWER = KEYS.associateBy { it.lowercase(Locale.ROOT) }

    /** Null if {@code pattern} is a valid shortcut. */
    fun check(pattern: String): Problem? {
        var trimmed = pattern.trim()
        if (trimmed.isEmpty()) {
            return Problem("Empty shortcut", null)
        }
        if (trimmed.endsWith("++")) {
            trimmed = trimmed.dropLast(1) + "Plus"
        }
        val parts = trimmed.split(Regex("\\s*\\+\\s*"))
        if (parts.any { it.isEmpty() }) {
            return Problem("Incomplete shortcut: a '+' with nothing after it", null)
        }
        val fixed = mutableListOf<String>()
        var keys = 0
        var unknown: String? = null
        for (part in parts) {
            val lower = part.lowercase(Locale.ROOT)
            val modifier = MODIFIERS[lower]
            val key = ALIASES[lower] ?: KEYS_BY_LOWER[lower]
            when {
                modifier != null -> fixed += modifier
                key != null -> {
                    keys++
                    fixed += key
                }
                else -> {
                    if (unknown == null) {
                        unknown = part
                    }
                    val guess = closest(lower)
                    fixed += guess ?: part
                    if (guess != null && guess !in MODIFIERS.values) {
                        keys++
                    }
                }
            }
        }
        val suggestion = fixed.joinToString("+").takeIf { it != pattern.trim() }
        if (unknown != null) {
            val corrected = suggestion?.takeIf { check(it) == null }
            return Problem("Unknown key \"$unknown\": unweird would read this shortcut as something else" +
                    (corrected?.let { ". Did you mean \"$it\"?" } ?: ""), corrected)
        }
        if (keys == 0) {
            return Problem("No key in this shortcut, only modifiers", null)
        }
        if (keys > 1) {
            return Problem("More than one key: only the last one counts", null)
        }
        return null
    }

    /** The nearest known name (modifier or key) within two edits, in its usual spelling. */
    private fun closest(lower: String): String? {
        val candidates = MODIFIERS.keys.map { it to MODIFIERS.getValue(it) } +
                ALIASES.map { it.key to it.value } + KEYS_BY_LOWER.map { it.key to it.value }
        return candidates
            .filter { it.first.length > 1 }
            .map { it to distance(lower, it.first) }
            .filter { it.second <= 2 && it.second < lower.length }
            .minByOrNull { it.second }
            ?.first?.second
    }

    private fun distance(a: String, b: String): Int {
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            current.copyInto(previous)
        }
        return previous[b.length]
    }
}
