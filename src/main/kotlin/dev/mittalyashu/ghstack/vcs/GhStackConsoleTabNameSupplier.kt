package com.github.mittalyashu.ghstack.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.github.mittalyashu.ghstack.GhStackBundle
import java.util.function.Supplier

class GhStackConsoleTabNameSupplier(@Suppress("unused") project: Project) : Supplier<@NlsContexts.TabTitle String> {
    override fun get(): String = GhStackBundle.message("console.git.tab.title")
}
