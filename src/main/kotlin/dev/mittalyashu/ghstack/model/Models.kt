package dev.mittalyashu.ghstack.model

data class StackView(
    val trunk: String,
    val currentBranch: String,
    val branches: List<StackLayer>,
)

data class StackLayer(
    val name: String,
    val head: String? = null,
    val base: String? = null,
    val isCurrent: Boolean = false,
    val isMerged: Boolean = false,
    val isQueued: Boolean = false,
    val needsRebase: Boolean = false,
    val pr: PullRequestRef? = null,
)

data class PullRequestRef(
    val number: Int,
    val url: String? = null,
    val state: String? = null,
    val merged: Boolean? = null,
)

data class TrackedStacks(
    val repository: String? = null,
    val stacks: List<TrackedStack>,
)

data class TrackedStack(
    val id: String? = null,
    val number: Int? = null,
    val trunk: String,
    val branches: List<String>,
) {
    val bottom: String? get() = branches.firstOrNull()
    val top: String? get() = branches.lastOrNull()

    fun displayLabel(): String {
        val chain = branches.joinToString(" → ").ifEmpty { "(empty)" }
        val prefix = if (number != null && number > 0) "Stack #$number" else "Local stack"
        return "$prefix  ($trunk)  $chain"
    }

    fun checkoutTarget(): String {
        return when {
            number != null && number > 0 -> number.toString()
            top != null -> top!!
            else -> trunk
        }
    }
}

sealed class StackLoadResult {
    data class Loaded(val view: StackView) : StackLoadResult()
    data object NotInStack : StackLoadResult()
    data class Failed(val message: String, val exitCode: Int? = null) : StackLoadResult()
}
