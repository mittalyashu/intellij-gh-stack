package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.ide.CopyPasteManager

/** Copies the selected stack row's branch name to the clipboard; a no-op when nothing is selected. */
internal fun copyBranchName(row: StackRow?) {
    CopyPasteManager.copyTextToClipboard(branchNameToCopy(row) ?: return)
}
