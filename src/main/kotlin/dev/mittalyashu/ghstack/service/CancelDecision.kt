package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.cli.GhResult

/** Where the stash created around a rebase goes once the command is over. */
internal enum class StashRestore {
    /** Apply and drop the stash. */
    RESTORE,

    /** Leave the stash in place. */
    KEEP,
}

/**
 * Whether a cancelled `gh stack rebase` left a journal that must be aborted.
 *
 * gh-stack writes [GH_STACK_REBASE_STATE_FILE] before cascading and refuses to start (exit 7) while a journal exists,
 * so a journal that was absent before the launch belongs to the cancelled command. A rebase that is paused now but
 * was not before can also belong to it (for example when gh-stack resolves a journal the snapshot does not see), so
 * it is worth an abort attempt too. A journal that already existed is left alone: the cancelled command never
 * started, and aborting would roll back a paused rebase the user did not ask to cancel.
 */
internal fun shouldAbortCancelledRebase(
    journalExistedBefore: Boolean,
    journalExistsAfter: Boolean,
    rebasePaused: Boolean,
): Boolean = !journalExistedBefore && (journalExistsAfter || rebasePaused)

/**
 * gh-stack reports `no rebase in progress` when `--abort` finds no journal. That is a clean no-op only when no rebase
 * is paused afterwards; a pause gh-stack does not own (e.g. a plain `git rebase`) must keep the stash out of it.
 */
internal fun isNoRebaseInProgress(result: GhResult): Boolean =
    !result.succeeded &&
        "${result.stdout}\n${result.stderr}".contains("no rebase in progress", ignoreCase = true)

/**
 * Whether the stash created around a rebase can be restored. A failed rebase leaves a paused conflict, and a failed
 * abort leaves a half-rebased cascade, so in both cases the stash is kept rather than applied into that state.
 */
internal fun decideStashRestore(succeeded: Boolean, cancelled: Boolean, abortFailed: Boolean): StashRestore = when {
    abortFailed -> StashRestore.KEEP
    succeeded || cancelled -> StashRestore.RESTORE
    else -> StashRestore.KEEP
}
