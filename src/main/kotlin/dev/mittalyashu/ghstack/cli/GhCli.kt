package dev.mittalyashu.ghstack.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class GhCli(private val workingDirectory: Path) {
    private val log = Logger.getInstance(GhCli::class.java)

    fun viewJson(): GhResult = run("stack", "view", "--json")

    fun addBranch(branchName: String): GhResult = run("stack", "add", branchName)

    fun checkout(target: String): GhResult = run("stack", "checkout", target)

    fun init(branchName: String): GhResult = run("stack", "init", branchName)

    fun version(): GhResult = run("--version")

    private fun run(vararg args: String): GhResult {
        val pkgx = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("pkgx")
            ?: return GhResult(
                exitCode = 127,
                stdout = "",
                stderr = "pkgx was not found on PATH.",
            )

        val commandLine = GeneralCommandLine(listOf(pkgx.absolutePath, "gh") + args.toList())
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
        val output: ProcessOutput = CapturingProcessHandler(commandLine).runProcess(60_000)
        return GhResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
        )
    }
}
