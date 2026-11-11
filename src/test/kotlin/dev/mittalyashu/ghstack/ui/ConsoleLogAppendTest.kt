package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.cli.GhCommandRun
import com.github.mittalyashu.ghstack.service.GhCommandLogEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLogAppendTest {
    private val entry = GhCommandLogEntry(
        sequence = 2,
        run = GhCommandRun(commandLine = "gh stack view", exitCode = 0, stdout = "", stderr = ""),
    )

    @Test
    fun `new entries are appended`() {
        assertTrue(shouldAppendLogEntry(disposed = false, entry = entry, lastSequence = 1))
    }

    @Test
    fun `entries replayed from history are not appended again`() {
        assertFalse(shouldAppendLogEntry(disposed = false, entry = entry, lastSequence = 2))
        assertFalse(shouldAppendLogEntry(disposed = false, entry = entry, lastSequence = 3))
    }

    @Test
    fun `entries queued before dispose are dropped`() {
        assertFalse(shouldAppendLogEntry(disposed = true, entry = entry, lastSequence = 0))
    }
}
