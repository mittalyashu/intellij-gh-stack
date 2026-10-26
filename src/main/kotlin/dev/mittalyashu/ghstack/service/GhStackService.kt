package dev.mittalyashu.ghstack.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import dev.mittalyashu.ghstack.GhStackBundle
import dev.mittalyashu.ghstack.cli.GhCli
import dev.mittalyashu.ghstack.cli.GhExitCodes
import dev.mittalyashu.ghstack.cli.GhResult
import dev.mittalyashu.ghstack.cli.StackJson
import dev.mittalyashu.ghstack.model.StackLoadResult
import dev.mittalyashu.ghstack.model.TrackedStacks
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

@Service(Service.Level.PROJECT)
class GhStackService(private val project: Project) {
    fun gitRoot(): Path? = primaryRepository()?.root?.toNioPath()

    fun primaryRepository(): GitRepository? {
        return GitRepositoryManager.getInstance(project).repositories.firstOrNull()
    }

    fun loadCurrentStack(): StackLoadResult {
        val root = gitRoot() ?: return StackLoadResult.Failed("This project is not under Git.")
        val result = GhCli(root).viewJson()
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
        val file = root.resolve(".git").resolve("gh-stack")
        if (!Files.isRegularFile(file)) {
            return Result.success(TrackedStacks(stacks = emptyList()))
        }
        return runCatching { StackJson.parseTrackedFile(Files.readString(file)) }
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
        val repo = primaryRepository() ?: return false
        val handler = GitLineHandler(project, repo.root, GitCommand.STATUS)
        handler.addParameters("--porcelain", "--untracked-files=all")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return result.success() && result.output.any { it.isNotBlank() }
    }

    /**
     * Stashes local changes (including untracked files), runs the rebase, and reports the stash to restore
     * only if the rebase succeeded. After a failed rebase (e.g. a conflict) the stash is kept and the error mentions it.
     */
    fun rebaseWithStash(vararg flags: String): RebaseOutcome {
        val repo = primaryRepository() ?: error("This project is not under Git.")
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
        val repo = primaryRepository() ?: return false
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

    /** True while a rebase (interactive, merge or apply) is paused in the primary repository, including in linked worktrees. */
    fun isRebaseInProgress(): Boolean = primaryRepository()?.isRebaseInProgress == true

    fun refreshGit() {
        val repo = primaryRepository() ?: return
        VfsUtil.markDirtyAndRefresh(true, true, true, repo.root)
        repo.update()
        findGitDir(repo.root)?.let { gitDir ->
            VfsUtil.markDirtyAndRefresh(true, true, true, gitDir)
        }
    }

    private fun requireCli(): GhCli {
        val root = gitRoot() ?: error("This project is not under Git.")
        return GhCli(root)
    }

    private fun findGitDir(root: VirtualFile): VirtualFile? {
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toNioPath().resolve(".git"))
    }
}
