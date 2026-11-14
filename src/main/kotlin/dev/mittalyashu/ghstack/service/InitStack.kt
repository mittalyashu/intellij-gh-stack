package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.model.TrackedStack

/** Everything the Initialize stack dialog needs; loaded off the EDT (branch list plus a git lookup). */
data class InitStackOptions(
    val localBranches: List<String>,
    val currentBranch: String?,
    val defaultBase: String?,
    val trackedStacks: List<TrackedStack>,
)

/**
 * The stack that already owns [branch], which makes `gh stack init` refuse to run. gh-stack only blocks a
 * non-trunk member, and a trunk branch can be shared across stacks, so a trunk match is never returned.
 */
internal fun existingStackForBranch(stacks: List<TrackedStack>, branch: String): TrackedStack? {
    if (branch.isEmpty()) return null
    return stacks.firstOrNull { stack -> stack.trunk != branch && branch in stack.branches }
}

/**
 * The stack that already contains [branch], matching gh-stack's `Contains` (trunk included). `gh stack init`
 * rejects a first branch that is already tracked, whether as a layer or as another stack's trunk.
 */
internal fun stackContainingBranch(stacks: List<TrackedStack>, branch: String): TrackedStack? {
    if (branch.isEmpty()) return null
    return stacks.firstOrNull { stack -> branch == stack.trunk || branch in stack.branches }
}

/**
 * The default trunk for a new stack: the repository's default branch when it is known (it may live only on the
 * remote, which gh-stack resolves), else a conventional `main`/`master`, else the current branch, else the
 * first local branch.
 */
internal fun defaultBaseBranch(
    localBranches: List<String>,
    currentBranch: String?,
    defaultBranch: String?,
): String? {
    defaultBranch?.takeIf { it.isNotEmpty() }?.let { return it }
    localBranches.firstOrNull { it == "main" }?.let { return it }
    localBranches.firstOrNull { it == "master" }?.let { return it }
    currentBranch?.takeIf { it in localBranches }?.let { return it }
    return localBranches.firstOrNull()
}

/** The first branch to propose: the checked-out branch when it is not the trunk, otherwise nothing. */
internal fun defaultFirstBranch(currentBranch: String?, base: String?): String =
    currentBranch?.takeIf { it.isNotEmpty() && it != base }.orEmpty()
