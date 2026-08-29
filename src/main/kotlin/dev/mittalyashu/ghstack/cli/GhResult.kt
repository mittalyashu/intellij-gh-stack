package dev.mittalyashu.ghstack.cli

data class GhResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val succeeded: Boolean get() = exitCode == 0

    fun errorText(): String {
        return listOf(stderr, stdout)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "Command failed with exit code $exitCode"
    }
}

object GhExitCodes {
    const val SUCCESS = 0
    const val NOT_IN_STACK = 2
}
