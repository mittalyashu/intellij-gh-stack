package com.github.mittalyashu.ghstack.cli

import com.github.mittalyashu.ghstack.GhStackBundle
import com.intellij.execution.process.ProcessOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhCliTimeoutTest {
    @Test
    fun `timeout becomes a distinct failure`() {
        val output = ProcessOutput()
        output.exitCode = 143
        output.appendStderr("partial output")
        output.setTimeout()

        val result = toGhResult(output, 60_000)

        assertTrue(result.timedOut)
        assertEquals(60_000, result.timeoutMs)
        assertEquals(GhExitCodes.TIMED_OUT, result.exitCode)
        assertFalse(result.succeeded)
        assertTrue(result.errorText().startsWith(GhStackBundle.message("error.timed.out", 60)))
        assertTrue(result.errorText().contains("partial output"))
    }

    @Test
    fun `timeout with no captured output still explains itself`() {
        val output = ProcessOutput()
        output.exitCode = 143
        output.setTimeout()

        val result = toGhResult(output, 5 * 60_000)

        assertEquals(GhStackBundle.message("error.timed.out", 300), result.errorText())
    }

    @Test
    fun `normal completion keeps the exit code and output`() {
        val output = ProcessOutput()
        output.exitCode = 3
        output.appendStderr("rebase conflict")

        val result = toGhResult(output, 60_000)

        assertFalse(result.timedOut)
        assertEquals(3, result.exitCode)
        assertEquals("rebase conflict", result.errorText())
    }
}
