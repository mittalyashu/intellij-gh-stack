package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import com.github.mittalyashu.ghstack.model.StackView
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

    @Test
    fun stackRowsOrdersTopBranchAtTopAndTrunkAtBottom() {
        val view = StackView(
            trunk = "main",
            currentBranch = "feature-b",
            branches = listOf(
                StackLayer(name = "feature-a"),
                StackLayer(name = "feature-b", isCurrent = true),
                StackLayer(name = "feature-c"),
            ),
        )

        val rows = stackRows(view)

        assertEquals(
            listOf(
                StackRow.Layer(StackLayer(name = "feature-c")),
                StackRow.Layer(StackLayer(name = "feature-b", isCurrent = true)),
                StackRow.Layer(StackLayer(name = "feature-a")),
                StackRow.Trunk("main"),
            ),
            rows,
        )
    }

    @Test
    fun stackRowsWithEmptyBranchesContainsOnlyTrunk() {
        val view = StackView(
            trunk = "main",
            currentBranch = "main",
            branches = emptyList(),
        )

        val rows = stackRows(view)

        assertEquals(listOf(StackRow.Trunk("main")), rows)
    }
}
