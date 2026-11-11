package com.github.mittalyashu.ghstack.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.stash.GitStashOperations
import git4idea.stash.loadStashStack

/** A stash created around a rebase, identified by commit hash rather than by its (shifting) `stash@{n}` index. */
data class StashRef(val hash: String, val message: String)

/**
 * Git operations on the repository targeted by the panel: branches, working-tree and config state, stashes, rebase
 * state and the VFS refresh after a command. Stack commands and repository selection live in [GhStackService].
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {
    private val log = Logger.getInstance(GitService::class.java)

    /** Short names of [repository]'s local branches. */
    fun localBranchNames(repository: GitRepository): Set<String> =
        repository.branches.localBranches.mapTo(mutableSetOf()) { it.name }

    /** The checked-out branch's short name, or null when HEAD is detached. */
    fun currentBranchName(repository: GitRepository): String? {
        val result = run(repository, GitCommand.REV_PARSE, "--abbrev-ref", "HEAD")
        return result.output.firstOrNull()?.trim()?.takeUnless { it.isEmpty() || it == "HEAD" }
    }

    /** Points [name] at [startPoint] without checking it out. */
    fun createBranch(repository: GitRepository, name: String, startPoint: String = "HEAD"): Boolean =
        runChecked(repository, GitCommand.BRANCH, name, startPoint)

    /** Checks [name] out, keeping uncommitted changes that do not conflict. */
    fun checkoutBranch(repository: GitRepository, name: String): Boolean =
        runChecked(repository, GitCommand.CHECKOUT, name)

    /** Force-deletes the local branch [name]; commits reachable from elsewhere remain. */
    fun deleteBranch(repository: GitRepository, name: String): Boolean =
        runChecked(repository, GitCommand.BRANCH, "-D", name)

    /**
     * True when `gh stack rebase` would refuse to run. Mirrors gh-stack's own check
     * (`git status --porcelain --untracked-files=all`), so untracked files count.
     */
    fun isDirty(repository: GitRepository): Boolean {
        val result = run(repository, GitCommand.STATUS, "--porcelain", "--untracked-files=all")
        return result.success() && result.output.any { it.isNotBlank() }
    }

    /**
     * Whether the effective git config has `rerere.enabled=true`. Unset, `false`, and a failed `git config`
     * all count as disabled, matching how git itself decides whether to record resolutions.
     */
    fun isRerereEnabled(repository: GitRepository): Boolean =
        gitConfig(repository, "--get", "--bool", "rerere.enabled") == "true"

    /**
     * Whether rerere was previously declined. Reads the project-local `gh-stack.rerere-declined` key that
     * `gh stack` itself writes, so the plugin and a terminal `gh stack` agree on "don't ask again".
     */
    fun isRerereDeclined(repository: GitRepository): Boolean =
        gitConfig(repository, "--get", "gh-stack.rerere-declined") == "true"

    /**
     * Enables rerere in the project-local config, matching `gh stack`'s `ensureRerere`: both `rerere.enabled`
     * and `rerere.autoupdate`. Returns false when a write fails. Must not run on the EDT.
     */
    fun enableRerere(repository: GitRepository): Boolean =
        setGitConfig(repository, "rerere.enabled", "true") &&
            setGitConfig(repository, "rerere.autoupdate", "true")

    /** Records a declined prompt in project-local config so neither the plugin nor `gh stack` asks again. */
    fun declineRerere(repository: GitRepository): Boolean =
        setGitConfig(repository, "gh-stack.rerere-declined", "true")

    /** The `refs/stash` commit hash, or null when no stash exists. */
    fun stashHead(repository: GitRepository): String? {
        val result = run(repository, GitCommand.REV_PARSE, "--verify", "--quiet", "refs/stash")
        return if (result.success()) result.output.firstOrNull()?.trim()?.ifEmpty { null } else null
    }

    /** Returns a git error message, or null when the push succeeded (or there was nothing to stash). */
    fun stashLocalChanges(repository: GitRepository, message: String): String? {
        val result = run(
            repository,
            GitCommand.STASH,
            "push",
            "--include-untracked",
            "-m",
            message,
            silent = false,
        )
        if (result.success()) return null
        return result.errorOutputAsJoinedString.ifBlank { "git stash failed" }
    }

    /**
     * Applies and drops [stash] through Git4Idea's unstash, which shows its own conflict resolver and keeps the stash
     * on conflict. The progress is modal and the conflict resolver requires a background thread, so call it off the
     * EDT. Returns false when the stash can't be found.
     */
    fun restoreStash(repository: GitRepository, stash: StashRef): Boolean {
        val info = runCatching { loadStashStack(project, repository.root) }
            .getOrNull()
            ?.firstOrNull { it.hash.asString() == stash.hash }
            ?: return false
        GitStashOperations.unstash(project, info, null, true, true)
        return true
    }

    /** True while a rebase (interactive, merge or apply) is paused in [repository], including in linked worktrees. */
    fun isRebaseInProgress(repository: GitRepository): Boolean = repository.isRebaseInProgress

    /** Refreshes [repository]'s VFS state and its catalog's common directory. Must not run on the EDT. */
    fun refresh(repository: GitRepository) {
        VfsUtil.markDirtyAndRefresh(true, true, true, repository.root)
        repository.update()
        findGitCommonDir(repository.root)?.let { commonDir ->
            VfsUtil.markDirtyAndRefresh(true, true, true, commonDir)
        }
    }

    private fun gitConfig(repository: GitRepository, vararg params: String): String? {
        val result = run(repository, GitCommand.CONFIG, *params)
        return if (result.success()) result.output.firstOrNull()?.trim() else null
    }

    private fun setGitConfig(repository: GitRepository, key: String, value: String): Boolean =
        runChecked(repository, GitCommand.CONFIG, "--local", key, value)

    private fun run(
        repository: GitRepository,
        command: GitCommand,
        vararg params: String,
        silent: Boolean = true,
    ): GitCommandResult {
        val handler = GitLineHandler(project, repository.root, command)
        handler.addParameters(*params)
        handler.setSilent(silent)
        return Git.getInstance().runCommand(handler)
    }

    private fun runChecked(repository: GitRepository, command: GitCommand, vararg params: String): Boolean {
        val result = run(repository, command, *params)
        if (!result.success()) {
            log.warn("git ${command.name().lowercase()} ${params.joinToString(" ")} failed: ${result.errorOutputAsJoinedString}")
        }
        return result.success()
    }

    private fun findGitCommonDir(root: VirtualFile): VirtualFile? =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolveGitCommonDir(root.toNioPath()))
}
