package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCommandRun
import com.github.mittalyashu.ghstack.settings.ConsoleVerbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhCommandLogTest {
    private fun finished(
        exitCode: Int,
        stdout: String = "",
        stderr: String = "",
        cancelled: Boolean = false,
    ) = GhCommandLogEntry.Finished(
        sequence = 2,
        run = GhCommandRun(
            commandLine = "gh stack view --json",
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            cancelled = cancelled,
        ),
    )

    @Test
    fun `off logs nothing`() {
        assertEquals(emptyList<GhLogLine>(), formatGhCommandLog(finished(0, stdout = "{}"), ConsoleVerbosity.OFF))
    }

    @Test
    fun `a started command is shown before it finishes`() {
        val lines = formatGhCommandLog(
            GhCommandLogEntry.Started(sequence = 1, commandLine = "gh stack rebase --upstack"),
            ConsoleVerbosity.COMMANDS,
        )

        assertEquals(listOf(GhLogLine("$ gh stack rebase --upstack", GhLogKind.COMMAND)), lines)
    }

    @Test
    fun `a finished command does not repeat the command line`() {
        val lines = formatGhCommandLog(finished(0, stderr = "warning"), ConsoleVerbosity.COMMANDS)

        assertFalse(lines.any { it.kind == GhLogKind.COMMAND })
        assertEquals(listOf(GhLogLine("warning", GhLogKind.ERROR)), lines.filter { it.text.isNotEmpty() })
    }

    @Test
    fun `commands verbosity hides successful output`() {
        val lines = formatGhCommandLog(finished(0, stdout = "{}"), ConsoleVerbosity.COMMANDS)

        assertTrue(lines.none { it.text == "{}" })
    }

    @Test
    fun `full verbosity shows successful output`() {
        val lines = formatGhCommandLog(finished(0, stdout = "line one\nline two"), ConsoleVerbosity.FULL)

        assertEquals(
            listOf(
                GhLogLine("line one", GhLogKind.OUTPUT),
                GhLogLine("line two", GhLogKind.OUTPUT),
            ),
            lines.filter { it.kind == GhLogKind.OUTPUT && it.text.isNotEmpty() },
        )
    }

    @Test
    fun `failure always shows its output and exit code`() {
        val lines = formatGhCommandLog(finished(3, stdout = "partial", stderr = "conflict"), ConsoleVerbosity.COMMANDS)

        assertTrue(lines.contains(GhLogLine(GhStackBundle.message("console.exit.code", 3), GhLogKind.ERROR)))
        assertTrue(lines.contains(GhLogLine("partial", GhLogKind.OUTPUT)))
        assertTrue(lines.contains(GhLogLine("conflict", GhLogKind.ERROR)))
    }

    @Test
    fun `a cancelled run says it was cancelled instead of showing its kill exit code`() {
        val lines = formatGhCommandLog(finished(137, cancelled = true), ConsoleVerbosity.COMMANDS)

        assertTrue(lines.contains(GhLogLine(GhStackBundle.message("error.cancelled"), GhLogKind.ERROR)))
        assertFalse(lines.any { it.text == GhStackBundle.message("console.exit.code", 137) })
    }

    @Test
    fun `stderr is shown even when the command succeeds`() {
        val lines = formatGhCommandLog(finished(0, stderr = "warning"), ConsoleVerbosity.COMMANDS)

        assertTrue(lines.contains(GhLogLine("warning", GhLogKind.ERROR)))
    }
}
