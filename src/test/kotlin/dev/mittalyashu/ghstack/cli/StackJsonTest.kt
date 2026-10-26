package dev.mittalyashu.ghstack.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackJsonTest {
    @Test
    fun parsesViewJson() {
        val json = """
            {
              "trunk": "main",
              "currentBranch": "api",
              "branches": [
                {
                  "name": "auth",
                  "head": "aaa",
                  "base": "bbb",
                  "isCurrent": false,
                  "isMerged": false,
                  "isQueued": false,
                  "needsRebase": true,
                  "pr": { "number": 12, "url": "https://example.com/12", "state": "OPEN" }
                },
                {
                  "name": "api",
                  "isCurrent": true,
                  "pr": { "number": 13, "state": "OPEN" }
                }
              ]
            }
        """.trimIndent()

        val view = StackJson.parseView(json)
        assertEquals("main", view.trunk)
        assertEquals("api", view.currentBranch)
        assertEquals(2, view.branches.size)
        assertEquals("auth", view.branches[0].name)
        assertTrue(view.branches[0].needsRebase)
        assertEquals(12, view.branches[0].pr?.number)
        assertTrue(view.branches[1].isCurrent)
    }

    @Test
    fun parsesTrackedStacksFile() {
        val json = """
            {
              "schemaVersion": 1,
              "repository": "github.com:acme/app",
              "stacks": [
                {
                  "id": "S_1",
                  "number": 7,
                  "trunk": { "branch": "main", "head": "abc" },
                  "branches": [
                    { "branch": "auth" },
                    { "branch": "api" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val tracked = StackJson.parseTrackedFile(json)
        assertEquals(1, tracked.stacks.size)
        assertEquals(7, tracked.stacks[0].number)
        assertEquals("main", tracked.stacks[0].trunk)
        assertEquals(listOf("auth", "api"), tracked.stacks[0].branches)
        assertEquals("7", tracked.stacks[0].checkoutTarget())
    }

    @Test
    fun viewWithoutBranchesOrOptionalFields() {
        val json = """{ "trunk": "main" }"""

        val view = StackJson.parseView(json)
        assertEquals("main", view.trunk)
        assertEquals("", view.currentBranch)
        assertTrue(view.branches.isEmpty())
    }

    @Test
    fun viewBranchWithoutPrOrWithNullPrHasNoPr() {
        val json = """
            {
              "branches": [
                { "name": "a" },
                { "name": "b", "pr": null },
                { "name": "c", "pullRequest": { "number": 9 } }
              ]
            }
        """.trimIndent()

        val view = StackJson.parseView(json)
        assertNull(view.branches[0].pr)
        assertNull(view.branches[1].pr)
        assertEquals(9, view.branches[2].pr?.number)
    }

    @Test(expected = IllegalStateException::class)
    fun viewRootMustBeAnObject() {
        StackJson.parseView("[]")
    }

    @Test
    fun trackedFileWithoutStacksYieldsEmptyList() {
        val tracked = StackJson.parseTrackedFile("""{ "repository": "github.com:acme/app" }""")
        assertEquals("github.com:acme/app", tracked.repository)
        assertTrue(tracked.stacks.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun trackedFileRootMustBeAnObject() {
        StackJson.parseTrackedFile("[]")
    }

    @Test
    fun trackedStackDropsNonPositiveNumberAndBlankMembers() {
        val json = """
            {
              "stacks": [
                { "number": 0, "trunk": { "branch": "main" }, "branches": ["a", null] },
                { "trunk": "trunk" },
                { "branches": ["x"] }
              ]
            }
        """.trimIndent()

        val tracked = StackJson.parseTrackedFile(json)
        assertEquals(2, tracked.stacks.size)
        assertTrue(tracked.stacks[0].isLocal)
        assertEquals(listOf("a"), tracked.stacks[0].branches)
        assertEquals("trunk", tracked.stacks[1].trunk)
    }
}
