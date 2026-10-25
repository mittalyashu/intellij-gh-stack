package dev.mittalyashu.ghstack.service

import dev.mittalyashu.ghstack.settings.StashMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StashDecisionTest {
    @Test
    fun cleanTreeAlwaysRuns() {
        StashMode.entries.forEach {
            assertEquals(RebaseAction.RUN, decideRebaseAction(it, isDirty = false))
        }
    }

    @Test
    fun dirtyTreeFollowsMode() {
        assertEquals(RebaseAction.PROMPT, decideRebaseAction(StashMode.ASK, isDirty = true))
        assertEquals(RebaseAction.STASH, decideRebaseAction(StashMode.ALWAYS, isDirty = true))
        assertEquals(RebaseAction.RUN, decideRebaseAction(StashMode.NEVER, isDirty = true))
    }
}
