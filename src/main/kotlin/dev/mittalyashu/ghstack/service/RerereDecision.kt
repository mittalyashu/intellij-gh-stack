package com.github.mittalyashu.ghstack.service

enum class RerereAction {
    /** rerere is already on, was declined before, or there is nobody to ask: leave git config alone. */
    SKIP,

    /** Ask the user whether to enable git rerere, then enable it or record the decline. */
    PROMPT,
}

/**
 * Mirrors `gh stack`'s own `ensureRerere`: skip once rerere is enabled or was declined (both project-local
 * config), and skip when there is no interactive user to ask, so a headless run is never blocked on a prompt.
 */
fun decideRerereAction(enabled: Boolean, declined: Boolean, interactive: Boolean): RerereAction = when {
    enabled || declined || !interactive -> RerereAction.SKIP
    else -> RerereAction.PROMPT
}
