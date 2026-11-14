package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.model.TrackedStack
import com.github.mittalyashu.ghstack.service.InitStackOptions
import com.github.mittalyashu.ghstack.service.defaultFirstBranch
import com.github.mittalyashu.ghstack.service.existingStackForBranch
import com.github.mittalyashu.ghstack.service.stackContainingBranch
import javax.swing.JCheckBox
import javax.swing.JComponent

/** The choices made in [InitStackDialog]. [base] is empty when no trunk branch could be offered. */
data class InitStackRequest(
    val branch: String,
    val base: String,
    val unstackFirst: Boolean,
)

/**
 * First-class replacement for the plain branch-name prompt: lets the user pick the trunk the stack is
 * based on, adopt an existing local branch (or name a new one), and unstack an existing stack first.
 */
class InitStackDialog(
    project: Project,
    private val options: InitStackOptions,
) : DialogWrapper(project) {
    private val existingStack = existingStackForBranch(options.trackedStacks, options.currentBranch.orEmpty())
    private val baseCombo = ComboBox(options.localBranches.toTypedArray()).apply { isEditable = true }
    private val branchCombo = ComboBox(options.localBranches.toTypedArray()).apply {
        isEditable = true
        selectedItem = defaultFirstBranch(options.currentBranch, options.defaultBase)
    }
    private val unstackCheckBox = JCheckBox(GhStackBundle.message("dialog.init.unstack")).apply {
        isVisible = existingStack != null
    }

    init {
        title = GhStackBundle.message("dialog.init.title")
        baseCombo.selectedItem = options.defaultBase
        init()
    }

    override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        existingStack?.let { existing ->
            builder
                .addComponent(
                    JBLabel(
                        html(
                            GhStackBundle.message(
                                "dialog.init.existing.stack",
                                options.currentBranch.orEmpty(),
                                existing.trunk,
                            )
                        )
                    )
                )
                .addComponent(unstackCheckBox)
                .addVerticalGap(8)
        }
        return builder
            .addLabeledComponent(GhStackBundle.message("dialog.init.base.label"), baseCombo)
            .addLabeledComponent(GhStackBundle.message("dialog.init.branch.label"), branchCombo)
            .addComponent(JBLabel(html(GhStackBundle.message("dialog.init.hint"))).apply {
                foreground = UIUtil.getContextHelpForeground()
            })
            .panel
    }

    override fun doValidate(): ValidationInfo? {
        val branch = branchName()
        if (branch.isEmpty()) {
            return ValidationInfo(GhStackBundle.message("dialog.init.branch.required"), branchCombo)
        }
        if (branch == baseName()) {
            return ValidationInfo(GhStackBundle.message("dialog.init.branch.same.as.base"), branchCombo)
        }
        if (existingStack != null && !unstackCheckBox.isSelected) {
            return ValidationInfo(GhStackBundle.message("dialog.init.unstack.required"), unstackCheckBox)
        }
        if (stackContainingBranch(remainingStacks(), branch) != null) {
            return ValidationInfo(GhStackBundle.message("dialog.init.branch.in.stack", branch), branchCombo)
        }
        return null
    }

    fun request(): InitStackRequest = InitStackRequest(
        branch = branchName(),
        base = baseName(),
        unstackFirst = existingStack != null && unstackCheckBox.isSelected,
    )

    /** After an opted-in unstack the current branch's stack is gone, so it no longer blocks the new one. */
    private fun remainingStacks(): List<TrackedStack> {
        val unstacking = existingStack != null && unstackCheckBox.isSelected
        return if (unstacking) options.trackedStacks.filter { it !== existingStack } else options.trackedStacks
    }

    private fun baseName(): String = (baseCombo.editor.item as? String)?.trim().orEmpty()

    private fun branchName(): String = (branchCombo.editor.item as? String)?.trim().orEmpty()
}

private fun html(text: String): String =
    "<html><body style='width:360px'>${escapeHtml(text)}</body></html>"

/** Branch names may contain `&`, `<` and `>`; escape them so they render literally in the HTML label. */
private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
