package dev.mittalyashu.ghstack.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import dev.mittalyashu.ghstack.settings.GhStackSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class GhCli(
    private val workingDirectory: Path,
    private val configuredPath: () -> String = { GhStackSettings.getInstance().ghExecutablePath },
) {
    private val log = Logger.getInstance(GhCli::class.java)

    fun viewJson(): GhResult = run("stack", "view", "--json")

    fun addBranch(branchName: String): GhResult = run("stack", "add", branchName)

    fun checkout(target: String): GhResult = run("stack", "checkout", target)

    fun init(branchName: String): GhResult = run("stack", "init", branchName)

    fun version(): GhResult = run("--version")

    private fun run(vararg args: String): GhResult {
        val invocation = GhInvocation.resolve(configuredPath()) { name ->
            PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(name)?.absolutePath
        }
        val command = when (invocation) {
            is GhInvocation.Missing -> return GhResult(
                exitCode = 127,
                stdout = "",
                stderr = invocation.message,
            )

            is GhInvocation.Ready -> invocation.command
        }

        val commandLine = GeneralCommandLine(command + args.toList())
            .withWorkDirectory(workingDirectory.toFile())
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment(
                mapOf(
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
            CapturingProcessHandler(commandLine).runProcess(60_000)
        } catch (e: ExecutionException) {
            log.warn("Could not start ${command.first()}", e)
            return GhResult(
                exitCode = 127,
                stdout = "",
                stderr = "Could not start ${command.first()}: ${e.message ?: e.javaClass.simpleName}",
            )
        }
        return GhResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
        )
    }
}
