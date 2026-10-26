package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhResult
import org.junit.Assert.assertEquals
import org.junit.Test

class GhFailureTest {
    @Test
    fun missingGhReportsSettingsHint() {
        val result = GhResult(exitCode = 127, stdout = "", stderr = "Could not start gh")
        assertEquals(GhStackBundle.message("status.gh.missing"), humanizeGhFailure(result))
    }

    @Test
    fun unknownStackCommandReportsExtensionHint() {
        val result = GhResult(exitCode = 1, stdout = "", stderr = "unknown command \"stack\" for \"gh\"")
        assertEquals(GhStackBundle.message("status.extension.missing"), humanizeGhFailure(result))
    }

    @Test
    fun stackBranchNotFoundPassesThrough() {
        val result = GhResult(exitCode = 1, stdout = "", stderr = "stack feature not found")
        assertEquals("stack feature not found", humanizeGhFailure(result))
    }

    @Test
    fun repositoryNotFoundPassesThrough() {
        val result = GhResult(exitCode = 1, stdout = "", stderr = "fatal: repository 'origin' not found")
        assertEquals("fatal: repository 'origin' not found", humanizeGhFailure(result))
    }

    @Test
    fun otherErrorsPassThrough() {
        val result = GhResult(exitCode = 3, stdout = "", stderr = "rebase conflict")
        assertEquals("rebase conflict", humanizeGhFailure(result))
    }
}
