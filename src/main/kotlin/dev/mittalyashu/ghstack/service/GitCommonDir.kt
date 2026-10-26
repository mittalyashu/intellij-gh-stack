package dev.mittalyashu.ghstack.service

import java.nio.file.Files
import java.nio.file.Path

/** The name of gh-stack's catalog file, stored in Git's common directory (not the per-worktree git dir). */
internal const val GH_STACK_STATE_FILE = "gh-stack"

/**
 * Resolves gh-stack's catalog path, `<common-dir>/gh-stack`, for the repository checked out at [gitRoot].
 *
 * `gh stack` stores its catalog in Git's common directory, which is only `<root>/.git` for a plain clone. In a linked
 * worktree (or submodule) `<root>/.git` is a file pointing at a per-worktree git dir whose `commondir` file points back
 * at the shared directory; with `git init --separate-git-dir` it points directly at the common dir. Resolving through
 * those layouts is what keeps the "All stacks" lookup working outside a standard clone.
 *
 * Git4idea's `GitRepository` does not expose the common directory on this platform version, so the pointer files are
 * read directly. The filesystem accessors are injectable so the resolution can be unit-tested without touching disk.
 */
internal fun resolveGitCommonDir(
    gitRoot: Path,
    isDirectory: (Path) -> Boolean,
    readText: (Path) -> String?,
): Path {
    val dotGit = gitRoot.resolve(".git")
    val gitDir = if (isDirectory(dotGit)) {
        dotGit
    } else {
        readText(dotGit)
            ?.let(::parseGitDirPointer)
            ?.let { resolveAgainst(gitRoot, it) }
            ?: return dotGit
    }

    val commonDir = readText(gitDir.resolve("commondir"))
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return gitDir

    return resolveAgainst(gitDir, commonDir)
}

/** Resolves [gitRoot]'s Git common directory using the real filesystem, falling back to `<root>/.git` on bad pointers. */
internal fun resolveGitCommonDir(gitRoot: Path): Path =
    runCatching { resolveGitCommonDir(gitRoot, { Files.isDirectory(it) }, ::readTextOrNull) }
        .getOrElse { gitRoot.resolve(".git") }

/** The path of gh-stack's catalog for [gitRoot], using the real filesystem. */
internal fun resolveGhStackStateFile(gitRoot: Path): Path =
    resolveGitCommonDir(gitRoot).resolve(GH_STACK_STATE_FILE)

/** The path of gh-stack's catalog for [gitRoot], with injectable filesystem accessors for testing. */
internal fun resolveGhStackStateFile(
    gitRoot: Path,
    isDirectory: (Path) -> Boolean,
    readText: (Path) -> String?,
): Path = resolveGitCommonDir(gitRoot, isDirectory, readText).resolve(GH_STACK_STATE_FILE)

/** The `gitdir: <path>` target of a `.git` pointer file, or null when the content is not a pointer. */
private fun parseGitDirPointer(content: String): String? =
    content.lineSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith("gitdir:") }
        ?.removePrefix("gitdir:")
        ?.trim()
        ?.takeIf(String::isNotEmpty)

private fun resolveAgainst(base: Path, path: String): Path {
    val candidate = Path.of(path)
    return if (candidate.isAbsolute) candidate.normalize() else base.resolve(candidate).normalize()
}

private fun readTextOrNull(path: Path): String? =
    runCatching { Files.readString(path) }.getOrNull()
