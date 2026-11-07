package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StackRowTest {
    @Test
    fun trunkRowCopiesItsBranchName() {
        assertEquals("main", branchNameToCopy(StackRow.Trunk("main")))
    }

    @Test
    fun layerRowCopiesItsBranchName() {
        assertEquals("feature-b", branchNameToCopy(StackRow.Layer(StackLayer(name = "feature-b"))))
    }

    @Test
    fun nothingSelectedHasNoBranchName() {
        assertNull(branchNameToCopy(null))
    }
}
