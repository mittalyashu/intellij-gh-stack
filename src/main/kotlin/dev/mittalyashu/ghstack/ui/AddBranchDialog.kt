package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.github.mittalyashu.ghstack.GhStackBundle
import git4idea.validators.GitRefNameValidator
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Prompts for a new stack branch name and sanitises it as the user types, using git4idea's
 * [GitRefNameValidator] (the same logic behind the native Git "New Branch" dialog), so invalid
 * input such as `abc abc abc` is turned into `abc-abc-abc` instead of failing in `gh stack add`.
 */
internal class AddBranchDialog(project: Project) : DialogWrapper(project) {
    private val validator = GitRefNameValidator.getInstance()
    private val nameField = JBTextField().apply { columns = 30 }

    init {
        title = GhStackBundle.message("dialog.add.branch.title")
        init()
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = cleanUpLater()
            override fun removeUpdate(e: DocumentEvent) = cleanUpLater()
            override fun changedUpdate(e: DocumentEvent) = cleanUpLater()
        })
    }

    fun showAndGetBranchName(): String? {
        if (!showAndGet()) return null
        return cleanedName().ifEmpty { null }
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(GhStackBundle.message("dialog.add.branch.prompt"), nameField)
        .panel

    override fun doValidate(): ValidationInfo? {
        val name = cleanedName()
        return when {
            name.isEmpty() -> ValidationInfo(GhStackBundle.message("dialog.add.branch.empty"), nameField)
            !validator.checkInput(name) -> ValidationInfo(GhStackBundle.message("dialog.add.branch.invalid"), nameField)
            else -> null
        }
    }

    private fun cleanedName(): String = validator.cleanUpBranchName(nameField.text).trim()

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
}
