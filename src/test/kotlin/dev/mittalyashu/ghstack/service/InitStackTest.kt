package com.github.mittalyashu.ghstack.service

import com.github.mittalyashu.ghstack.model.TrackedStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitStackTest {
    private val stack = TrackedStack(trunk = "main", branches = listOf("feature-a", "feature-b"))

    @Test
    fun existingStackMatchesNonTrunkMember() {
        assertEquals(stack, existingStackForBranch(listOf(stack), "feature-a"))
    }

    @Test
    fun trunkCanBeSharedAcrossStacks() {
        assertNull(existingStackForBranch(listOf(stack), "main"))
    }

    @Test
    fun unrelatedBranchIsNotInAStack() {
        assertNull(existingStackForBranch(listOf(stack), "other"))
    }

    @Test
    fun emptyBranchIsNeverInAStack() {
        assertNull(existingStackForBranch(listOf(stack), ""))
    }

    @Test
    fun containingBranchMatchesTrunkAndLayers() {
        assertEquals(stack, stackContainingBranch(listOf(stack), "main"))
        assertEquals(stack, stackContainingBranch(listOf(stack), "feature-b"))
        assertNull(stackContainingBranch(listOf(stack), "other"))
        assertNull(stackContainingBranch(listOf(stack), ""))
    }

    @Test
    fun defaultBasePrefersRepositoryDefaultBranch() {
        assertEquals("develop", defaultBaseBranch(listOf("main", "develop"), "feature", "develop"))
    }

    @Test
    fun defaultBaseUsesRemoteOnlyDefaultBranch() {
        assertEquals("develop", defaultBaseBranch(listOf("main"), "feature", "develop"))
    }

    @Test
    fun defaultBaseFallsBackToMainThenMaster() {
        assertEquals("main", defaultBaseBranch(listOf("master", "main"), "feature", null))
        assertEquals("master", defaultBaseBranch(listOf("master", "release"), "feature", null))
    }

    @Test
    fun defaultBaseFallsBackToCurrentBranch() {
        assertEquals("feature", defaultBaseBranch(listOf("release", "feature"), "feature", null))
    }

    @Test
    fun defaultBaseFallsBackToFirstLocalBranch() {
        assertEquals("release", defaultBaseBranch(listOf("release", "feature"), "detached", null))
    }

    @Test
    fun defaultBaseIsNullWithoutBranches() {
        assertNull(defaultBaseBranch(emptyList(), "feature", null))
    }

    @Test
    fun defaultFirstBranchIsCurrentWhenNotTrunk() {
        assertEquals("feature", defaultFirstBranch("feature", "main"))
    }

    @Test
    fun defaultFirstBranchIsEmptyOnTrunk() {
        assertEquals("", defaultFirstBranch("main", "main"))
    }

    @Test
    fun defaultFirstBranchIsEmptyWithoutCurrentBranch() {
        assertEquals("", defaultFirstBranch(null, "main"))
    }
}
