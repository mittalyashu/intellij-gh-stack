package com.github.mittalyashu.ghstack.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
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

    fun init(branchName: String): GhResult = run(MUTATION_TIMEOUT_MS, "stack", "init", branchName)

    fun rebase(vararg flags: String): GhResult {
        val timeoutMs = if (flags.contains("--abort")) DEFAULT_TIMEOUT_MS else REBASE_TIMEOUT_MS
        return run(timeoutMs, "stack", "rebase", *flags)
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
        val invocation = GhInvocation.resolve(configuredPath()) { name ->
            PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(name)?.absolutePath
        }
        val command = when (invocation) {
            is GhInvocation.Missing -> {
                val result = GhResult(exitCode = 127, stdout = "", stderr = invocation.message)
                report("gh ${args.joinToString(" ")}", result)
                return result
            }

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

        log.debug("Running: ${commandLine.commandLineString} in $workingDirectory")
        val output: ProcessOutput = try {
            CapturingProcessHandler(commandLine).runProcess(timeoutMs)
        } catch (e: ExecutionException) {
            log.warn("Could not start ${command.first()}", e)
            val result = GhResult(
                exitCode = 127,
                stdout = "",
                stderr = "Could not start ${command.first()}: ${e.message ?: e.javaClass.simpleName}",
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

    private companion object {
        private const val DEFAULT_TIMEOUT_MS = 60_000

        /** `add`, `checkout` and `init` can fetch, pull and commit, which regularly takes longer than a minute. */
        private const val MUTATION_TIMEOUT_MS = 5 * 60_000
        private const val REBASE_TIMEOUT_MS = 10 * 60_000
    }
}

/**
 * Maps a finished process onto [GhResult]. `runProcess(timeout)` kills the process on timeout and only reports
 * that through [ProcessOutput.isTimeout], so without this a timeout looks like an arbitrary exit code with no output.
 */
internal fun toGhResult(output: ProcessOutput, timeoutMs: Int): GhResult {
    if (!output.isTimeout) {
        return GhResult(exitCode = output.exitCode, stdout = output.stdout, stderr = output.stderr)
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
