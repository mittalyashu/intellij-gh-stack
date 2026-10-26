package com.github.mittalyashu.ghstack.cli

import com.github.mittalyashu.ghstack.GhStackBundle

sealed interface GhInvocation {
    data class Ready(val command: List<String>) : GhInvocation

    data class Missing(val message: String) : GhInvocation

    companion object {
        fun resolve(
            configuredPath: String,
            findOnPath: (String) -> String?,
        ): GhInvocation {
            val custom = configuredPath.trim()
            if (custom.isNotEmpty()) {
                return Ready(listOf(custom))
            }

            findOnPath("gh")?.let { gh -> return Ready(listOf(gh)) }

            return Missing(GhStackBundle.message("status.gh.missing"))
        }
    }
}
