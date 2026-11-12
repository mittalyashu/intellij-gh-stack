package com.github.mittalyashu.ghstack.service

/**
 * The branch to check out after renaming [oldName] to [newName], keeping the user on the branch they started on.
 * `gh stack init` checks out the top branch, so the original branch is restored unless it was the renamed one.
 */
internal fun branchToRestore(
    originalBranch: String?,
    oldName: String,
    newName: String,
    renamed: Boolean,
): String? = when {
    originalBranch == null -> null
    renamed && originalBranch == oldName -> newName
    else -> originalBranch
}
