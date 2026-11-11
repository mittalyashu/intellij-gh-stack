package com.github.mittalyashu.ghstack.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCli
import com.github.mittalyashu.ghstack.cli.GhExitCodes
import com.github.mittalyashu.ghstack.cli.GhResult
import com.github.mittalyashu.ghstack.cli.StackJson
import com.github.mittalyashu.ghstack.model.StackLoadResult
import com.github.mittalyashu.ghstack.model.TrackedStack
import com.github.mittalyashu.ghstack.model.TrackedStacks
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.nio.file.Files
import java.nio.file.Path

/** The rebase result, plus the stash to restore when the rebase succeeded with changes stashed. */
data class RebaseOutcome(val result: GhResult, val stashToRestore: StashRef? = null)

/** Listener for [GhStackService.REPOSITORY_SELECTED]. */
fun interface RepositorySelectionListener {
    fun repositorySelected()
}

/**
 * The project-level gh-stack CLI service used by the panel. It owns the repository selection and the high-level
 * stack commands; plain Git operations are delegated to [GitService].
 */
@Service(Service.Level.PROJECT)
class GhStackService(private val project: Project) {
    private val git = project.getService(GitService::class.java)

    companion object {
        /** Published when the panel's repository selector changes, so every open panel stays in sync. */
        val REPOSITORY_SELECTED: Topic<RepositorySelectionListener> =
            Topic.create("GH Stack repository selected", RepositorySelectionListener::class.java)
    }

    /**
     * The repository explicitly picked in the panel, or null while the selector follows the file selected in the
     * editor. Volatile because panel actions read it from background threads.
     */
    @Volatile
    private var selectedRoot: Path? = null

    /**
     * The repository auto mode follows, resolved from the file selected in the editor. Kept apart from
     * [selectedRoot] so an explicit choice always wins; null until the editor context resolves to a repository.
     */
    @Volatile
    private var autoRoot: Path? = null

    /**
     * The root pinned for a task running on the current thread. A task snapshots the selection when it starts and
     * pins it here, so a repository switch made from another panel mid-task cannot split the task's git and `gh`
     * commands across two repositories.
     */
    private val pinnedRoot = ThreadLocal<Path?>()

    private fun effectiveRoot(): Path? = effectiveRepositoryRoot(pinnedRoot.get(), selectedRoot, autoRoot)

    fun gitRoot(): Path? = selectedRepository()?.root?.toNioPath()

    /** Git repositories in the project, ordered for the panel's selector (the project's own repository first). */
    fun repositoryChoices(): List<RepositoryChoice> = buildChoices(gitRepositories())

    /** The explicitly selected root, or null while the selector follows the file selected in the editor. */
    fun selectedRepositoryRoot(): Path? = selectedRoot

    /** Whether no repository is pinned and the panel follows the file selected in the editor. */
    fun isAutoRepository(): Boolean = selectedRoot == null

    /**
     * Re-resolves the repository auto mode follows from the file selected in the editor, preferring the deepest
     * enclosing repository. A file outside every registered repository keeps the previous selection. No-op while
     * the user pinned a repository.
     */
    @RequiresEdt
    fun syncAutoRepository() {
        if (selectedRoot != null) return
        val file = FileEditorManager.getInstance(project).selectedFiles
            .firstOrNull()
            ?.takeIf { it.isInLocalFileSystem }
            ?.let { VfsUtilCore.virtualToIoFile(it).toPath() }
        autoRoot = resolveAutoRepository(repositoryChoices(), file, autoRoot)
    }

    /**
     * The repository the panel's actions run against: the pinned one while it still exists, otherwise the one
     * auto mode follows from the editor, falling back to the project's own repository (and to the first Git root
     * when the project itself is not a repository).
     */
    fun selectedRepository(): GitRepository? {
        val repositories = gitRepositories()
        if (repositories.isEmpty()) return null
        val choice = chooseRepository(buildChoices(repositories), effectiveRoot()) ?: return null
        return repositories.firstOrNull { it.root.toNioPath().normalize() == choice.root }
    }

    /**
     * Selects [root] for every panel action, or null to follow the file selected in the editor, and notifies the
     * other open panels.
     */
    @RequiresEdt
    fun selectRepository(root: Path?) {
        selectedRoot = root?.normalize()
        // Re-resolve the editor context on the next sync instead of reusing a root from before the pin.
        if (root == null) autoRoot = null
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
        val originalBranch = git.currentBranchName(repository)
            ?: return pruneFailure(GhStackBundle.message("prune.error.detached"))

        // Point the temporary branch at HEAD so switching to it is safe even with uncommitted changes.
        if (!git.createBranch(repository, temporaryBranch)) {
            return pruneFailure(GhStackBundle.message("prune.error.create.branch", temporaryBranch))
        }
        var result: GhResult? = null
        var cleanupError: String? = null
        try {
            result = if (git.checkoutBranch(repository, temporaryBranch)) {
                requireCli().unstackLocal()
            } else {
                pruneFailure(GhStackBundle.message("prune.error.checkout.branch", temporaryBranch))
            }
        } finally {
            if (!git.checkoutBranch(repository, originalBranch)) {
                cleanupError = GhStackBundle.message("prune.error.restore.branch", originalBranch)
            }
            if (!git.deleteBranch(repository, temporaryBranch)) {
                val deleteError = GhStackBundle.message("prune.error.delete.branch", temporaryBranch)
                cleanupError = cleanupError?.let { "$it\n$deleteError" } ?: deleteError
            }
        }
        cleanupError?.let { return pruneFailure(it) }
        return result ?: pruneFailure(GhStackBundle.message("prune.error.unknown"))
    }

    private fun pruneFailure(message: String): GhResult = GhResult(exitCode = 1, stdout = "", stderr = message)

    fun addBranch(name: String): GhResult = requireCli().addBranch(name)

    fun checkout(target: String): GhResult = requireCli().checkout(target)

    fun initStack(branchName: String): GhResult = requireCli().init(branchName)

    fun rebase(vararg flags: String): GhResult = requireCli().rebase(*flags)

    /**
     * Stashes local changes (including untracked files), runs the rebase, and reports the stash to restore
     * only if the rebase succeeded. After a failed rebase (e.g. a conflict) the stash is kept and the error mentions it.
     */
    fun rebaseWithStash(vararg flags: String): RebaseOutcome {
        val repository = selectedRepository() ?: error("This project is not under Git.")
        val message = GhStackBundle.message("stash.name")
        val headBefore = git.stashHead(repository)
        git.stashLocalChanges(repository, message)?.let { stashError ->
            return RebaseOutcome(GhResult(1, "", stashError))
        }
        val stash = git.stashHead(repository)?.takeIf { it != headBefore }

        val result = rebase(*flags)
        return when {
            stash == null -> RebaseOutcome(result)
            result.succeeded -> RebaseOutcome(result, StashRef(stash, message))
            else -> RebaseOutcome(
                result.copy(stderr = "${result.errorText()}\n\n${GhStackBundle.message("stash.kept.hint", message)}"),
            )
        }
    }

    fun rebaseContinue(): GhResult = requireCli().rebaseContinue()

    fun rebaseAbort(): GhResult = requireCli().rebaseAbort()

    private fun requireCli(): GhCli = cliFor(gitRoot() ?: error("This project is not under Git."))

    private fun cliFor(root: Path): GhCli {
        val console = GhStackConsoleService.getInstance(project)
        return GhCli(root, onCommandRun = console::record)
    }
}
