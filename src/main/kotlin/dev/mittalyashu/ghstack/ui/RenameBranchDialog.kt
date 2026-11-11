package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.initStackArgs
import com.github.mittalyashu.ghstack.model.StackLayer
import git4idea.validators.GitRefNameValidator
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The `git` and `gh stack` commands that rename a layer's branch and re-initialize its stack. */
internal data class RenameBranchPlan(
    val oldName: String,
    val newName: String,
    val trunk: String,
    val branches: List<String>,
) {
    val unstackCommand: String get() = "gh stack unstack --local"
    val renameCommand: String get() = "git branch -m $oldName $newName"
    val unsetUpstreamCommand: String get() = "git branch --unset-upstream $newName"
    val initCommand: String get() = "gh ${initStackArgs(branches, trunk).joinToString(" ")}"
    val commands: List<String>
        get() = listOf(unstackCommand, renameCommand, unsetUpstreamCommand, initCommand)
}

/** Builds the plan for renaming [oldName] to [newName], keeping the branch list in bottom-to-top stack order. */
internal fun buildRenameBranchPlan(
    oldName: String,
    newName: String,
    trunk: String,
    branches: List<String>,
): RenameBranchPlan = RenameBranchPlan(
    oldName = oldName,
    newName = newName,
    trunk = trunk,
    branches = branches.map { if (it == oldName) newName else it },
)

/**
 * The popup description for the rename action: renaming a branch with a pull request closes that pull request on
 * GitHub, so the action explains the consequence and stays disabled, while a layer without one gets the plain prompt.
 */
internal fun renameBranchDescription(layer: StackLayer?): String = when {
    layer == null -> GhStackBundle.message("panel.rename.branch.no.layer")
    layer.pr != null -> GhStackBundle.message("panel.rename.branch.pr", layer.pr.number)
    else -> GhStackBundle.message("panel.rename.branch.description")
}

/**
 * Prompts for a new branch name and sanitises it as the user types, using git4idea's [GitRefNameValidator]. The
 * description spells out the unstack / rename / init sequence and shows the commands the panel will run.
 */
internal class RenameBranchDialog(
    project: Project,
    private val oldName: String,
    private val trunk: String,
    private val branches: List<String>,
    existingBranches: Set<String>,
) : DialogWrapper(project) {
    private val validator = GitRefNameValidator.getInstance()
    private val takenNames = existingBranches - oldName
    private val nameField = JBTextField(oldName).apply { columns = 30 }
    private val commandsArea = JBTextArea().apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        columns = COMMAND_COLUMNS
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        border = JBUI.Borders.empty()
    }
    private var descriptionComponent: JEditorPane? = null

    init {
        title = GhStackBundle.message("dialog.rename.branch.title")
        init()
        nameField.selectAll()
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onNameChanged()
            override fun removeUpdate(e: DocumentEvent) = onNameChanged()
            override fun changedUpdate(e: DocumentEvent) = onNameChanged()
        })
        updatePreview()
    }

    fun showAndGetBranchName(): String? {
        if (!showAndGet()) return null
        return cleanedName().ifEmpty { null }
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun createCenterPanel(): JComponent = panel {
        row(GhStackBundle.message("dialog.rename.branch.prompt")) {
            cell(nameField)
        }
        row {
            descriptionComponent = comment("").component
        }
        row {
            cell(commandsArea)
        }
    }

    override fun doValidate(): ValidationInfo? {
        val name = cleanedName()
        return when {
            name.isEmpty() -> ValidationInfo(GhStackBundle.message("dialog.add.branch.empty"), nameField)
            !validator.checkInput(name) -> ValidationInfo(GhStackBundle.message("dialog.add.branch.invalid"), nameField)
            name == oldName -> ValidationInfo(GhStackBundle.message("dialog.rename.branch.same"), nameField)
            name in takenNames -> ValidationInfo(GhStackBundle.message("dialog.rename.branch.exists", name), nameField)
            else -> null
        }
    }

    private fun cleanedName(): String = validator.cleanUpBranchName(nameField.text).trim()

    private fun onNameChanged() {
        cleanUpLater()
        updatePreview()
    }

    private fun updatePreview() {
        if (isDisposed) return
        val proposed = cleanedName().ifEmpty { GhStackBundle.message("dialog.rename.branch.placeholder") }
        descriptionComponent?.text = StringUtil.escapeXmlEntities(
            GhStackBundle.message("dialog.rename.branch.description", oldName, proposed),
        )
        val commands = buildRenameBranchPlan(oldName, proposed, trunk, branches).commands
        commandsArea.text = commands.joinToString("\n")
        // Grow the code block with wrapped command lines so a long branch list is never clipped.
        commandsArea.rows = commands.sumOf { it.length / COMMAND_COLUMNS + 1 }
        commandsArea.revalidate()
    }

    // The document cannot be modified from inside a document listener, so defer the cleanup to the EDT.
    private fun cleanUpLater() {
        SwingUtilities.invokeLater {
            if (isDisposed) return@invokeLater
            val (fixed, caret) = cleanUpBranchNameOnTyping(nameField.text, nameField.caretPosition)
            if (fixed == nameField.text) return@invokeLater
            nameField.text = fixed
            nameField.caretPosition = caret
        }
    }

    private companion object {
        private const val COMMAND_COLUMNS = 48
    }
}
