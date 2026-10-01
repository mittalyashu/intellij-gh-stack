package dev.mittalyashu.ghstack.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import dev.mittalyashu.ghstack.GhStackBundle
import javax.swing.JComponent
import javax.swing.JPanel

class GhStackConfigurable : Configurable {
    private val settings = GhStackSettings.getInstance()
    private val ghPathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
    }
    private var panel: JPanel? = null

    override fun getDisplayName(): String = GhStackBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        if (panel == null) {
            val comment = JBLabel(GhStackBundle.message("settings.gh.path.comment")).apply {
                foreground = UIUtil.getContextHelpForeground()
            }
            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(
                    GhStackBundle.message("settings.gh.path.label"),
                    ghPathField,
                )
                .addComponentToRightColumn(comment)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
        return panel!!
    }

    override fun isModified(): Boolean = ghPathField.text.trim() != settings.ghExecutablePath

    override fun apply() {
        settings.ghExecutablePath = ghPathField.text.trim()
    }

    override fun reset() {
        ghPathField.text = settings.ghExecutablePath
    }

    override fun disposeUIResources() {
        panel = null
    }
}
