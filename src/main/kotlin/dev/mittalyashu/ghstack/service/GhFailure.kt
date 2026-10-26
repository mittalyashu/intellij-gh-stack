package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhInvocation
import com.github.mittalyashu.ghstack.cli.GhResult

/**
 * Rewrites a raw gh/gh-stack error into a user-facing message when the failure is a missing
 * prerequisite rather than a stack error.
 *
 * `exitCode == 127` means gh never ran: either [GhInvocation] could not find it on PATH or the
 * process could not be started. gh-stack's own exit codes are 0-8, so keying off 127 (instead of
 * sniffing for "not found") keeps genuine gh-stack text such as `stack <branch> not found` or
 * `repository not found` intact.
 */
internal fun humanizeGhFailure(result: GhResult): String {
    val text = result.errorText()
    val lower = text.lowercase()
    return when {
        result.exitCode == 127 -> GhStackBundle.message("status.gh.missing")
        lower.contains("unknown command") && lower.contains("stack") ->
            GhStackBundle.message("status.extension.missing")
        else -> text
    }
}
