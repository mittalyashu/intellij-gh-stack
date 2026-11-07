package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.github.mittalyashu.ghstack.GhStackBundle
import git4idea.repo.GitRepositoryManager

class GhStackToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val panel = GhStackPanel(project)
        val stackContent = contentFactory.createContent(panel.component(), GhStackBundle.message("tab.title"), false)
        stackContent.setDisposer(panel)
        toolWindow.contentManager.addContent(stackContent)

        val console = GhStackConsolePanel(project)
        val consoleContent = contentFactory.createContent(
            console.component(),
            GhStackBundle.message("console.tab.title"),
            false,
        )
        consoleContent.setDisposer(console)
        toolWindow.contentManager.addContent(consoleContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
