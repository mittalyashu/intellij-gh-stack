package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.github.mittalyashu.ghstack.GhStackBundle
import java.awt.Component
import java.awt.Container

class CurrentStackHeaderTest : BasePlatformTestCase() {
    fun testRebaseActionSitsNextToCurrentStackTitle() {
        val panel = GhStackPanel(project)
        Disposer.register(testRootDisposable, panel)

        val title = findComponent(panel) {
            it is JBLabel && it.text == GhStackBundle.message("panel.current.stack.title")
        }
        assertNotNull("The current stack title is missing", title)

        val rebaseText = GhStackBundle.message("panel.rebase")
        val rebaseToolbars = toolbars(panel).filter { it.actionGroup.templatePresentation.text == rebaseText }
        assertEquals("The rebase action should appear exactly once", 1, rebaseToolbars.size)
        assertTrue(
            "The rebase action should sit in the current stack header",
            isDescendant(title!!.parent, rebaseToolbars.single().component),
        )
    }

    private fun findComponent(root: Component, predicate: (Component) -> Boolean): Component? {
        if (predicate(root)) return root
        if (root is Container) {
            root.components.forEach { child -> findComponent(child, predicate)?.let { return it } }
        }
        return null
    }

    private fun toolbars(root: Component): List<ActionToolbar> = buildList {
        if (root is ActionToolbar) add(root)
        if (root is Container) root.components.forEach { addAll(toolbars(it)) }
    }

    private fun isDescendant(ancestor: Component, component: Component): Boolean {
        var current: Component? = component
        while (current != null) {
            if (current == ancestor) return true
            current = current.parent
        }
        return false
    }
}
