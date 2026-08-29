package dev.mittalyashu.ghstack.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import dev.mittalyashu.ghstack.cli.GhCli
import dev.mittalyashu.ghstack.cli.GhExitCodes
import dev.mittalyashu.ghstack.cli.GhResult
import dev.mittalyashu.ghstack.cli.StackJson
import dev.mittalyashu.ghstack.model.StackLoadResult
import dev.mittalyashu.ghstack.model.TrackedStacks
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.nio.file.Files
import java.nio.file.Path

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
            return StackLoadResult.Failed(humanizeFailure(result), result.exitCode)
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

    private fun humanizeFailure(result: GhResult): String {
        val text = result.errorText()
        val lower = text.lowercase()
        return when {
            lower.contains("no such file") || lower.contains("not found") ->
                "GitHub CLI (gh) was not found on PATH."
            lower.contains("unknown command") && lower.contains("stack") ->
                "The gh stack extension is not installed. Run: gh extension install github/gh-stack"
            else -> text
        }
    }
}
