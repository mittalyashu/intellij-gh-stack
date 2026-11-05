package com.github.mittalyashu.ghstack.ui

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.service.GhCommandLogEntry
import com.github.mittalyashu.ghstack.service.GhLogKind
import com.github.mittalyashu.ghstack.service.GhStackConsoleService
import com.github.mittalyashu.ghstack.service.formatGhCommandLog
import com.github.mittalyashu.ghstack.settings.ConsoleVerbosity
import com.github.mittalyashu.ghstack.settings.GhStackSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Read-only console that shows every `gh` invocation made by the plugin. */
class GhStackConsolePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val console: ConsoleView =
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    private var lastSequence = 0L

    init {
        Disposer.register(this, console)
        add(createToolbar().component, BorderLayout.NORTH)
        add(console.component, BorderLayout.CENTER)

        val service = GhStackConsoleService.getInstance(project)
        project.messageBus.connect(this).subscribe(
            GhStackConsoleService.TOPIC,
            GhStackConsoleService.Listener { entry ->
                ApplicationManager.getApplication().invokeLater({ append(entry) }, ModalityState.any())
            },
        )
        val history = service.history()
        if (history.isEmpty() || GhStackSettings.getInstance().consoleVerbosity == ConsoleVerbosity.OFF) {
            printHint()
        }
        history.forEach { append(it) }
    }

    fun component(): JComponent = this

    override fun dispose() {}

    private fun createToolbar(): ActionToolbar {
        val toolbar = ActionManager.getInstance().createActionToolbar(
            ActionPlaces.TOOLBAR,
            DefaultActionGroup(*console.createConsoleActions()),
            true,
        )
        toolbar.targetComponent = this
        return toolbar
    }

    private fun printHint() {
        val verbosity = GhStackSettings.getInstance().consoleVerbosity
        val hint = if (verbosity == ConsoleVerbosity.OFF) {
            GhStackBundle.message("console.disabled.hint")
        } else {
            GhStackBundle.message("console.hint")
        }
        printLine(hint, ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    private fun append(entry: GhCommandLogEntry) {
        if (Disposer.isDisposed(this) || entry.sequence <= lastSequence) return
        lastSequence = entry.sequence
        formatGhCommandLog(entry, GhStackSettings.getInstance().consoleVerbosity).forEach { line ->
            printLine(line.text, line.kind.contentType())
        }
    }

    private fun printLine(text: String, contentType: ConsoleViewContentType) {
        console.print(text, contentType)
        console.print("\n", contentType)
    }
}

private fun GhLogKind.contentType(): ConsoleViewContentType = when (this) {
    GhLogKind.COMMAND -> ConsoleViewContentType.USER_INPUT
    GhLogKind.OUTPUT -> ConsoleViewContentType.NORMAL_OUTPUT
    GhLogKind.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
}
