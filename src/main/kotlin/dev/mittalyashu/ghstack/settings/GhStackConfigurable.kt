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
        row(GhStackBundle.message("settings.shelve.label")) {
            comboBox(
                ShelveMode.entries,
                SimpleListCellRenderer.create("") { GhStackBundle.message(it.messageKey()) },
            ).bindItem(
                { settings.shelveMode },
                { settings.shelveMode = it ?: ShelveMode.ASK },
            )
        }
        row {
            comment(GhStackBundle.message("settings.shelve.comment"))
        }
    }
}

private fun ShelveMode.messageKey(): String = when (this) {
    ShelveMode.ASK -> "settings.shelve.ask"
    ShelveMode.ALWAYS -> "settings.shelve.always"
    ShelveMode.NEVER -> "settings.shelve.never"
}
