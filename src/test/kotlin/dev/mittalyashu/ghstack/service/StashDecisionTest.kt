package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.cli.GhResult
import com.github.mittalyashu.ghstack.settings.StashMode
import com.intellij.openapi.ui.Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StashDecisionTest {
    @Test
    fun cleanTreeAlwaysRuns() {
        StashMode.entries.forEach {
            assertEquals(StashAction.RUN, decideStashAction(it, isDirty = false))
        }
    }

    @Test
    fun dirtyTreeFollowsMode() {
        assertEquals(StashAction.PROMPT, decideStashAction(StashMode.ASK, isDirty = true))
        assertEquals(StashAction.STASH, decideStashAction(StashMode.ALWAYS, isDirty = true))
        assertEquals(StashAction.RUN, decideStashAction(StashMode.NEVER, isDirty = true))
    }

    @Test
    fun stashPromptMapsAnswers() {
        assertEquals(true, stashChoiceFromExitCode(Messages.YES))
        assertEquals(false, stashChoiceFromExitCode(Messages.NO))
        assertNull(stashChoiceFromExitCode(Messages.CANCEL))
    }

    @Test
    fun successfulCommandRestoresItsStash() {
        val outcome = stashOutcome(GhResult(0, "", ""), "abc123", "stash message", "kept hint")

        assertEquals(StashRef("abc123", "stash message"), outcome.stashToRestore)
    }

    @Test
    fun failedCommandKeepsTheStashAndExplainsIt() {
        val outcome = stashOutcome(GhResult(1, "", "boom"), "abc123", "stash message", "kept hint")

        assertNull(outcome.stashToRestore)
        assertEquals("boom\n\nkept hint", outcome.result.stderr)
    }

    @Test
    fun noStashMeansNothingToRestore() {
        val outcome = stashOutcome(GhResult(0, "", ""), null, "stash message", "kept hint")

        assertNull(outcome.stashToRestore)
    }
}
