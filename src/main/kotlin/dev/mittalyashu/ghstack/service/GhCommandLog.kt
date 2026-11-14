package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCommandRun
import com.github.mittalyashu.ghstack.settings.ConsoleVerbosity

/**
 * One event in the GH Stack console, numbered so a console that attaches late can replay history without duplicates.
 * A command produces a [Started] event when its process launches and a [Finished] event once it exits, which keeps a
 * long or cancelled `gh` command visible while it runs.
 */
sealed interface GhCommandLogEntry {
    val sequence: Long

    /** A command that has just started; only its command line is known yet. */
    data class Started(override val sequence: Long, val commandLine: String) : GhCommandLogEntry

    /** A command that has finished; the command line was printed by its [Started] entry. */
    data class Finished(override val sequence: Long, val run: GhCommandRun) : GhCommandLogEntry
}

enum class GhLogKind { COMMAND, OUTPUT, ERROR }

data class GhLogLine(val text: String, val kind: GhLogKind)

/**
 * Formats one console event. Command lines are shown as soon as the command starts; successful output is only shown
 * in [ConsoleVerbosity.FULL], while failures always include their output and exit code.
 */
internal fun formatGhCommandLog(entry: GhCommandLogEntry, verbosity: ConsoleVerbosity): List<GhLogLine> {
    if (verbosity == ConsoleVerbosity.OFF) return emptyList()
    return when (entry) {
        is GhCommandLogEntry.Started -> listOf(GhLogLine("$ ${entry.commandLine}", GhLogKind.COMMAND))
        is GhCommandLogEntry.Finished -> formatRun(entry.run, verbosity)
    }
}

private fun formatRun(run: GhCommandRun, verbosity: ConsoleVerbosity): List<GhLogLine> {
    val lines = mutableListOf<GhLogLine>()
    when {
        run.cancelled -> lines += GhLogLine(GhStackBundle.message("error.cancelled"), GhLogKind.ERROR)
        !run.succeeded -> lines += GhLogLine(GhStackBundle.message("console.exit.code", run.exitCode), GhLogKind.ERROR)
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
