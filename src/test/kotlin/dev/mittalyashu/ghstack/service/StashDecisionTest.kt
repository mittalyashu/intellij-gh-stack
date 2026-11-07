package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.settings.StashMode
import com.intellij.openapi.ui.Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun stashPromptMapsAnswers() {
        assertEquals(true, stashChoiceFromExitCode(Messages.YES))
        assertEquals(false, stashChoiceFromExitCode(Messages.NO))
        assertNull(stashChoiceFromExitCode(Messages.CANCEL))
    }
}
