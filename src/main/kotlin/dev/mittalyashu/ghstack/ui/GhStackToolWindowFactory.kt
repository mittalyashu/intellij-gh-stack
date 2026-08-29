package dev.mittalyashu.ghstack.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import git4idea.repo.GitRepositoryManager

class GhStackToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GhStackPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component(), "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
