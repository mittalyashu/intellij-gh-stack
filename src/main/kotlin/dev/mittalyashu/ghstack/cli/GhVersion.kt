package com.github.mittalyashu.ghstack.cli

/** A `gh stack` extension as reported by `gh extension list`. [version] is null when gh reports none. */
internal data class GhStackExtension(val installed: Boolean, val version: String?)

private val GH_VERSION_PATTERN = Regex("""gh version (\S+)""")
private const val GH_STACK_REPO = "github/gh-stack"
private const val GH_STACK_NAME = "stack"

/**
 * Extracts the version from `gh --version` output, e.g. `gh version 2.62.0 (2024-11-14)`.
 * Returns null when the output does not carry a version (e.g. an unrelated error message).
 */
internal fun parseGhVersion(versionOutput: String): String? =
    GH_VERSION_PATTERN.find(versionOutput)?.groupValues?.get(1)

/**
 * Extracts the `gh stack` extension from `gh extension list` output. The table's columns are
 * `NAME REPO VERSION`, where the extension's name is rendered as `gh stack`. gh reports no
 * version for a local install, and [version] is then null.
 */
internal fun parseGhStackExtension(listOutput: String): GhStackExtension {
    val columns = listOutput.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("NAME") }
        .map { it.split(Regex("\\s+")) }
        .firstOrNull { it.size >= 2 && it[0] == "gh" && it[1] == GH_STACK_NAME }
        ?: return GhStackExtension(installed = false, version = null)
    val version = columns.getOrNull(3)?.takeIf { columns.getOrNull(2) == GH_STACK_REPO && it.isNotBlank() }
    return GhStackExtension(installed = true, version = version)
}
