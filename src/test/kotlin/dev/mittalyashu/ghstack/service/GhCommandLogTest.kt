package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCommandRun
import com.github.mittalyashu.ghstack.settings.ConsoleVerbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhCommandLogTest {
    private fun entry(exitCode: Int, stdout: String = "", stderr: String = "") = GhCommandLogEntry(
        sequence = 1,
        run = GhCommandRun(
            commandLine = "gh stack view --json",
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
        ),
    )

    @Test
    fun `off logs nothing`() {
        assertEquals(emptyList<GhLogLine>(), formatGhCommandLog(entry(0, stdout = "{}"), ConsoleVerbosity.OFF))
    }

    @Test
    fun `commands verbosity shows the command line and hides successful output`() {
        val lines = formatGhCommandLog(entry(0, stdout = "{}", stderr = ""), ConsoleVerbosity.COMMANDS)

        assertEquals(GhLogLine("$ gh stack view --json", GhLogKind.COMMAND), lines.first())
        assertTrue(lines.none { it.text == "{}" })
    }

    @Test
    fun `full verbosity shows successful output`() {
        val lines = formatGhCommandLog(entry(0, stdout = "line one\nline two"), ConsoleVerbosity.FULL)

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
        val lines = formatGhCommandLog(entry(3, stdout = "partial", stderr = "conflict"), ConsoleVerbosity.COMMANDS)

        assertTrue(lines.contains(GhLogLine(GhStackBundle.message("console.exit.code", 3), GhLogKind.ERROR)))
        assertTrue(lines.contains(GhLogLine("partial", GhLogKind.OUTPUT)))
        assertTrue(lines.contains(GhLogLine("conflict", GhLogKind.ERROR)))
    }

    @Test
    fun `stderr is shown even when the command succeeds`() {
        val lines = formatGhCommandLog(entry(0, stderr = "warning"), ConsoleVerbosity.COMMANDS)

        assertTrue(lines.contains(GhLogLine("warning", GhLogKind.ERROR)))
    }
}
