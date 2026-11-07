package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCommandRun
import com.github.mittalyashu.ghstack.settings.ConsoleVerbosity

/** A logged invocation, numbered so a console that attaches late can replay history without duplicates. */
data class GhCommandLogEntry(val sequence: Long, val run: GhCommandRun)

enum class GhLogKind { COMMAND, OUTPUT, ERROR }

data class GhLogLine(val text: String, val kind: GhLogKind)

/**
 * Formats one invocation for the GH Stack console. Command lines are always shown; successful output is only
 * shown in [ConsoleVerbosity.FULL], while failures always include their output and exit code.
 */
internal fun formatGhCommandLog(entry: GhCommandLogEntry, verbosity: ConsoleVerbosity): List<GhLogLine> {
    if (verbosity == ConsoleVerbosity.OFF) return emptyList()
    val run = entry.run
    val lines = mutableListOf<GhLogLine>()
    lines += GhLogLine("$ ${run.commandLine}", GhLogKind.COMMAND)
    if (!run.succeeded) {
        lines += GhLogLine(GhStackBundle.message("console.exit.code", run.exitCode), GhLogKind.ERROR)
    }
    if (verbosity == ConsoleVerbosity.FULL || !run.succeeded) {
        appendOutput(lines, run.stdout, GhLogKind.OUTPUT)
    }
    appendOutput(lines, run.stderr, GhLogKind.ERROR)
    lines += GhLogLine("", GhLogKind.OUTPUT)
    return lines
}

private fun appendOutput(lines: MutableList<GhLogLine>, text: String, kind: GhLogKind) {
    text.trimEnd().takeIf { it.isNotEmpty() }?.lineSequence()?.forEach { lines += GhLogLine(it, kind) }
}
