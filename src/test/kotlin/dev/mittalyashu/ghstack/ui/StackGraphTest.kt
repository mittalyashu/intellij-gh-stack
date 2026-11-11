package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import com.github.mittalyashu.ghstack.model.StackView
import org.junit.Assert.assertEquals
import org.junit.Test

class StackGraphTest {
    @Test
    fun trunkRowEndsTheLane() {
        val graph = stackGraphRowAt(index = 3, rowCount = 4, row = StackRow.Trunk("main"))

        assertEquals(StackGraphRow(StackGraphNode.TRUNK, lineAbove = true, lineBelow = false), graph)
    }

    @Test
    fun topLayerStartsTheLane() {
        val graph = stackGraphRowAt(index = 0, rowCount = 4, row = StackRow.Layer(StackLayer(name = "feature")))

        assertEquals(StackGraphRow(StackGraphNode.LAYER, lineAbove = false, lineBelow = true), graph)
    }

    @Test
    fun middleLayerConnectsBothNeighbours() {
        val graph = stackGraphRowAt(index = 1, rowCount = 3, row = StackRow.Layer(StackLayer(name = "feature")))

        assertEquals(StackGraphRow(StackGraphNode.LAYER, lineAbove = true, lineBelow = true), graph)
    }

    @Test
    fun singleTrunkRowHasNoConnections() {
        val graph = stackGraphRowAt(index = 0, rowCount = 1, row = StackRow.Trunk("main"))

        assertEquals(StackGraphRow(StackGraphNode.TRUNK, lineAbove = false, lineBelow = false), graph)
    }

    @Test
    fun currentLayerKeepsTheAccentNodeOverOtherStatuses() {
        val layer = StackLayer(name = "feature", isCurrent = true, needsRebase = true, isMerged = true, isQueued = true)

        assertEquals(StackGraphNode.CURRENT, stackGraphRowAt(0, 1, StackRow.Layer(layer)).node)
    }

    @Test
    fun needsRebaseOutranksMergedAndQueued() {
        val layer = StackLayer(name = "feature", needsRebase = true, isMerged = true, isQueued = true)

        assertEquals(StackGraphNode.NEEDS_REBASE, stackGraphRowAt(0, 1, StackRow.Layer(layer)).node)
    }

    @Test
    fun mergedOutranksQueued() {
        val layer = StackLayer(name = "feature", isMerged = true, isQueued = true)

        assertEquals(StackGraphNode.MERGED, stackGraphRowAt(0, 1, StackRow.Layer(layer)).node)
    }

    @Test
    fun queuedLayerHasItsOwnNode() {
        val layer = StackLayer(name = "feature", isQueued = true)

        assertEquals(StackGraphNode.QUEUED, stackGraphRowAt(0, 1, StackRow.Layer(layer)).node)
    }

    @Test
    fun graphFollowsTheTopToBottomRowOrder() {
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
        val graph = rows.mapIndexed { index, row -> stackGraphRowAt(index, rows.size, row) }

        assertEquals(
            listOf(
                StackGraphRow(StackGraphNode.LAYER, lineAbove = false, lineBelow = true),
                StackGraphRow(StackGraphNode.CURRENT, lineAbove = true, lineBelow = true),
                StackGraphRow(StackGraphNode.LAYER, lineAbove = true, lineBelow = true),
                StackGraphRow(StackGraphNode.TRUNK, lineAbove = true, lineBelow = false),
            ),
            graph,
        )
    }
}
