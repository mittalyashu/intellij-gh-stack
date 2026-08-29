package dev.mittalyashu.ghstack.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.mittalyashu.ghstack.GhStackBundle
import dev.mittalyashu.ghstack.model.TrackedStack
import dev.mittalyashu.ghstack.service.GhStackService
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class AllStacksDialog(
    private val project: Project,
    private val service: GhStackService,
) : DialogWrapper(project) {
    private val listModel = DefaultListModel<TrackedStack>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = javax.swing.DefaultListCellRenderer().apply {
            // labels come from TrackedStack.toString via custom renderer below
        }
        setCellRenderer { _, value, _, _, _ ->
            javax.swing.JLabel(value?.displayLabel().orEmpty()).apply {
                border = JBUI.Borders.empty(4, 8)
            }
        }
        emptyText.text = GhStackBundle.message("dialog.all.stacks.empty")
    }

    init {
        title = GhStackBundle.message("dialog.all.stacks.title")
        init()
        loadStacks()
    }

    override fun createCenterPanel(): JComponent {
        return JBScrollPane(list).apply {
            preferredSize = Dimension(560, 280)
        }
    }

    override fun doOKAction() {
        val selected = list.selectedValue ?: return
        val target = selected.checkoutTarget()
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GhStackBundle.message("progress.checkout"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val result = service.checkout(target)
                ApplicationManager.getApplication().invokeLater({
                    if (!result.succeeded) {
                        Messages.showErrorDialog(
                            project,
                            result.errorText(),
                            GhStackBundle.message("notification.error"),
                        )
                    } else {
                        service.refreshGit()
                        close(OK_EXIT_CODE)
                    }
                }, ModalityState.any())
            }
        })
    }

    private fun loadStacks() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GhStackBundle.message("progress.list"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val result = service.loadTrackedStacks()
                ApplicationManager.getApplication().invokeLater({
                    listModel.clear()
                    result.fold(
                        onSuccess = { tracked ->
                            tracked.stacks.forEach { listModel.addElement(it) }
                            if (tracked.stacks.isNotEmpty()) {
                                list.selectedIndex = 0
                            }
                        },
                        onFailure = {
                            Messages.showErrorDialog(
                                project,
                                it.message ?: GhStackBundle.message("dialog.all.stacks.empty"),
                                GhStackBundle.message("notification.error"),
                            )
                        },
                    )
                }, ModalityState.any())
            }
        })
    }
}
