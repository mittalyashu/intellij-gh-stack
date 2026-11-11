package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.GhStackBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
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

        val rebaseToolbars = rebaseToolbars(panel)
        assertEquals("The rebase action should appear exactly once", 1, rebaseToolbars.size)
        assertTrue(
            "The rebase action should sit in the current stack header",
            isDescendant(title!!.parent, rebaseToolbars.single().component),
        )
    }

    fun testRebaseVariantsStayInASingleDropdown() {
        val panel = GhStackPanel(project)
        Disposer.register(testRootDisposable, panel)

        val children = rebaseToolbars(panel).single().actionGroup.getChildren(null)
        assertEquals("The rebase action should be a single toolbar button", 1, children.size)
        val rebaseGroup = children.single() as? DefaultActionGroup
        assertNotNull("The rebase action should be a dropdown group", rebaseGroup)
        assertEquals(GhStackBundle.message("panel.rebase"), rebaseGroup!!.templatePresentation.text)
        assertTrue("The rebase group should open as a popup", rebaseGroup.isPopup)
        assertEquals("Every rebase variant should stay in the dropdown", 4, rebaseGroup.getChildren(null).size)
    }

    /** Toolbars whose root group wraps the "Rebase stack" group, i.e. the one that shows it as a dropdown. */
    private fun rebaseToolbars(root: Component): List<ActionToolbar> {
        val rebaseText = GhStackBundle.message("panel.rebase")
        return toolbars(root).filter { toolbar ->
            toolbar.actionGroup.getChildren(null).any { child ->
                child is ActionGroup && child.templatePresentation.text == rebaseText
            }
        }
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
