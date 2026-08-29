package dev.mittalyashu.ghstack.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import dev.mittalyashu.ghstack.ui.GhStackPanel

class GhStackChangesViewContentProvider(private val project: Project) : ChangesViewContentProvider {
    private var panel: GhStackPanel? = null

    override fun initTabContent(content: Content) {
        val stackPanel = GhStackPanel(project)
        panel = stackPanel
        content.setComponent(stackPanel.component())
        content.setDisposer(stackPanel)
    }

    override fun disposeContent() {
        panel = null
    }
}
