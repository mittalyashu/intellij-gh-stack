package dev.mittalyashu.ghstack.cli

import org.junit.Assert.assertEquals
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
}
