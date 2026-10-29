package com.github.mittalyashu.ghstack.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhCli
import com.github.mittalyashu.ghstack.service.describeGhVersions
import com.github.mittalyashu.ghstack.service.detectGhVersions
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JEditorPane

class GhStackConfigurable : BoundConfigurable(GhStackBundle.message("settings.title")) {
    private val settings = GhStackSettings.getInstance()
    private val versionRequest = AtomicInteger()

    override fun createPanel(): DialogPanel {
        var versionText: JEditorPane? = null
        var ghPathField: TextFieldWithBrowseButton? = null
        val panel = panel {
            row(GhStackBundle.message("settings.gh.path.label")) {
                ghPathField = textFieldWithBrowseButton()
                    .bindText(
                        { settings.ghExecutablePath },
                        { settings.ghExecutablePath = it.trim() },
                    )
                    .component
            }
            row {
                comment(GhStackBundle.message("settings.gh.path.comment"))
            }
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
            row(GhStackBundle.message("settings.versions.label")) {
                versionText = text(GhStackBundle.message("settings.versions.loading")).component
                button(GhStackBundle.message("settings.versions.check")) {
                    checkVersions(versionText, ghPathField)
                }
            }
        }
        checkVersions(versionText, ghPathField)
        return panel
    }

    private fun checkVersions(component: JEditorPane?, ghPathField: TextFieldWithBrowseButton?) {
        if (component == null) return
        val request = versionRequest.incrementAndGet()
        component.text = GhStackBundle.message("settings.versions.loading")
        val configuredPath = ghPathField?.text ?: settings.ghExecutablePath
        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            val text = runCatching {
                val workingDirectory = Path.of(
                    System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir"),
                )
                val cli = GhCli(workingDirectory) { configuredPath }
                describeGhVersions(detectGhVersions(cli::version, cli::extensionList))
            }.getOrElse { GhStackBundle.message("settings.versions.error") }
            application.invokeLater({
                if (versionRequest.get() == request) component.text = text
            }, ModalityState.any())
        }
    }
}

private fun StashMode.messageKey(): String = when (this) {
    StashMode.ASK -> "settings.stash.ask"
    StashMode.ALWAYS -> "settings.stash.always"
    StashMode.NEVER -> "settings.stash.never"
}
