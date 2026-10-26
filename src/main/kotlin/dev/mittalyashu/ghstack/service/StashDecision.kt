package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.settings.StashMode

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
