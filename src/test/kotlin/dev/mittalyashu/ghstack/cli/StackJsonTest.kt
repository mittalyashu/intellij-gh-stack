package dev.mittalyashu.ghstack.cli

import org.junit.Assert.assertEquals
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
}
