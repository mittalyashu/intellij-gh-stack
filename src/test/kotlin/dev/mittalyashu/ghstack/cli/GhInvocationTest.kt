package dev.mittalyashu.ghstack.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhInvocationTest {
    @Test
    fun usesConfiguredPathWhenPresent() {
        val invocation = GhInvocation.resolve("/opt/homebrew/bin/gh") {
            error("PATH should not be searched when a custom path is configured")
        }
        assertEquals(GhInvocation.Ready(listOf("/opt/homebrew/bin/gh")), invocation)
    }

    @Test
    fun trimsConfiguredPath() {
        val invocation = GhInvocation.resolve("  /custom/gh  ") { null }
        assertEquals(GhInvocation.Ready(listOf("/custom/gh")), invocation)
    }

    @Test
    fun fallsBackToPkgx() {
        val invocation = GhInvocation.resolve("") { name ->
            if (name == "pkgx") "/usr/local/bin/pkgx" else null
        }
        assertEquals(GhInvocation.Ready(listOf("/usr/local/bin/pkgx", "gh")), invocation)
    }

    @Test
    fun fallsBackToGhOnPath() {
        val invocation = GhInvocation.resolve("") { name ->
            if (name == "gh") "/usr/bin/gh" else null
        }
        assertEquals(GhInvocation.Ready(listOf("/usr/bin/gh")), invocation)
    }

    @Test
    fun reportsMissingWhenNothingFound() {
        val invocation = GhInvocation.resolve("") { null }
        assertTrue(invocation is GhInvocation.Missing)
    }
}
