package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.cli.GhResult
import com.github.mittalyashu.ghstack.settings.StashMode
import com.intellij.openapi.ui.Messages

enum class StashAction {
    /** Run the command as-is. */
    RUN,

    /** Ask the user whether to stash first. */
    PROMPT,

    /** Stash, run the command, then restore. */
    STASH,
}

fun decideStashAction(mode: StashMode, isDirty: Boolean): StashAction = when {
    !isDirty -> StashAction.RUN
    else -> when (mode) {
        StashMode.ASK -> StashAction.PROMPT
        StashMode.ALWAYS -> StashAction.STASH
        StashMode.NEVER -> StashAction.RUN
    }
}

/**
 * Maps the result of the yes/no/cancel stash prompt to a stash choice: true to stash, false to run without
 * stashing, or null when the operation was cancelled. A dismissed dialog (Esc or the window close button) reports
 * [Messages.CANCEL].
 */
fun stashChoiceFromExitCode(exitCode: Int): Boolean? = when (exitCode) {
    Messages.YES -> true
    Messages.NO -> false
    else -> null
}

/**
 * Maps a finished command to the outcome the panel acts on: only a successful command restores the stash it
 * created, while a failed command keeps the stash and appends [keptHint] to the error.
 */
internal fun stashOutcome(
    result: GhResult,
    stash: String?,
    message: String,
    keptHint: String,
): RebaseOutcome = when {
    stash == null -> RebaseOutcome(result)
    result.succeeded -> RebaseOutcome(result, StashRef(stash, message))
    else -> RebaseOutcome(result.copy(stderr = "${result.errorText()}\n\n$keptHint"))
}
