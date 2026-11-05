package com.github.mittalyashu.ghstack.vcs

import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.mittalyashu.ghstack.GhStackBundle

class GhStackConsoleTabRegistrationTest : BasePlatformTestCase() {
    fun testRegistersConsoleTabNextToTheStackTab() {
        val consoleTab = ChangesViewContentEP.EP_NAME.getExtensions(project)
            .single { it.tabName == "GHStackConsole" }

        assertEquals(GhStackBundle.message("console.git.tab.title"), consoleTab.getDisplayName(project))
        assertTrue(consoleTab.getInstance(project) is GhStackConsoleContentProvider)
    }
}
