package com.github.mittalyashu.ghstack.cli

import com.intellij.openapi.util.SystemInfo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Runs [GhCli.rebaseCancellable] against a real scripted `gh` so the process-tree kill is exercised end to end, not
 * just the polling loop that [GhCliCancellationTest] covers with a fake handler.
 *
 * The scripted `gh` needs a POSIX shell and the child-state assertion reads `/proc`, so only Linux runs these cases;
 * CI is Ubuntu.
 */
class GhCliProcessCancellationTest {
    private lateinit var workingDirectory: Path

    @Before
    fun createWorkingDirectory() {
        assumeTrue("The scripted gh needs a POSIX shell and /proc", SystemInfo.isLinux)
        workingDirectory = Files.createTempDirectory("gh-stack-cancel")
    }

    @After
    fun deleteWorkingDirectory() {
        if (::workingDirectory.isInitialized) {
            workingDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cancelling kills the gh process and its git child`() {
        val childPidFile = workingDirectory.resolve("child.pid")
        // `sleep 60` keeps gh alive like a cascading rebase; the background sleep stands in for the git child it
        // spawns. The child only exits when it is killed, so its state proves the tree kill reached it.
        val gh = script(
            """
            #!/bin/sh
            sleep 60 &
            echo ${'$'}! > "$childPidFile"
            sleep 60
            """.trimIndent(),
        )
        val started = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val canceller = thread {
            started.set(awaitFile(childPidFile, timeoutMs = 5_000))
            cancelled.set(true)
        }

        val startedAt = System.nanoTime()
        val result = GhCli(workingDirectory, configuredPath = { gh.toString() })
            .rebaseCancellable({ cancelled.get() })
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        canceller.join(5_000)

        assertTrue("the scripted gh never started", started.get())
        assertTrue("a killed run is reported as cancelled", result.cancelled)
        assertTrue("cancellation must return promptly (took ${elapsedMs}ms)", elapsedMs < 5_000)
        val child = Files.readString(childPidFile).trim().toLong()
        assertTrue(
            "the git child must die with its gh parent",
            awaitStopped(child, timeoutMs = 3_000),
        )
    }

    @Test
    fun `a run that completes is reported as successful`() {
        val gh = script("#!/bin/sh\necho rebased\nexit 0")

        val result = GhCli(workingDirectory, configuredPath = { gh.toString() }).rebaseCancellable({ false })

        assertTrue(result.succeeded)
        assertFalse(result.cancelled)
        assertTrue(result.stdout.contains("rebased"))
    }

    private fun script(body: String): Path {
        val file = workingDirectory.resolve("gh")
        Files.writeString(file, "$body\n")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
        return file
    }

    private fun awaitFile(file: Path, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!Files.isRegularFile(file) && System.nanoTime() < deadline) {
            Thread.sleep(25)
        }
        return Files.isRegularFile(file)
    }

    /** True once [pid] is gone or a zombie (killed but not yet reaped), i.e. no longer running. */
    private fun awaitStopped(pid: Long, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (!isRunning(pid)) return true
            Thread.sleep(25)
        }
        return !isRunning(pid)
    }

    private fun isRunning(pid: Long): Boolean {
        val stat = Path.of("/proc/$pid/stat")
        if (!Files.exists(stat)) return false
        val state = Files.readString(stat).substringAfterLast(") ").firstOrNull()
        return state != 'Z' && state != 'X'
    }
}
