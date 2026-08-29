package dev.mittalyashu.ghstack.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import dev.mittalyashu.ghstack.GhStackBundle
import java.util.function.Supplier

class GhStackTabNameSupplier(@Suppress("unused") project: Project) : Supplier<@NlsContexts.TabTitle String> {
    override fun get(): String = GhStackBundle.message("tab.title")
}
