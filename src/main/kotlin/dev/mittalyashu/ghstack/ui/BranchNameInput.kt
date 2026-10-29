package com.github.mittalyashu.ghstack.ui

import git4idea.validators.GitRefNameValidator

/**
 * Cleans up [text] while the user types, keeping the caret at the same logical position, and returns
 * the fixed text together with the caret offset it should move to. The original values are returned
 * unchanged when nothing needs fixing.
 *
 * [clean] defaults to git4idea's typing-time branch-name cleanup, the same rule the native Git
 * "New Branch" dialog applies, so `abc abc abc` becomes `abc-abc-abc` instead of failing in `gh stack add`.
 */
internal fun cleanUpBranchNameOnTyping(
    text: String,
    caret: Int,
    clean: (String) -> String = ::gitBranchNameOnTyping,
): Pair<String, Int> {
    val fixed = clean(text)
    if (fixed == text) return text to caret
    return fixed to clean(text.take(caret)).length
}

private fun gitBranchNameOnTyping(text: String): String =
    GitRefNameValidator.getInstance().cleanUpBranchNameOnTyping(text)
