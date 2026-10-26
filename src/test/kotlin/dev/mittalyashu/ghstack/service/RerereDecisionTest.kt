package com.github.mittalyashu.ghstack.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RerereDecisionTest {
    @Test
    fun enabledSkips() {
        assertEquals(RerereAction.SKIP, decideRerereAction(enabled = true, declined = false, interactive = true))
        assertEquals(RerereAction.SKIP, decideRerereAction(enabled = true, declined = true, interactive = true))
    }

    @Test
    fun declinedSkips() {
        assertEquals(RerereAction.SKIP, decideRerereAction(enabled = false, declined = true, interactive = true))
    }

    @Test
    fun nonInteractiveSkips() {
        assertEquals(RerereAction.SKIP, decideRerereAction(enabled = false, declined = false, interactive = false))
    }

    @Test
    fun promptsWhenDisabledAndInteractive() {
        assertEquals(RerereAction.PROMPT, decideRerereAction(enabled = false, declined = false, interactive = true))
    }
}
