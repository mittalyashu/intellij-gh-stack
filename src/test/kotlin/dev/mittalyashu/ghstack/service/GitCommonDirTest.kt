package dev.mittalyashu.ghstack.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class GitCommonDirTest {
    private val root = Path.of("/repo")
    private val dirs = mutableSetOf<Path>()
    private val files = mutableMapOf<Path, String>()

    private fun commonDir() = resolveGitCommonDir(root, dirs::contains, files::get)

    @Test
    fun normalCloneUsesDotGitDirectory() {
        dirs.add(root.resolve(".git"))
        assertEquals(root.resolve(".git"), commonDir())
    }

    @Test
    fun commondirIsHonoredEvenWhenDotGitIsDirectory() {
        dirs.add(root.resolve(".git"))
        files[root.resolve(".git/commondir")] = "/shared/.git\n"
        assertEquals(Path.of("/shared/.git"), commonDir())
    }

    @Test
    fun linkedWorktreeFollowsGitdirAndCommondir() {
        files[root.resolve(".git")] = "gitdir: /main/.git/worktrees/wt\n"
        files[Path.of("/main/.git/worktrees/wt/commondir")] = "../..\n"
        assertEquals(Path.of("/main/.git"), commonDir())
    }

    @Test
    fun relativeGitdirIsResolvedAgainstRoot() {
        files[root.resolve(".git")] = "gitdir: ../main/.git/worktrees/wt"
        files[Path.of("/main/.git/worktrees/wt/commondir")] = "../.."
        assertEquals(Path.of("/main/.git"), commonDir())
    }

    @Test
    fun separateGitDirWithoutCommondirIsTheCommonDir() {
        files[root.resolve(".git")] = "gitdir: /elsewhere/app.git"
        assertEquals(Path.of("/elsewhere/app.git"), commonDir())
    }

    @Test
    fun missingDotGitFallsBackToDotGitPath() {
        assertEquals(root.resolve(".git"), commonDir())
    }

    @Test
    fun stateFileLivesInCommonDir() {
        files[root.resolve(".git")] = "gitdir: /main/.git/worktrees/wt"
        files[Path.of("/main/.git/worktrees/wt/commondir")] = "../.."
        assertEquals(
            Path.of("/main/.git/gh-stack"),
            resolveGhStackStateFile(root, dirs::contains, files::get),
        )
    }
}
