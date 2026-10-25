package dev.mittalyashu.ghstack.service

import dev.mittalyashu.ghstack.settings.ShelveMode

enum class RebaseAction {
    /** Run `gh stack rebase` as-is. */
    RUN,

    /** Ask the user whether to shelve first. */
    PROMPT,

    /** Shelve, rebase, then restore. */
    SHELVE,
}

fun decideRebaseAction(mode: ShelveMode, hasLocalChanges: Boolean): RebaseAction = when {
    !hasLocalChanges -> RebaseAction.RUN
    else -> when (mode) {
        ShelveMode.ASK -> RebaseAction.PROMPT
        ShelveMode.ALWAYS -> RebaseAction.SHELVE
        ShelveMode.NEVER -> RebaseAction.RUN
    }
}
