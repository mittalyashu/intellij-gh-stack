package com.github.mittalyashu.ghstack.cli

import com.github.mittalyashu.ghstack.GhStackBundle

data class GhResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    /** The timeout that stopped the process, or null when it finished on its own. */
    val timeoutMs: Int? = null,
    /** True when the user stopped the process before it finished. */
    val cancelled: Boolean = false,
) {
    val succeeded: Boolean get() = exitCode == 0 && !cancelled

    val timedOut: Boolean get() = timeoutMs != null

    fun errorText(): String {
        val captured = listOf(stderr, stdout)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
        val timeout = timeoutMs
        return when {
            captured != null -> captured
            timeout != null -> GhStackBundle.message("error.timed.out", timeout / 1000)
            cancelled -> GhStackBundle.message("error.cancelled")
            else -> "Command failed with exit code $exitCode"
        }
    }
}

object GhExitCodes {
    const val SUCCESS = 0
    const val NOT_IN_STACK = 2

    /** Exit code used when the plugin stops a command at its timeout (the `timeout(1)` convention). */
    const val TIMED_OUT = 124
}
