package dev.mittalyashu.ghstack.cli

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

            findOnPath("pkgx")?.let { pkgx -> return Ready(listOf(pkgx, "gh")) }
            findOnPath("gh")?.let { gh -> return Ready(listOf(gh)) }

            return Missing(
                "GitHub CLI (gh) was not found. Configure a custom path under " +
                    "Settings | Tools | GH Stack."
            )
        }
    }
}
