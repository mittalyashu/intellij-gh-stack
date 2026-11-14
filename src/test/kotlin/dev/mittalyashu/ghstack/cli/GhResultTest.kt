package com.github.mittalyashu.ghstack.cli

import com.github.mittalyashu.ghstack.GhStackBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GhResultTest {
    @Test
    fun errorTextPrefersStderr() {
        val result = GhResult(exitCode = 3, stdout = "out", stderr = "rebase conflict")
        assertEquals("rebase conflict", result.errorText())
    }

    @Test
    fun errorTextFallsBackToStdoutWhenStderrIsBlank() {
        val result = GhResult(exitCode = 1, stdout = "some output", stderr = "  \n")
        assertEquals("some output", result.errorText())
    }

    @Test
    fun errorTextFallsBackToExitCodeWhenBothAreBlank() {
        val result = GhResult(exitCode = 4, stdout = "", stderr = "")
        assertEquals("Command failed with exit code 4", result.errorText())
    }

    @Test
    fun errorTextReportsTimeoutWhenNoOutputWasCaptured() {
        val result = GhResult(exitCode = GhExitCodes.TIMED_OUT, stdout = "", stderr = "", timeoutMs = 60_000)
        assertEquals(GhStackBundle.message("error.timed.out", 60), result.errorText())
    }

    @Test
    fun cancelledDefaultsToFalse() {
        val result = GhResult(exitCode = 0, stdout = "", stderr = "")
        assertFalse(result.cancelled)
    }

    @Test
    fun errorTextReportsCancellationWhenNoOutputWasCaptured() {
        val result = GhResult(exitCode = 137, stdout = "", stderr = "", cancelled = true)
        assertEquals(GhStackBundle.message("error.cancelled"), result.errorText())
    }

    @Test
    fun errorTextKeepsCapturedOutputForACancelledRun() {
        val result = GhResult(exitCode = 137, stdout = "partial output", stderr = "", cancelled = true)
        assertEquals("partial output", result.errorText())
    }

    @Test
    fun cancelledResultIsNeverASuccess() {
        val result = GhResult(exitCode = 0, stdout = "", stderr = "", cancelled = true)
        assertFalse(result.succeeded)
    }
}
