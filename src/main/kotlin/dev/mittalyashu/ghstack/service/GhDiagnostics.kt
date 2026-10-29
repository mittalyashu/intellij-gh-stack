package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhResult
import com.github.mittalyashu.ghstack.cli.parseGhStackExtension
import com.github.mittalyashu.ghstack.cli.parseGhVersion

/**
 * The gh CLI and `gh stack` extension versions detected for the settings panel. [gh] is null when gh
 * is missing or its version could not be read; [failure] then carries the user-facing hint. [extension]
 * is null when gh reports no version (e.g. a local install), while [extensionInstalled] distinguishes
 * that from the extension not being installed at all.
 */
internal data class GhVersions(
    val gh: String?,
    val extension: String?,
    val extensionInstalled: Boolean,
    val failure: String?,
)

/**
 * Detects the installed gh and gh stack extension versions. `gh --version` runs first; when it fails
 * (exit code 127 for a missing/unstartable gh), the extension is not probed and the failure is routed
 * through [humanizeGhFailure] so a missing gh shows the settings hint.
 */
internal fun detectGhVersions(
    version: () -> GhResult,
    extensionList: () -> GhResult,
): GhVersions {
    val versionResult = version()
    if (!versionResult.succeeded) {
        return GhVersions(
            gh = null,
            extension = null,
            extensionInstalled = false,
            failure = humanizeGhFailure(versionResult),
        )
    }
    val extensionResult = extensionList()
    if (extensionResult.exitCode == 127) {
        return GhVersions(null, null, extensionInstalled = false, failure = humanizeGhFailure(extensionResult))
    }
    val extension = parseGhStackExtension(extensionResult.stdout)
    return GhVersions(
        gh = parseGhVersion(versionResult.stdout),
        extension = extension.version,
        extensionInstalled = extension.installed,
        failure = null,
    )
}

/** Renders [versions] as the single status line shown in the settings panel. */
internal fun describeGhVersions(versions: GhVersions): String {
    versions.failure?.let { return it }
    val ghPart = versions.gh
        ?.let { GhStackBundle.message("settings.versions.gh", it) }
        ?: GhStackBundle.message("settings.versions.gh.unknown")
    val extensionPart = when {
        !versions.extensionInstalled -> GhStackBundle.message("settings.versions.extension.missing")
        versions.extension == null -> GhStackBundle.message("settings.versions.extension.unknown")
        else -> GhStackBundle.message("settings.versions.extension", versions.extension)
    }
    return GhStackBundle.message("settings.versions.summary", ghPart, extensionPart)
}
