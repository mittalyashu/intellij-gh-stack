package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhDiagnosticsTest {
    @Test
    fun missingGhSkipsExtensionProbeAndShowsHint() {
        var probed = false
        val versions = detectGhVersions(
            version = { GhResult(exitCode = 127, stdout = "", stderr = "Could not start gh") },
            extensionList = { probed = true; GhResult(0, "", "") },
        )
        assertFalse(probed)
        assertEquals(GhStackBundle.message("status.gh.missing"), describeGhVersions(versions))
    }

    @Test
    fun reportsBothVersions() {
        val versions = detectGhVersions(
            version = { GhResult(0, "gh version 2.62.0 (2024-11-14)\n", "") },
            extensionList = { GhResult(0, "NAME      REPO              VERSION\ngh stack  github/gh-stack   v0.3.0\n", "") },
        )
        assertEquals("2.62.0", versions.gh)
        assertEquals("v0.3.0", versions.extension)
        val ghPart = GhStackBundle.message("settings.versions.gh", "2.62.0")
        val extensionPart = GhStackBundle.message("settings.versions.extension", "v0.3.0")
        assertEquals(
            GhStackBundle.message("settings.versions.summary", ghPart, extensionPart),
            describeGhVersions(versions),
        )
    }

    @Test
    fun reportsUnknownExtensionVersion() {
        val versions = detectGhVersions(
            version = { GhResult(0, "gh version 2.62.0 (2024-11-14)\n", "") },
            extensionList = { GhResult(0, "gh stack\t\t\n", "") },
        )
        assertTrue(versions.extensionInstalled)
        assertNull(versions.extension)
        val ghPart = GhStackBundle.message("settings.versions.gh", "2.62.0")
        assertEquals(
            GhStackBundle.message(
                "settings.versions.summary",
                ghPart,
                GhStackBundle.message("settings.versions.extension.unknown"),
            ),
            describeGhVersions(versions),
        )
    }

    @Test
    fun reportsExtensionMissing() {
        val versions = detectGhVersions(
            version = { GhResult(0, "gh version 2.62.0 (2024-11-14)\n", "") },
            extensionList = { GhResult(0, "NAME  REPO  VERSION\n", "") },
        )
        assertFalse(versions.extensionInstalled)
        val ghPart = GhStackBundle.message("settings.versions.gh", "2.62.0")
        assertEquals(
            GhStackBundle.message(
                "settings.versions.summary",
                ghPart,
                GhStackBundle.message("settings.versions.extension.missing"),
            ),
            describeGhVersions(versions),
        )
    }

    @Test
    fun unstartableGhDuringExtensionListShowsHint() {
        val versions = detectGhVersions(
            version = { GhResult(0, "gh version 2.62.0 (2024-11-14)\n", "") },
            extensionList = { GhResult(127, "", "Could not start gh") },
        )
        assertEquals(GhStackBundle.message("status.gh.missing"), describeGhVersions(versions))
    }
}
