package com.github.mittalyashu.ghstack.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import com.github.mittalyashu.ghstack.ui.GhStackConsolePanel

/** Hosts the command console next to the Stack tab of the Git / Version Control tool window. */
class GhStackConsoleContentProvider(private val project: Project) : ChangesViewContentProvider {
    private var panel: GhStackConsolePanel? = null

    override fun initTabContent(content: Content) {
        val consolePanel = GhStackConsolePanel(project)
        panel = consolePanel
        content.setComponent(consolePanel.component())
        content.setDisposer(consolePanel)
    }

    override fun disposeContent() {
        panel = null
    }
}
