package com.github.mittalyashu.ghstack.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.settings.GhStackSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** One completed `gh` invocation, reported so it can be shown in the GH Stack console. */
data class GhCommandRun(
    val commandLine: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val succeeded: Boolean get() = exitCode == 0
}

class GhCli(
    private val workingDirectory: Path,
    private val configuredPath: () -> String = { GhStackSettings.getInstance().ghExecutablePath },
    private val onCommandRun: (GhCommandRun) -> Unit = {},
) {
    private val log = Logger.getInstance(GhCli::class.java)

    fun viewJson(): GhResult = run("stack", "view", "--json")

    fun addBranch(branchName: String): GhResult = run(MUTATION_TIMEOUT_MS, "stack", "add", branchName)

    fun checkout(target: String): GhResult = run(MUTATION_TIMEOUT_MS, "stack", "checkout", target)

    fun init(branchName: String): GhResult = init(listOf(branchName))

    /** Initializes a stack from [branches] (bottom to top), optionally on [base] instead of the default trunk. */
    fun init(branches: List<String>, base: String? = null): GhResult =
        run(MUTATION_TIMEOUT_MS, *initStackArgs(branches, base).toTypedArray())

    fun rebase(vararg flags: String): GhResult {
        val timeoutMs = if (flags.contains("--abort")) DEFAULT_TIMEOUT_MS else REBASE_TIMEOUT_MS
        return run(timeoutMs, "stack", "rebase", *flags)
    }

    /**
     * Runs `gh stack rebase` while polling [isCancelled]. Cancelling stops the whole process tree and returns a
     * [GhResult.cancelled] result instead of throwing, so the caller can still abort the cascade and restore its stash.
     */
    fun rebaseCancellable(isCancelled: () -> Boolean, vararg flags: String): GhResult {
        val timeoutMs = if (flags.contains("--abort")) DEFAULT_TIMEOUT_MS else REBASE_TIMEOUT_MS
        return runCancellable(timeoutMs, isCancelled, "stack", "rebase", *flags)
    }

    fun rebaseContinue(): GhResult = rebase("--continue")

    fun rebaseAbort(): GhResult = rebase("--abort")

    fun version(): GhResult = run("--version")

    fun extensionList(): GhResult = run("extension", "list")

    /** Removes a stack from local tracking only. A stack number targets a specific stack; without one the active stack is used. */
    fun unstackLocal(stackNumber: Int? = null): GhResult {
        val args = buildList {
            add("stack")
            add("unstack")
            stackNumber?.let { add(it.toString()) }
            add("--local")
        }
        return run(*args.toTypedArray())
    }

    private fun run(vararg args: String): GhResult = run(DEFAULT_TIMEOUT_MS, *args)

    private fun run(timeoutMs: Int, vararg args: String): GhResult {
        val commandLine = when (val prepared = prepare(args)) {
            is PreparedCommand.Missing -> {
                report("gh ${args.joinToString(" ")}", prepared.result)
                return prepared.result
            }

            is PreparedCommand.Ready -> prepared.commandLine
        }

        log.debug("Running: ${commandLine.commandLineString} in $workingDirectory")
        val output: ProcessOutput = try {
            CapturingProcessHandler(commandLine).runProcess(timeoutMs)
        } catch (e: ExecutionException) {
            log.warn("Could not start ${commandLine.exePath}", e)
            val result = GhResult(
                exitCode = 127,
                stdout = "",
                stderr = "Could not start ${commandLine.exePath}: ${e.message ?: e.javaClass.simpleName}",
            )
            report(commandLine.commandLineString, result)
            return result
        }
        if (output.isTimeout) {
            log.warn("gh ${args.joinToString(" ")} timed out after ${timeoutMs / 1000}s")
        }
        val result = toGhResult(output, timeoutMs)
        report(commandLine.commandLineString, result)
        return result
    }

    private fun runCancellable(timeoutMs: Int, isCancelled: () -> Boolean, vararg args: String): GhResult {
        val commandLine = when (val prepared = prepare(args)) {
            is PreparedCommand.Missing -> {
                report("gh ${args.joinToString(" ")}", prepared.result)
                return prepared.result
            }

            is PreparedCommand.Ready -> prepared.commandLine
        }

        log.debug("Running (cancellable): ${commandLine.commandLineString} in $workingDirectory")
        val handler = try {
            CapturingProcessHandler(commandLine).apply {
                // gh stack rebase spawns git children; kill the whole tree so a follow-up `--abort` cannot race a
                // git rebase that is still running after its parent gh process is gone.
                setShouldDestroyProcessRecursively(true)
            }
        } catch (e: ExecutionException) {
            log.warn("Could not start ${commandLine.exePath}", e)
            val result = GhResult(
                exitCode = 127,
                stdout = "",
                stderr = "Could not start ${commandLine.exePath}: ${e.message ?: e.javaClass.simpleName}",
            )
            report(commandLine.commandLineString, result)
            return result
        }

        val output = ProcessOutput()
        handler.addProcessListener(CapturingProcessAdapter(output))
        handler.startNotify()
        when (awaitProcess(handler, timeoutMs, isCancelled)) {
            ProcessStop.FINISHED -> Unit
            ProcessStop.TIMED_OUT -> {
                log.warn("gh ${args.joinToString(" ")} timed out after ${timeoutMs / 1000}s")
                output.setTimeout()
            }

            ProcessStop.CANCELLED -> {
                log.info("gh ${args.joinToString(" ")} was cancelled")
                output.setCancelled()
            }
        }
        val result = toGhResult(output, timeoutMs)
        report(commandLine.commandLineString, result)
        return result
    }

    private fun prepare(args: Array<out String>): PreparedCommand {
        val invocation = GhInvocation.resolve(configuredPath()) { name ->
            PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(name)?.absolutePath
        }
        val command = when (invocation) {
            is GhInvocation.Missing -> return PreparedCommand.Missing(
                GhResult(exitCode = 127, stdout = "", stderr = invocation.message)
            )

            is GhInvocation.Ready -> invocation.command
        }
        val commandLine = GeneralCommandLine(command + args.toList())
            .withWorkDirectory(workingDirectory.toFile())
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment(
                mapOf(
                    // Point go-gh (used by gh stack) at the gh binary we invoke, so its `gh auth token`
                    // fallback works when gh is not on the IDE's PATH. cli/cli exports this to extensions.
                    "GH_PATH" to command.first(),
                    "GH_PROMPT_DISABLED" to "1",
                    "GH_PAGER" to "cat",
                    "GIT_PAGER" to "cat",
                    "PAGER" to "cat",
                    "CI" to "1",
                    "NO_COLOR" to "1",
                )
            )
        return PreparedCommand.Ready(commandLine)
    }

    private fun report(commandLine: String, result: GhResult) {
        runCatching {
            onCommandRun(
                GhCommandRun(
                    commandLine = commandLine,
                    exitCode = result.exitCode,
                    stdout = result.stdout,
                    stderr = result.stderr,
                )
            )
        }.onFailure { log.warn("Could not report the gh command run", it) }
    }

    private sealed interface PreparedCommand {
        class Ready(val commandLine: GeneralCommandLine) : PreparedCommand
        class Missing(val result: GhResult) : PreparedCommand
    }

    private companion object {
        private const val DEFAULT_TIMEOUT_MS = 60_000

        /** `add`, `checkout` and `init` can fetch, pull and commit, which regularly takes longer than a minute. */
        private const val MUTATION_TIMEOUT_MS = 5 * 60_000
        private const val REBASE_TIMEOUT_MS = 10 * 60_000
    }
}

/** The `gh` arguments that initialize a stack from [branches] (bottom to top) on [base]. */
internal fun initStackArgs(branches: List<String>, base: String?): List<String> = buildList {
    add("stack")
    add("init")
    base?.takeIf { it.isNotBlank() }?.let {
        add("--base")
        add(it)
    }
    addAll(branches)
}

/** How a cancellable process run ended. */
internal enum class ProcessStop {
    /** The process terminated on its own. */
    FINISHED,

    /** The caller asked to cancel while the process was still running. */
    CANCELLED,

    /** The process was still running when its timeout elapsed. */
    TIMED_OUT,
}

/**
 * Waits for [handler] to terminate, polling [isCancelled] instead of the progress indicator so a cancellation
 * returns normally, letting the caller run its abort/stash cleanup. A process that has already terminated wins the
 * race: it is reported as [ProcessStop.FINISHED] and never destroyed, so a completed rebase is not rolled back.
 */
internal fun awaitProcess(handler: ProcessHandler, timeoutMs: Int, isCancelled: () -> Boolean): ProcessStop {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!handler.waitFor(PROCESS_POLL_MS)) {
        if (handler.isProcessTerminated) {
            // The process finished between two polls; wait for its listeners so the captured output is settled.
            handler.waitFor(PROCESS_KILL_WAIT_MS)
            return ProcessStop.FINISHED
        }
        val stop = when {
            isCancelled() -> ProcessStop.CANCELLED
            System.currentTimeMillis() >= deadline -> ProcessStop.TIMED_OUT
            else -> null
        } ?: continue
        handler.destroyProcess()
        if (!handler.waitFor(PROCESS_KILL_WAIT_MS)) {
            processLog.warn("The gh process did not terminate within ${PROCESS_KILL_WAIT_MS}ms after being destroyed")
        }
        // A successful run can finish between the poll and the kill. Its real result must win, so a rebase that
        // already completed is reported as finished and never rolled back by the caller's abort cleanup.
        return if (handler.exitCode == 0) ProcessStop.FINISHED else stop
    }
    return ProcessStop.FINISHED
}

private val processLog = Logger.getInstance(GhCli::class.java)

private const val PROCESS_POLL_MS = 100L
private const val PROCESS_KILL_WAIT_MS = 5_000L

/**
 * Maps a finished process onto [GhResult]. `runProcess(timeout)` kills the process on timeout and only reports
 * that through [ProcessOutput.isTimeout], so without this a timeout looks like an arbitrary exit code with no output.
 */
internal fun toGhResult(output: ProcessOutput, timeoutMs: Int): GhResult {
    if (!output.isTimeout) {
        return GhResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
            cancelled = output.isCancelled,
        )
    }
    val message = GhStackBundle.message("error.timed.out", timeoutMs / 1000)
    val captured = output.stderr.trim()
    return GhResult(
        exitCode = GhExitCodes.TIMED_OUT,
        stdout = output.stdout,
        stderr = if (captured.isEmpty()) message else "$message\n\n$captured",
        timeoutMs = timeoutMs,
    )
}
