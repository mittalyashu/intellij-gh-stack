package com.github.mittalyashu.ghstack.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenameDecisionTest {
    @Test
    fun noOriginalBranchMeansNothingToRestore() {
        assertNull(branchToRestore(null, "auth", "auth-v2", renamed = true))
    }

    @Test
    fun renamedCurrentBranchIsRestoredUnderItsNewName() {
        assertEquals("auth-v2", branchToRestore("auth", "auth", "auth-v2", renamed = true))
    }

    @Test
    fun anotherCurrentBranchIsRestoredAsIs() {
        assertEquals("api", branchToRestore("api", "auth", "auth-v2", renamed = true))
    }

    @Test
    fun failedRenameKeepsTheOriginalBranchName() {
        assertEquals("auth", branchToRestore("auth", "auth", "auth-v2", renamed = false))
    }
}
