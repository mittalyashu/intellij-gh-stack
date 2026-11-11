package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import com.github.mittalyashu.ghstack.model.StackView

/** A row in the stack list: the trunk or one layer of the stack, each backed by a branch name. */
internal sealed class StackRow {
    abstract val branchName: String

    data class Trunk(override val branchName: String) : StackRow()

    data class Layer(val layer: StackLayer) : StackRow() {
        override val branchName: String get() = layer.name
    }
}

/**
 * Builds the rows to display in the current stack list.
 *
 * Rows are ordered top-to-bottom with the top branch at the top and the trunk branch at the bottom.
 */
internal fun stackRows(view: StackView): List<StackRow> = buildList {
    for (layer in view.branches.asReversed()) {
        add(StackRow.Layer(layer))
    }
    add(StackRow.Trunk(view.trunk))
}

/** The branch name to copy for the selected row, or null when nothing is selected. */
internal fun branchNameToCopy(row: StackRow?): String? = row?.branchName
