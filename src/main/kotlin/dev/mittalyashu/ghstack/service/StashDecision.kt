package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.settings.StashMode
import com.intellij.openapi.ui.Messages

enum class RebaseAction {
    /** Run `gh stack rebase` as-is. */
    RUN,

    /** Ask the user whether to stash first. */
    PROMPT,

    /** Stash, rebase, then restore. */
    STASH,
}

fun decideRebaseAction(mode: StashMode, isDirty: Boolean): RebaseAction = when {
    !isDirty -> RebaseAction.RUN
    else -> when (mode) {
        StashMode.ASK -> RebaseAction.PROMPT
        StashMode.ALWAYS -> RebaseAction.STASH
        StashMode.NEVER -> RebaseAction.RUN
    }
}

/**
 * Maps the result of the yes/no/cancel stash prompt to a stash choice: true to stash, false to rebase without
 * stashing, or null when the rebase was cancelled. A dismissed dialog (Esc or the window close button) reports
 * [Messages.CANCEL].
 */
fun stashChoiceFromExitCode(exitCode: Int): Boolean? = when (exitCode) {
    Messages.YES -> true
    Messages.NO -> false
    else -> null
}
