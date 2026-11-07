package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer

/** A row in the stack list: the trunk or one layer of the stack, each backed by a branch name. */
internal sealed class StackRow {
    abstract val branchName: String

    data class Trunk(override val branchName: String) : StackRow()

    data class Layer(val layer: StackLayer) : StackRow() {
        override val branchName: String get() = layer.name
    }
}

/** The branch name to copy for the selected row, or null when nothing is selected. */
internal fun branchNameToCopy(row: StackRow?): String? = row?.branchName
