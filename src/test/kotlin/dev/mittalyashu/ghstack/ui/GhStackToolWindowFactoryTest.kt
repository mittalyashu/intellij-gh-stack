package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.mittalyashu.ghstack.GhStackBundle

class GhStackToolWindowFactoryTest : BasePlatformTestCase() {
    fun testAddsStackAndConsoleTabs() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GH Stack")
            ?: ToolWindowManager.getInstance(project)
                .registerToolWindow("GH Stack", false, ToolWindowAnchor.BOTTOM)

        GhStackToolWindowFactory().createToolWindowContent(project, toolWindow)

        val titles = toolWindow.contentManager.contents.map { it.displayName }
        assertEquals(
            listOf(GhStackBundle.message("tab.title"), GhStackBundle.message("console.tab.title")),
            titles,
        )
        toolWindow.contentManager.removeAllContents(true)
    }
}
