package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.cli.GhResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelDecisionTest {
    @Test
    fun journalCreatedByTheCommandIsAborted() {
        assertTrue(
            shouldAbortCancelledRebase(
                journalExistedBefore = false,
                journalExistsAfter = true,
                rebasePaused = false,
            )
        )
    }

    @Test
    fun pausedRebaseWithoutJournalIsAborted() {
        // gh-stack may resolve a journal from a state dir the snapshot does not watch, so a new pause is worth an
        // abort attempt even when the common-dir journal is absent.
        assertTrue(
            shouldAbortCancelledRebase(
                journalExistedBefore = false,
                journalExistsAfter = false,
                rebasePaused = true,
            )
        )
    }

    @Test
    fun preExistingJournalIsNeverAborted() {
        // gh-stack refuses to start while a journal exists, so this is a paused rebase the user did not cancel.
        assertFalse(
            shouldAbortCancelledRebase(
                journalExistedBefore = true,
                journalExistsAfter = true,
                rebasePaused = true,
            )
        )
    }

    @Test
    fun cancelBeforeTheJournalIsWrittenNeedsNoAbort() {
        assertFalse(
            shouldAbortCancelledRebase(
                journalExistedBefore = false,
                journalExistsAfter = false,
                rebasePaused = false,
            )
        )
    }

    @Test
    fun noRebaseInProgressIsRecognisedAsANoOp() {
        val result = GhResult(exitCode = 1, stdout = "", stderr = "Error: no rebase in progress")
        assertTrue(isNoRebaseInProgress(result))
    }

    @Test
    fun otherAbortFailuresAreNotSwallowed() {
        val result = GhResult(exitCode = 1, stdout = "", stderr = "could not restore branches: locked")
        assertFalse(isNoRebaseInProgress(result))
        assertFalse(isNoRebaseInProgress(GhResult(exitCode = 0, stdout = "no rebase in progress", stderr = "")))
    }

    @Test
    fun successfulRebaseRestoresTheStash() {
        assertEquals(
            StashRestore.RESTORE,
            decideStashRestore(succeeded = true, cancelled = false, abortFailed = false),
        )
    }

    @Test
    fun failedRebaseKeepsTheStash() {
        assertEquals(
            StashRestore.KEEP,
            decideStashRestore(succeeded = false, cancelled = false, abortFailed = false),
        )
    }

    @Test
    fun cancelledRebaseWithoutAbortRestoresTheStash() {
        assertEquals(
            StashRestore.RESTORE,
            decideStashRestore(succeeded = false, cancelled = true, abortFailed = false),
        )
    }

    @Test
    fun failedAbortKeepsTheStash() {
        assertEquals(
            StashRestore.KEEP,
            decideStashRestore(succeeded = false, cancelled = true, abortFailed = true),
        )
    }

    @Test
    fun rebaseOutcomeCarriesTheKeptStash() {
        val stash = StashRef(hash = "abc123", message = "GH Stack: rebase")
        val outcome = RebaseOutcome(GhResult(1, "", "conflict"), stashKept = stash)
        assertEquals(stash, outcome.stashKept)
        assertEquals(null, outcome.stashToRestore)
    }
}
