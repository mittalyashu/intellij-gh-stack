package dev.mittalyashu.ghstack.service

import dev.mittalyashu.ghstack.settings.ShelveMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelveDecisionTest {
    @Test
    fun cleanTreeAlwaysRuns() {
        ShelveMode.entries.forEach {
            assertEquals(RebaseAction.RUN, decideRebaseAction(it, hasLocalChanges = false))
        }
    }

    @Test
    fun dirtyTreeFollowsMode() {
        assertEquals(RebaseAction.PROMPT, decideRebaseAction(ShelveMode.ASK, hasLocalChanges = true))
        assertEquals(RebaseAction.SHELVE, decideRebaseAction(ShelveMode.ALWAYS, hasLocalChanges = true))
        assertEquals(RebaseAction.RUN, decideRebaseAction(ShelveMode.NEVER, hasLocalChanges = true))
    }
}
