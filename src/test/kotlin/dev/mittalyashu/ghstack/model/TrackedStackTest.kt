package com.github.mittalyashu.ghstack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedStackTest {
    @Test
    fun stackWithoutNumberIsLocal() {
        val stack = TrackedStack(trunk = "main", branches = listOf("auth"))
        assertTrue(stack.isLocal)
        assertEquals("Local stack  (main)  auth", stack.displayLabel())
    }

    @Test
    fun stackWithPositiveNumberIsRemote() {
        val stack = TrackedStack(number = 7, trunk = "main", branches = listOf("auth", "api"))
        assertFalse(stack.isLocal)
        assertEquals("Stack #7  (main)  auth → api", stack.displayLabel())
    }

    @Test
    fun nonPositiveNumberIsTreatedAsLocal() {
        val stack = TrackedStack(number = 0, trunk = "main", branches = emptyList())
        assertTrue(stack.isLocal)
        assertEquals("Local stack  (main)  (empty)", stack.displayLabel())
    }
}
