package com.github.mittalyashu.ghstack.service

import java.nio.file.Path

/** A Git repository offered by the panel's repository selector, labelled for display. */
data class RepositoryChoice(
    val root: Path,
    val label: String,
    val isSubmodule: Boolean,
)

/**
 * Builds the selector's entries: the project's own repository first, then a repository containing the project
 * (when the project is opened in a subdirectory of a repo), then the other roots inside the project, then roots
 * outside it. Within each group entries are ordered by [label].
 *
 * Labels are paths relative to [projectBaseDir] (a submodule shows as `libs/foo`); a repository that is or contains
 * the project is labelled with its directory name, and a root outside the project keeps its absolute path.
 */
internal fun buildRepositoryChoices(
    repositories: Collection<Path>,
    projectBaseDir: Path?,
    submoduleRoots: Set<Path>,
): List<RepositoryChoice> {
    val base = projectBaseDir?.normalize()
    return repositories
        .map { it.normalize() }
        .distinct()
        .map { root ->
            RepositoryChoice(
                root = root,
                label = repositoryLabel(root, base),
                isSubmodule = root in submoduleRoots,
            )
        }
        .sortedWith(
            compareBy<RepositoryChoice>(
                { repositoryRank(it.root, base) },
                { ancestorDepthDescending(it.root, base) },
                { it.label },
            )
        )
}

/**
 * The entry to show for [selected]: that root when it is still present, otherwise the preferred (first) entry.
 * A null [selected] means no repository was picked explicitly.
 */
internal fun chooseRepository(choices: List<RepositoryChoice>, selected: Path?): RepositoryChoice? {
    val root = selected?.normalize()
    return choices.firstOrNull { it.root == root } ?: choices.firstOrNull()
}

/**
 * The root auto mode follows for [file]: the deepest repository that contains it, so a file in a submodule
 * resolves to the submodule rather than its superproject. A file outside every repository (or no file at all,
 * e.g. when no editor is open) keeps [previous], so opening a scratch file does not move the panel.
 */
internal fun resolveAutoRepository(
    choices: List<RepositoryChoice>,
    file: Path?,
    previous: Path?,
): Path? {
    val path = file?.normalize() ?: return previous
    return choices
        .filter { path.startsWith(it.root) }
        .maxByOrNull { it.root.nameCount }
        ?.root
        ?: previous
}

/**
 * The root a service call runs against: the root pinned by a running task wins over the user's explicit choice,
 * which wins over auto mode's editor-derived root. Null means no preference, so [chooseRepository] picks the
 * preferred entry.
 */
internal fun effectiveRepositoryRoot(pinned: Path?, selected: Path?, auto: Path?): Path? =
    pinned ?: selected ?: auto

private fun repositoryRank(root: Path, base: Path?): Int = when {
    base == null -> 0
    root == base -> 0
    base.startsWith(root) -> 1
    root.startsWith(base) -> 2
    else -> 3
}

/** Sorts nested repositories that contain the project nearest-first (the shallowest one is the least specific). */
private fun ancestorDepthDescending(root: Path, base: Path?): Int =
    if (base != null && root != base && base.startsWith(root)) -root.nameCount else 0

private fun repositoryLabel(root: Path, base: Path?): String {
    if (base == null) return root.toString()
    if (root == base || base.startsWith(root)) return root.fileName?.toString() ?: root.toString()
    val relative = runCatching { base.relativize(root) }.getOrNull()
    if (relative == null || relative.startsWith("..")) return root.toString()
    return relative.toString().replace('\\', '/')
}
