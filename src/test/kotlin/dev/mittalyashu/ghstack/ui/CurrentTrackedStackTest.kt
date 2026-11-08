package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import com.github.mittalyashu.ghstack.model.StackView
import com.github.mittalyashu.ghstack.model.TrackedStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentTrackedStackTest {
    private val auth = TrackedStack(number = 7, trunk = "main", branches = listOf("auth", "api"))
    private val docs = TrackedStack(trunk = "main", branches = listOf("docs"))

    @Test
    fun matchesTheStackContainingTheCurrentBranch() {
        val view = StackView(
            trunk = "main",
            currentBranch = "api",
            branches = listOf(StackLayer("auth"), StackLayer("api")),
        )

        assertEquals(auth, currentTrackedStack(listOf(docs, auth), view))
    }

    @Test
    fun fallsBackToMatchingTrunkAndBranchesWhenTheCurrentBranchIsTheTrunk() {
        val view = StackView(
            trunk = "main",
            currentBranch = "main",
            branches = listOf(StackLayer("docs")),
        )

        assertEquals(docs, currentTrackedStack(listOf(auth, docs), view))
    }

    @Test
    fun returnsNullWhenTheCurrentStackIsNotTracked() {
        val view = StackView(
            trunk = "main",
            currentBranch = "feature",
            branches = listOf(StackLayer("feature")),
        )

        assertNull(currentTrackedStack(listOf(auth, docs), view))
    }

    @Test
    fun returnsNullWithoutACurrentStackView() {
        assertNull(currentTrackedStack(listOf(auth, docs), null))
    }
}
