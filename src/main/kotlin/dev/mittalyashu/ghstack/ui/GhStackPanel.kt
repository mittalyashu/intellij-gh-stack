package dev.mittalyashu.ghstack.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.mittalyashu.ghstack.GhStackBundle
import dev.mittalyashu.ghstack.model.StackLayer
import dev.mittalyashu.ghstack.model.StackLoadResult
import dev.mittalyashu.ghstack.model.StackView
import dev.mittalyashu.ghstack.service.GhStackService
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

class GhStackPanel(private val project: Project) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {
    private val service = project.getService(GhStackService::class.java)
    private val status = JBLabel("", SwingConstants.LEFT).apply {
        border = JBUI.Borders.empty(8, 12)
        foreground = UIUtil.getContextHelpForeground()
    }
    private val listModel = DefaultListModel<StackRow>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = StackRowRenderer()
        emptyText.text = GhStackBundle.message("status.loading")
    }
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var loading = false

    init {
        val toolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, createActions(), true)
        toolbar.targetComponent = this

        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) checkoutSelected()
            }
        })

        project.messageBus.connect(this).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { scheduleRefresh() },
        )

        scheduleRefresh()
    }

    fun component(): JComponent = this

    fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refresh() }, 250)
    }

    private fun refresh() {
        if (loading || Disposer.isDisposed(this)) return
        loading = true
        status.text = GhStackBundle.message("status.loading")
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GhStackBundle.message("progress.view"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val result = service.loadCurrentStack()
                ApplicationManager.getApplication().invokeLater({
                    loading = false
                    if (!Disposer.isDisposed(this@GhStackPanel)) {
                        render(result)
                    }
                }, ModalityState.any())
            }
        })
    }

    private fun render(result: StackLoadResult) {
        listModel.clear()
        when (result) {
            is StackLoadResult.Loaded -> {
                val view = result.view
                listModel.addElement(StackRow.Trunk(view.trunk))
                view.branches.forEach { listModel.addElement(StackRow.Layer(it)) }
                status.text = stackSummary(view)
                list.emptyText.text = ""
            }
            is StackLoadResult.NotInStack -> {
                status.text = GhStackBundle.message("status.not.in.stack")
                list.emptyText.text = GhStackBundle.message("status.not.in.stack")
            }
            is StackLoadResult.Failed -> {
                status.text = result.message
                list.emptyText.text = result.message
            }
        }
    }

    private fun stackSummary(view: StackView): String {
        val names = buildList {
            add(view.trunk)
            addAll(view.branches.map { it.name })
        }
        return names.joinToString("  →  ")
    }

    private fun createActions(): DefaultActionGroup {
        val group = DefaultActionGroup()
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.refresh"),
            null,
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) = refresh()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.add.branch"),
            null,
            AllIcons.General.Add,
        ) {
            override fun actionPerformed(e: AnActionEvent) = addBranch()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.checkout"),
            null,
            AllIcons.Actions.Execute,
        ) {
            override fun actionPerformed(e: AnActionEvent) = checkoutSelected()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedLayer() != null
            }
        })
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.all.stacks"),
            null,
            AllIcons.Vcs.Branch,
        ) {
            override fun actionPerformed(e: AnActionEvent) = showAllStacks()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.init.stack"),
            null,
            AllIcons.Actions.NewFolder,
        ) {
            override fun actionPerformed(e: AnActionEvent) = initStack()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        return group
    }

    private fun selectedLayer(): StackLayer? {
        return (list.selectedValue as? StackRow.Layer)?.layer
    }

    private fun addBranch() {
        val name = Messages.showInputDialog(
            project,
            GhStackBundle.message("dialog.add.branch.prompt"),
            GhStackBundle.message("dialog.add.branch.title"),
            Messages.getQuestionIcon(),
        )?.trim().orEmpty()
        if (name.isEmpty()) return
        runGh(GhStackBundle.message("progress.add")) {
            service.addBranch(name)
        }
    }

    private fun checkoutSelected() {
        val layer = selectedLayer() ?: return
        runGh(GhStackBundle.message("progress.checkout")) {
            service.checkout(layer.name)
        }
    }

    private fun initStack() {
        val name = Messages.showInputDialog(
            project,
            GhStackBundle.message("dialog.init.prompt"),
            GhStackBundle.message("dialog.init.title"),
            Messages.getQuestionIcon(),
        )?.trim().orEmpty()
        if (name.isEmpty()) return
        runGh(GhStackBundle.message("progress.init")) {
            service.initStack(name)
        }
    }

    private fun showAllStacks() {
        AllStacksDialog(project, service).showAndGet().also {
            if (it) scheduleRefresh()
        }
    }

    private fun runGh(title: String, command: () -> dev.mittalyashu.ghstack.cli.GhResult) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) {
                val result = command()
                ApplicationManager.getApplication().invokeLater({
                    if (!result.succeeded) {
                        Messages.showErrorDialog(project, result.errorText(), GhStackBundle.message("notification.error"))
                    }
                    service.refreshGit()
                    refresh()
                }, ModalityState.any())
            }
        })
    }

    override fun dispose() {}
}

private sealed class StackRow {
    data class Trunk(val name: String) : StackRow()
    data class Layer(val layer: StackLayer) : StackRow()
}

private class StackRowRenderer : ColoredListCellRenderer<StackRow>() {
    override fun customizeCellRenderer(
        list: JList<out StackRow>,
        value: StackRow?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        ipad = JBUI.insets(6, 10)
        when (value) {
            is StackRow.Trunk -> {
                icon = AllIcons.Nodes.Module
                append(value.name, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append("  trunk", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            }
            is StackRow.Layer -> {
                val layer = value.layer
                icon = if (layer.isCurrent) AllIcons.Nodes.Favorite else AllIcons.Vcs.Branch
                val style = if (layer.isCurrent) {
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.namedColor("Link.activeForeground", JBColor.BLUE))
                } else {
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(layer.name, style)
                layer.pr?.let { append("  #${it.number}", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                if (layer.isCurrent) append("  ${GhStackBundle.message("branch.current")}", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                if (layer.needsRebase) append("  ${GhStackBundle.message("branch.needs.rebase")}", SimpleTextAttributes.ERROR_ATTRIBUTES)
                if (layer.isMerged) append("  ${GhStackBundle.message("branch.merged")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                if (layer.isQueued) append("  ${GhStackBundle.message("branch.queued")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            null -> Unit
        }
    }
}
