package com.github.mittalyashu.ghstack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTooltipsTest {
    @Test
    fun plainTooltipAppendsTheCommand() {
        assertEquals(
            "Check out this branch. Runs: gh stack checkout feature-b",
            plainTooltip("Check out this branch.", "gh stack checkout feature-b"),
        )
    }

    @Test
    fun plainTooltipWithoutCommandIsJustTheDescription() {
        assertEquals("Check out this branch.", plainTooltip("Check out this branch.", null))
    }

    @Test
    fun htmlTooltipPutsTheCommandInACodeBlock() {
        val tooltip = htmlTooltip("Check out this branch.", "gh stack checkout feature-b")
        assertTrue(tooltip.startsWith("<html>"))
        assertTrue(tooltip.contains("<pre"))
        assertTrue(tooltip.contains("gh stack checkout feature-b"))
    }

    @Test
    fun htmlTooltipWithoutCommandHasNoCodeBlock() {
        val tooltip = htmlTooltip("Check out this branch.", null)
        assertFalse(tooltip.contains("<pre"))
    }

    @Test
    fun htmlTooltipEscapesMarkupInTheCommand() {
        val tooltip = htmlTooltip("Check out this branch.", "gh stack checkout <branch>")
        assertTrue(tooltip.contains("&lt;branch&gt;"))
        assertFalse(tooltip.contains("<branch>"))
    }
}
