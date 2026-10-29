package com.github.mittalyashu.ghstack.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhVersionTest {
    @Test
    fun parsesGhVersion() {
        val output = "gh version 2.62.0 (2024-11-14)\nhttps://github.com/cli/cli/releases/tag/v2.62.0\n"
        assertEquals("2.62.0", parseGhVersion(output))
    }

    @Test
    fun parsesPrereleaseGhVersion() {
        assertEquals("2.63.0-beta.1", parseGhVersion("gh version 2.63.0-beta.1 (2025-01-01)"))
    }

    @Test
    fun returnsNullWithoutAVersion() {
        assertNull(parseGhVersion("gh: command not found"))
    }

    @Test
    fun parsesBinaryExtensionVersion() {
        // gh writes tab-separated columns when stdout is not a TTY (the plugin always pipes it).
        val output = "NAME\tREPO\tVERSION\ngh stack\tgithub/gh-stack\tv0.3.0\n"
        assertEquals(GhStackExtension(installed = true, version = "v0.3.0"), parseGhStackExtension(output))
    }

    @Test
    fun parsesPaddedColumnOutput() {
        val output = "NAME      REPO              VERSION\ngh stack  github/gh-stack   v0.3.0\n"
        assertEquals(GhStackExtension(installed = true, version = "v0.3.0"), parseGhStackExtension(output))
    }

    @Test
    fun reportsLocalExtensionWithoutVersion() {
        // A local install leaves the REPO and VERSION columns empty in gh's TSV output.
        assertEquals(GhStackExtension(installed = true, version = null), parseGhStackExtension("gh stack\t\t\n"))
    }

    @Test
    fun reportsGitExtensionCommit() {
        val output = "gh stack  github/gh-stack  a1b2c3d4\n"
        assertEquals(GhStackExtension(installed = true, version = "a1b2c3d4"), parseGhStackExtension(output))
    }

    @Test
    fun reportsNotInstalledWhenAbsent() {
        val output = "NAME       REPO              VERSION\ngh graphql  github/gh-graphql  v1.0.0\n"
        val extension = parseGhStackExtension(output)
        assertFalse(extension.installed)
        assertNull(extension.version)
    }

    @Test
    fun doesNotMatchRepoMentionedElsewhere() {
        assertFalse(parseGhStackExtension("see github/gh-stack for details\n").installed)
    }

    @Test
    fun matchesGhStackAmongOtherExtensions() {
        val output = "NAME         REPO                VERSION\n" +
            "gh graphql   github/gh-graphql   v1.0.0\n" +
            "gh stack     github/gh-stack     v0.3.0\n"
        assertTrue(parseGhStackExtension(output).installed)
        assertEquals("v0.3.0", parseGhStackExtension(output).version)
    }
}
