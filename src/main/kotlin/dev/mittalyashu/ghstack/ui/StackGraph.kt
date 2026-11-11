package com.github.mittalyashu.ghstack.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.github.mittalyashu.ghstack.GhStackBundle
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JList

/** The node drawn for a stack row in the graph gutter. */
internal enum class StackGraphNode { TRUNK, LAYER, CURRENT, MERGED, QUEUED, NEEDS_REBASE }

/** One row's slice of the stack graph: the line segments touching it and the node where they meet. */
internal data class StackGraphRow(
    val node: StackGraphNode,
    val lineAbove: Boolean,
    val lineBelow: Boolean,
)

/**
 * The graph decorations for [row] at [index] of [rowCount] rows.
 *
 * The stack is linear, so every row sits on the same lane: a row connects to the row above unless it is the top
 * layer, and to the row below unless it is the trunk.
 */
internal fun stackGraphRowAt(index: Int, rowCount: Int, row: StackRow): StackGraphRow = StackGraphRow(
    node = graphNode(row),
    lineAbove = index > 0,
    lineBelow = index < rowCount - 1,
)

private fun graphNode(row: StackRow): StackGraphNode = when (row) {
    is StackRow.Trunk -> StackGraphNode.TRUNK
    is StackRow.Layer -> when {
        // The checked-out branch is the row users look for first, so its node keeps the accent even when the layer
        // is also merged, queued, or needs a rebase.
        row.layer.isCurrent -> StackGraphNode.CURRENT
        row.layer.needsRebase -> StackGraphNode.NEEDS_REBASE
        row.layer.isMerged -> StackGraphNode.MERGED
        row.layer.isQueued -> StackGraphNode.QUEUED
        else -> StackGraphNode.LAYER
    }
}

private const val GUTTER_WIDTH = 22
private const val TEXT_GAP = 6

private val laneColor = JBColor.namedColor("Component.borderColor", JBColor(0xC7C7C7, 0x5C5C5C))
private val currentColor = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
private val errorColor = JBColor.namedColor("Label.errorForeground", JBColor.RED)

/**
 * The list renderer for the current stack: a commit-graph gutter with a node per branch followed by the branch name
 * and its PR / status badges. The graph replaces the per-row branch icon, like the IDE's Git log.
 */
internal class StackRowRenderer : ColoredListCellRenderer<StackRow>() {
    private var graphRow: StackGraphRow? = null
    private var graphHighlight: Color? = null
    private var graphBackground: Color? = null

    override fun getListCellRendererComponent(
        list: JList<out StackRow>,
        value: StackRow?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ): Component {
        graphRow = value?.let { stackGraphRowAt(index, list.model.size, it) }
        // Keep the node visible against the selection background; fall back to the list foreground for themes that
        // don't define a selection foreground.
        graphHighlight = if (selected) list.selectionForeground ?: list.foreground else null
        // A merged node is drawn as an open ring, which needs the row's background to mask the lane beneath it.
        graphBackground = if (selected) list.selectionBackground else list.background
        // Reserve the gutter in the cell padding so the graph never overlaps the text.
        ipad = JBUI.insets(6, GUTTER_WIDTH + TEXT_GAP, 6, 10)
        return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

    override fun customizeCellRenderer(
        list: JList<out StackRow>,
        value: StackRow?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        when (value) {
            is StackRow.Trunk -> {
                append(value.branchName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append("  ${GhStackBundle.message("branch.trunk")}", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            }

            is StackRow.Layer -> {
                val layer = value.layer
                val style = if (layer.isCurrent) {
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, currentColor)
                } else {
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(layer.name, style)
                layer.pr?.let { append("  #${it.number}", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                if (layer.isCurrent) append(
                    "  ${GhStackBundle.message("branch.current")}",
                    SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES,
                )
                if (layer.needsRebase) append(
                    "  ${GhStackBundle.message("branch.needs.rebase")}",
                    SimpleTextAttributes.ERROR_ATTRIBUTES,
                )
                if (layer.isMerged) append(
                    "  ${GhStackBundle.message("branch.merged")}",
                    SimpleTextAttributes.GRAYED_ATTRIBUTES,
                )
                if (layer.isQueued) append(
                    "  ${GhStackBundle.message("branch.queued")}",
                    SimpleTextAttributes.GRAYED_ATTRIBUTES,
                )
            }

            null -> Unit
        }
    }

    protected override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val row = graphRow ?: return
        paintStackGraph(g, height, row, graphHighlight, graphBackground)
    }
}

/** Paints the graph gutter: the lane passing through the row and the node for its branch. */
private fun paintStackGraph(
    g: Graphics,
    height: Int,
    row: StackGraphRow,
    highlight: Color?,
    background: Color?,
) {
    val g2 = g.create() as Graphics2D
    try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val centerX = JBUIScale.scale(GUTTER_WIDTH) / 2
        val centerY = height / 2
        val radius = JBUIScale.scale(4)
        val color = highlight ?: stackGraphColor(row.node)
        g2.color = color
        g2.stroke = BasicStroke(JBUIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (row.lineAbove) g2.drawLine(centerX, 0, centerX, centerY)
        if (row.lineBelow) g2.drawLine(centerX, centerY, centerX, height)
        // The node is filled after the lane so it reads as a solid joint rather than a crossing.
        drawNode(g2, row.node, centerX, centerY, radius, color, background)
    } finally {
        g2.dispose()
    }
}

private fun drawNode(
    g2: Graphics2D,
    node: StackGraphNode,
    centerX: Int,
    centerY: Int,
    radius: Int,
    color: Color,
    background: Color?,
) {
    val diameter = radius * 2
    when (node) {
        // The trunk is the base of the stack, so it gets a rounded square rather than a branch's round node.
        StackGraphNode.TRUNK -> g2.fillRoundRect(
            centerX - radius,
            centerY - radius,
            diameter,
            diameter,
            JBUIScale.scale(2),
            JBUIScale.scale(2),
        )

        StackGraphNode.LAYER, StackGraphNode.NEEDS_REBASE ->
            g2.fillOval(centerX - radius, centerY - radius, diameter, diameter)

        // A merged PR reads as an open node, like a merged commit in the Git log.
        StackGraphNode.MERGED -> {
            if (background != null) {
                g2.color = background
                g2.fillOval(centerX - radius, centerY - radius, diameter, diameter)
                g2.color = color
            }
            g2.stroke = BasicStroke(JBUIScale.scale(1.5f))
            g2.drawOval(centerX - radius, centerY - radius, diameter, diameter)
        }

        // A queued PR is still a branch tip, so it keeps a solid node but a diamond shape.
        StackGraphNode.QUEUED -> g2.fillPolygon(
            intArrayOf(centerX, centerX + radius, centerX, centerX - radius),
            intArrayOf(centerY - radius, centerY, centerY + radius, centerY),
            4,
        )

        StackGraphNode.CURRENT -> {
            g2.fillOval(centerX - radius, centerY - radius, diameter, diameter)
            val ringRadius = radius + JBUIScale.scale(3)
            g2.stroke = BasicStroke(JBUIScale.scale(1f))
            g2.drawOval(centerX - ringRadius, centerY - ringRadius, ringRadius * 2, ringRadius * 2)
        }
    }
}

private fun stackGraphColor(node: StackGraphNode): Color = when (node) {
    StackGraphNode.CURRENT -> currentColor
    StackGraphNode.NEEDS_REBASE -> errorColor
    StackGraphNode.TRUNK, StackGraphNode.LAYER, StackGraphNode.MERGED, StackGraphNode.QUEUED -> laneColor
}
