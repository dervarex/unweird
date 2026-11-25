package com.dervarex.unweird.intellij

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutSyntaxTest {

    @Test
    fun validPatterns() {
        for (pattern in listOf("Ctrl+S", "ctrl+shift+z", "F11", "Escape", "Esc", "Ctrl++", "Cmd+Q", "Alt+Left", "PageDown", "Delete")) {
            assertNull(pattern, ShortcutSyntax.check(pattern))
        }
    }

    @Test
    fun typosGetASuggestion() {
        assertEquals("Ctrl+S", ShortcutSyntax.check("Ctl+S")?.suggestion)
        assertEquals("Shift+Escape", ShortcutSyntax.check("Shfit+Escape")?.suggestion)
        assertEquals("Ctrl+Enter", ShortcutSyntax.check("Ctrl+Entr")?.suggestion)
    }

    @Test
    fun nonsenseHasNoSuggestion() {
        val problem = ShortcutSyntax.check("Ctrl+Banana")
        assertNotNull(problem)
        assertNull(problem!!.suggestion)
    }

    @Test
    fun structuralProblems() {
        assertNotNull(ShortcutSyntax.check("Ctrl+S+D")) // two keys
        assertNotNull(ShortcutSyntax.check("Ctrl+Shift")) // no key
        assertNotNull(ShortcutSyntax.check("Ctrl+")) // incomplete
        assertNotNull(ShortcutSyntax.check(""))
    }
}
