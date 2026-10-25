package dev.mittalyashu.ghstack.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import dev.mittalyashu.ghstack.GhStackBundle

class GhStackConfigurable : BoundConfigurable(GhStackBundle.message("settings.title")) {
    private val settings = GhStackSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row(GhStackBundle.message("settings.stash.label")) {
            comboBox(
                StashMode.entries,
                SimpleListCellRenderer.create("") { GhStackBundle.message(it.messageKey()) },
            ).bindItem(
                { settings.stashMode },
                { settings.stashMode = it ?: StashMode.ASK },
            )
        }
        row {
            comment(GhStackBundle.message("settings.stash.comment"))
        }
    }
}

private fun StashMode.messageKey(): String = when (this) {
    StashMode.ASK -> "settings.stash.ask"
    StashMode.ALWAYS -> "settings.stash.always"
    StashMode.NEVER -> "settings.stash.never"
}
