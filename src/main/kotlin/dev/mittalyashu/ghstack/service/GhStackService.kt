package com.github.mittalyashu.ghstack.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCli
import com.github.mittalyashu.ghstack.cli.GhExitCodes
import com.github.mittalyashu.ghstack.cli.GhResult
import com.github.mittalyashu.ghstack.cli.StackJson
import com.github.mittalyashu.ghstack.model.StackLoadResult
import com.github.mittalyashu.ghstack.model.TrackedStack
import com.github.mittalyashu.ghstack.model.TrackedStacks
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashOperations
import git4idea.stash.loadStashStack
import java.nio.file.Files
import java.nio.file.Path

/** A stash created around a rebase, identified by commit hash rather than by its (shifting) `stash@{n}` index. */
data class StashRef(val hash: String, val message: String)

/** The rebase result, plus the stash to restore when the rebase succeeded with changes stashed. */
data class RebaseOutcome(val result: GhResult, val stashToRestore: StashRef? = null)

/** Listener for [GhStackService.REPOSITORY_SELECTED]. */
fun interface RepositorySelectionListener {
    fun repositorySelected()
}

@Service(Service.Level.PROJECT)
class GhStackService(private val project: Project) {
    private val log = Logger.getInstance(GhStackService::class.java)

    companion object {
        /** Published when the panel's repository selector changes, so every open panel stays in sync. */
        val REPOSITORY_SELECTED: Topic<RepositorySelectionListener> =
            Topic.create("GH Stack repository selected", RepositorySelectionListener::class.java)
    }

    /**
     * The repository explicitly picked in the panel, or null while the selector follows the project's own
     * repository. Volatile because panel actions read it from background threads.
     */
    @Volatile
    private var selectedRoot: Path? = null

    /**
     * The root pinned for a task running on the current thread. A task snapshots the selection when it starts and
     * pins it here, so a repository switch made from another panel mid-task cannot split the task's git and `gh`
     * commands across two repositories.
     */
    private val pinnedRoot = ThreadLocal<Path?>()

    private fun effectiveRoot(): Path? = pinnedRoot.get() ?: selectedRoot

    fun gitRoot(): Path? = selectedRepository()?.root?.toNioPath()

    /** Git repositories in the project, ordered for the panel's selector (the project's own repository first). */
    fun repositoryChoices(): List<RepositoryChoice> = buildChoices(gitRepositories())

    /** The explicitly selected root, or null while the selector follows the project's own repository. */
    fun selectedRepositoryRoot(): Path? = selectedRoot

    /**
     * The repository the panel's actions run against: the selected one while it still exists, otherwise the
     * project's own repository, falling back to the first Git root when the project itself is not a repository.
     */
    fun selectedRepository(): GitRepository? {
        val repositories = gitRepositories()
        if (repositories.isEmpty()) return null
        val choice = chooseRepository(buildChoices(repositories), effectiveRoot()) ?: return null
        return repositories.firstOrNull { it.root.toNioPath().normalize() == choice.root }
    }

    /** Selects [root] for every panel action and notifies the other open panels. Call from the EDT. */
    fun selectRepository(root: Path) {
        selectedRoot = root.normalize()
        project.messageBus.syncPublisher(REPOSITORY_SELECTED).repositorySelected()
    }

    /**
     * Pins [root] as the repository for every service call made by [block] on the calling thread, then restores the
     * previous pin. Panel tasks use this so a repository selected mid-task elsewhere cannot change their target.
     */
    fun <T> withPinnedRepository(root: Path?, block: () -> T): T {
        val previous = pinnedRoot.get()
        pinnedRoot.set(root)
        return try {
            block()
        } finally {
            pinnedRoot.set(previous)
        }
    }

    private fun gitRepositories(): List<GitRepository> =
        GitRepositoryManager.getInstance(project).repositories

    private fun buildChoices(repositories: List<GitRepository>): List<RepositoryChoice> {
        val submoduleRoots = repositories.flatMap { repository ->
            repository.submodules.map { repository.root.toNioPath().resolve(it.path).normalize() }
        }.toSet()
        return buildRepositoryChoices(
            repositories = repositories.map { it.root.toNioPath().normalize() },
            projectBaseDir = project.basePath?.let(Path::of),
            submoduleRoots = submoduleRoots,
        )
    }

    fun loadCurrentStack(): StackLoadResult {
        val root = gitRoot() ?: return StackLoadResult.Failed("This project is not under Git.")
        val result = cliFor(root).viewJson()
        if (result.exitCode == GhExitCodes.NOT_IN_STACK) {
            return StackLoadResult.NotInStack
        }
        if (!result.succeeded) {
            return StackLoadResult.Failed(humanizeGhFailure(result), result.exitCode)
        }
        return runCatching { StackLoadResult.Loaded(StackJson.parseView(result.stdout)) }
            .getOrElse { StackLoadResult.Failed(it.message ?: "Could not parse gh stack view --json") }
    }

    fun loadTrackedStacks(): Result<TrackedStacks> {
        val root = gitRoot() ?: return Result.failure(IllegalStateException("This project is not under Git."))
        val file = resolveGhStackStateFile(root)
        if (!Files.isRegularFile(file)) {
            return Result.success(TrackedStacks(stacks = emptyList()))
        }
        return runCatching { StackJson.parseTrackedFile(Files.readString(file)) }
    }

    /** Short names of the selected repository's local branches, used to tell a dangling tracked stack from a live one. */
    fun localBranchNames(): Set<String> {
        val repository = selectedRepository() ?: return emptySet()
        return repository.branches.localBranches.mapTo(mutableSetOf()) { it.name }
    }

    /**
     * Removes [stack] from local tracking only (`gh stack unstack --local`), leaving GitHub untouched.
     *
     * A numbered stack is unstacked by number. A local-only stack has no number, so the CLI resolves it from the
     * current branch; a dangling stack has no local branch to resolve from, so a temporary branch is checked out
     * around the command and deleted afterwards. Must not run on the EDT.
     */
    fun pruneLocalStack(stack: TrackedStack): GhResult {
        val number = stack.number
        return if (number != null && number > 0) {
            requireCli().unstackLocal(number)
        } else {
            pruneUnnumberedStack(stack)
        }
    }

    private fun pruneUnnumberedStack(stack: TrackedStack): GhResult {
        val repository = selectedRepository()
            ?: return pruneFailure(GhStackBundle.message("prune.error.no.repository"))
        val temporaryBranch = stack.branches.firstOrNull { it.isNotBlank() }
            ?: return pruneFailure(GhStackBundle.message("prune.error.no.branch"))
        val originalBranch = currentBranchName(repository.root)
            ?: return pruneFailure(GhStackBundle.message("prune.error.detached"))

        // Point the temporary branch at HEAD so switching to it is safe even with uncommitted changes.
        if (!runGit(repository.root, GitCommand.BRANCH, temporaryBranch, "HEAD")) {
            return pruneFailure(GhStackBundle.message("prune.error.create.branch", temporaryBranch))
        }
        var result: GhResult? = null
        var cleanupError: String? = null
        try {
            result = if (runGit(repository.root, GitCommand.CHECKOUT, temporaryBranch)) {
                requireCli().unstackLocal()
            } else {
                pruneFailure(GhStackBundle.message("prune.error.checkout.branch", temporaryBranch))
            }
        } finally {
            if (!runGit(repository.root, GitCommand.CHECKOUT, originalBranch)) {
                cleanupError = GhStackBundle.message("prune.error.restore.branch", originalBranch)
            }
            if (!runGit(repository.root, GitCommand.BRANCH, "-D", temporaryBranch)) {
                val deleteError = GhStackBundle.message("prune.error.delete.branch", temporaryBranch)
                cleanupError = cleanupError?.let { "$it\n$deleteError" } ?: deleteError
            }
        }
        cleanupError?.let { return pruneFailure(it) }
        return result ?: pruneFailure(GhStackBundle.message("prune.error.unknown"))
    }

    private fun pruneFailure(message: String): GhResult = GhResult(exitCode = 1, stdout = "", stderr = message)

    /** The checked-out branch's short name, or null when HEAD is detached. */
    private fun currentBranchName(root: VirtualFile): String? {
        val handler = GitLineHandler(project, root, GitCommand.REV_PARSE)
        handler.addParameters("--abbrev-ref", "HEAD")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return result.output.firstOrNull()?.trim()?.takeUnless { it.isEmpty() || it == "HEAD" }
    }

    private fun runGit(root: VirtualFile, command: GitCommand, vararg params: String): Boolean {
        val handler = GitLineHandler(project, root, command)
        handler.addParameters(*params)
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            log.warn("git ${command.name().lowercase()} ${params.joinToString(" ")} failed: ${result.errorOutputAsJoinedString}")
        }
        return result.success()
    }

    fun addBranch(name: String): GhResult = requireCli().addBranch(name)

    fun checkout(target: String): GhResult = requireCli().checkout(target)

    fun initStack(branchName: String): GhResult = requireCli().init(branchName)

    fun rebase(vararg flags: String): GhResult = requireCli().rebase(*flags)

    /**
     * True when `gh stack rebase` would refuse to run. Mirrors gh-stack's own check
     * (`git status --porcelain --untracked-files=all`), so untracked files count. Must not run on the EDT.
     */
    fun isDirty(): Boolean {
        val repo = selectedRepository() ?: return false
        val handler = GitLineHandler(project, repo.root, GitCommand.STATUS)
        handler.addParameters("--porcelain", "--untracked-files=all")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return result.success() && result.output.any { it.isNotBlank() }
    }

    /**
     * Whether the effective git config has `rerere.enabled=true`. Unset, `false`, and a failed `git config`
     * all count as disabled, matching how git itself decides whether to record resolutions.
     */
    fun isRerereEnabled(): Boolean {
        val root = selectedRepository()?.root ?: return false
        return gitConfig(root, "--get", "--bool", "rerere.enabled") == "true"
    }

    /**
     * Whether rerere was previously declined. Reads the project-local `gh-stack.rerere-declined` key that
     * `gh stack` itself writes, so the plugin and a terminal `gh stack` agree on "don't ask again".
     */
    fun isRerereDeclined(): Boolean {
        val root = selectedRepository()?.root ?: return false
        return gitConfig(root, "--get", "gh-stack.rerere-declined") == "true"
    }

    /**
     * Enables rerere in the project-local config, matching `gh stack`'s `ensureRerere`: both `rerere.enabled`
     * and `rerere.autoupdate`. Returns false when a write fails. Must not run on the EDT.
     */
    fun enableRerere(): Boolean {
        val root = selectedRepository()?.root ?: return false
        return setGitConfig(root, "rerere.enabled", "true") &&
            setGitConfig(root, "rerere.autoupdate", "true")
    }

    /** Records a declined prompt in project-local config so neither the plugin nor `gh stack` asks again. */
    fun declineRerere(): Boolean {
        val root = selectedRepository()?.root ?: return false
        return setGitConfig(root, "gh-stack.rerere-declined", "true")
    }

    private fun gitConfig(root: VirtualFile, vararg params: String): String? {
        val handler = GitLineHandler(project, root, GitCommand.CONFIG)
        handler.addParameters(*params)
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.output.firstOrNull()?.trim() else null
    }

    private fun setGitConfig(root: VirtualFile, key: String, value: String): Boolean {
        val handler = GitLineHandler(project, root, GitCommand.CONFIG)
        handler.addParameters("--local", key, value)
        handler.setSilent(true)
        return Git.getInstance().runCommand(handler).success()
    }

    /**
     * Stashes local changes (including untracked files), runs the rebase, and reports the stash to restore
     * only if the rebase succeeded. After a failed rebase (e.g. a conflict) the stash is kept and the error mentions it.
     */
    fun rebaseWithStash(vararg flags: String): RebaseOutcome {
        val repo = selectedRepository() ?: error("This project is not under Git.")
        val message = GhStackBundle.message("stash.name")
        val headBefore = stashHead(repo.root)
        stashLocalChanges(repo.root, message)?.let { stashError ->
            return RebaseOutcome(GhResult(1, "", stashError))
        }
        val stash = stashHead(repo.root)?.takeIf { it != headBefore }

        val result = rebase(*flags)
        return when {
            stash == null -> RebaseOutcome(result)
            result.succeeded -> RebaseOutcome(result, StashRef(stash, message))
            else -> RebaseOutcome(
                result.copy(stderr = "${result.errorText()}\n\n${GhStackBundle.message("stash.kept.hint", message)}"),
            )
        }
    }

    /**
     * Applies and drops [stash] through Git4Idea's unstash, which shows its own conflict resolver and keeps the stash
     * on conflict. The progress is modal and the conflict resolver requires a background thread, so call it off the
     * EDT. Returns false when the stash can't be found.
     */
    fun restoreStash(stash: StashRef): Boolean {
        val repo = selectedRepository() ?: return false
        val info = runCatching { loadStashStack(project, repo.root) }
            .getOrNull()
            ?.firstOrNull { it.hash.asString() == stash.hash }
            ?: return false
        GitStashOperations.unstash(project, info, null, true, true)
        return true
    }

    /** Returns a git error message, or null when the push succeeded (or there was nothing to stash). */
    private fun stashLocalChanges(root: VirtualFile, message: String): String? {
        val handler = GitLineHandler(project, root, GitCommand.STASH)
        handler.addParameters("push", "--include-untracked", "-m", message)
        val result = Git.getInstance().runCommand(handler)
        if (result.success()) return null
        return result.errorOutputAsJoinedString.ifBlank { "git stash failed" }
    }

    private fun stashHead(root: VirtualFile): String? {
        val handler = GitLineHandler(project, root, GitCommand.REV_PARSE)
        handler.addParameters("--verify", "--quiet", "refs/stash")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.output.firstOrNull()?.trim()?.ifEmpty { null } else null
    }

    fun rebaseContinue(): GhResult = requireCli().rebaseContinue()

    fun rebaseAbort(): GhResult = requireCli().rebaseAbort()

    /** True while a rebase (interactive, merge or apply) is paused in the selected repository, including in linked worktrees. */
    fun isRebaseInProgress(): Boolean = selectedRepository()?.isRebaseInProgress == true

    fun refreshGit() {
        val repo = selectedRepository() ?: return
        VfsUtil.markDirtyAndRefresh(true, true, true, repo.root)
        repo.update()
        findGitCommonDir(repo.root)?.let { commonDir ->
            VfsUtil.markDirtyAndRefresh(true, true, true, commonDir)
        }
    }

    private fun requireCli(): GhCli = cliFor(gitRoot() ?: error("This project is not under Git."))

    private fun cliFor(root: Path): GhCli {
        val console = GhStackConsoleService.getInstance(project)
        return GhCli(root, onCommandRun = console::record)
    }

    private fun findGitCommonDir(root: VirtualFile): VirtualFile? {
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolveGitCommonDir(root.toNioPath()))
    }
}
