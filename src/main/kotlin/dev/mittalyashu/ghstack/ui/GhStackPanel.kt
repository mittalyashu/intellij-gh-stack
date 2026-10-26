package com.github.mittalyashu.ghstack.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.cli.GhResult
import com.github.mittalyashu.ghstack.model.StackLayer
import com.github.mittalyashu.ghstack.model.StackLoadResult
import com.github.mittalyashu.ghstack.model.StackView
import com.github.mittalyashu.ghstack.service.GhStackService
import com.github.mittalyashu.ghstack.service.RebaseOutcome
import com.github.mittalyashu.ghstack.service.RebaseAction
import com.github.mittalyashu.ghstack.service.decideRebaseAction
import com.github.mittalyashu.ghstack.settings.GhStackSettings
import com.github.mittalyashu.ghstack.settings.StashMode
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
    @Volatile
    private var highlightBranch: String? = null
    private lateinit var actionToolbar: ActionToolbar

    init {
        actionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, createActions(), true)
        actionToolbar.targetComponent = this

        add(actionToolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)

        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = selectRowUnderPopupTrigger(e)

            override fun mouseReleased(e: MouseEvent) = selectRowUnderPopupTrigger(e)
        })
        PopupHandler.installSelectionListPopup(list, createPopupGroup(), ActionPlaces.POPUP)

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
        highlightNewBranch(result)
        actionToolbar.updateActionsAsync()
    }

    /** Selects the branch just created by an init, so the new stack stands out after the refresh. */
    private fun highlightNewBranch(result: StackLoadResult) {
        val branch = highlightBranch ?: return
        if (result !is StackLoadResult.Loaded) return
        highlightBranch = null
        val index = (0 until listModel.size()).firstOrNull {
            (listModel.getElementAt(it) as? StackRow.Layer)?.layer?.name == branch
        }
        if (index != null) {
            list.selectedIndex = index
            list.ensureIndexIsVisible(index)
        }
    }

    private fun stackSummary(view: StackView): String {
        val names = buildList {
            add(view.trunk)
            addAll(view.branches.map { it.name })
        }
        return names.joinToString("  →  ")
    }

    private fun createPopupGroup(): DefaultActionGroup {
        return DefaultActionGroup(
            object : DumbAwareAction(
                GhStackBundle.message("panel.checkout"),
                null,
                AllIcons.Actions.CheckOut,
            ) {
                override fun actionPerformed(e: AnActionEvent) = checkoutSelected()
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = checkoutTargetFor(selectedLayer()) != null
                }
            },
        )
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
        group.add(createRebaseGroup())
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.rebase.continue"),
            null,
            AllIcons.Actions.Resume,
        ) {
            override fun actionPerformed(e: AnActionEvent) = rebaseContinue()
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(e: AnActionEvent) {
                e.presentation.isVisible = service.isRebaseInProgress()
            }
        })
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.rebase.abort"),
            null,
            AllIcons.Actions.Cancel,
        ) {
            override fun actionPerformed(e: AnActionEvent) = rebaseAbort()
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(e: AnActionEvent) {
                e.presentation.isVisible = service.isRebaseInProgress()
            }
        })
        return group
    }

    private fun createRebaseGroup(): DefaultActionGroup {
        fun variant(messageKey: String, vararg flags: String) = object : DumbAwareAction(
            GhStackBundle.message(messageKey),
        ) {
            override fun actionPerformed(e: AnActionEvent) = rebase(*flags)
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }

        return DefaultActionGroup(
            GhStackBundle.message("panel.rebase"),
            listOf(
                variant("panel.rebase.stack"),
                variant("panel.rebase.upstack", "--upstack"),
                variant("panel.rebase.downstack", "--downstack"),
                variant("panel.rebase.no.trunk", "--no-trunk"),
            ),
        ).apply {
            isPopup = true
            templatePresentation.icon = AllIcons.Vcs.Merge
            templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, false)
        }
    }

    private fun selectedLayer(): StackLayer? {
        return (list.selectedValue as? StackRow.Layer)?.layer
    }

    /** Right-clicking a row selects it first (like the native Git panel) so the popup acts on that row. */
    private fun selectRowUnderPopupTrigger(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val index = list.locationToIndex(e.point)
        if (index >= 0 && list.getCellBounds(index, index)?.contains(e.point) == true) {
            list.selectedIndex = index
        }
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
        val target = checkoutTargetFor(selectedLayer()) ?: return
        runGh(GhStackBundle.message("progress.checkout")) {
            service.checkout(target)
        }
    }

    private fun initStack() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GhStackBundle.message("progress.init.load"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val options = service.loadInitStackOptions()
                ApplicationManager.getApplication().invokeLater({
                    if (Disposer.isDisposed(this@GhStackPanel)) return@invokeLater
                    val dialog = InitStackDialog(project, options)
                    if (dialog.showAndGet()) {
                        runInit(dialog.request())
                    }
                }, ModalityState.any())
            }
        })
    }

    private fun runInit(request: InitStackRequest) {
        runTask(GhStackBundle.message("progress.init")) {
            if (request.unstackFirst) {
                val unstack = service.unstackLocal()
                if (!unstack.succeeded) return@runTask RebaseOutcome(unstack)
            }
            val result = service.initStack(request.branch, request.base.ifEmpty { null })
            if (result.succeeded) {
                highlightBranch = request.branch
                RebaseOutcome(result)
            } else if (request.unstackFirst) {
                RebaseOutcome(
                    result.copy(
                        stderr = "${result.errorText()}\n\n${GhStackBundle.message("dialog.init.unstack.lost")}",
                    )
                )
            } else {
                RebaseOutcome(result)
            }
        }
    }

    private fun rebase(vararg flags: String) {
        runTask(GhStackBundle.message("progress.rebase")) {
            val settings = GhStackSettings.getInstance()
            val stash = when (decideRebaseAction(settings.stashMode, service.isDirty())) {
                RebaseAction.RUN -> false
                RebaseAction.STASH -> true
                RebaseAction.PROMPT -> askToStash(settings)
            }
            if (stash) service.rebaseWithStash(*flags) else RebaseOutcome(service.rebase(*flags))
        }
    }

    /** Returns whether to stash. Persists the choice when "remember" is ticked. Call from a background thread. */
    private fun askToStash(settings: GhStackSettings): Boolean {
        var stash = false
        ApplicationManager.getApplication().invokeAndWait({
            var remember = false
            stash = MessageDialogBuilder
                .yesNo(
                    GhStackBundle.message("dialog.stash.title"),
                    GhStackBundle.message("dialog.stash.message"),
                )
                .yesText(GhStackBundle.message("dialog.stash.yes"))
                .noText(GhStackBundle.message("dialog.stash.no"))
                .doNotAsk(object : DoNotAskOption.Adapter() {
                    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                        remember = isSelected
                    }

                    override fun getDoNotShowMessage() = GhStackBundle.message("dialog.stash.remember")
                })
                .ask(project)
            if (remember) {
                settings.stashMode = if (stash) StashMode.ALWAYS else StashMode.NEVER
            }
        }, ModalityState.defaultModalityState())
        return stash
    }

    private fun rebaseContinue() {
        runGh(GhStackBundle.message("progress.rebase.continue")) {
            service.rebaseContinue()
        }
    }

    private fun rebaseAbort() {
        val confirmed = Messages.showYesNoDialog(
            project,
            GhStackBundle.message("dialog.rebase.abort.confirm"),
            GhStackBundle.message("dialog.rebase.abort.title"),
            Messages.getWarningIcon(),
        ) == Messages.YES
        if (!confirmed) return
        runGh(GhStackBundle.message("progress.rebase.abort")) {
            service.rebaseAbort()
        }
    }

    private fun showAllStacks() {
        AllStacksDialog(project, service).showAndGet().also {
            if (it) scheduleRefresh()
        }
    }

    private fun runGh(title: String, command: () -> GhResult) = runTask(title) { RebaseOutcome(command()) }

    private fun runTask(title: String, command: () -> RebaseOutcome) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) {
                val outcome = command()
                outcome.stashToRestore?.let { stash ->
                    if (!service.restoreStash(stash)) {
                        ApplicationManager.getApplication().invokeLater({
                            Messages.showErrorDialog(
                                project,
                                GhStackBundle.message("stash.restore.missing", stash.message),
                                GhStackBundle.message("notification.error"),
                            )
                        }, ModalityState.any())
                    }
                }
                service.refreshGit()
                ApplicationManager.getApplication().invokeLater({
                    if (!outcome.result.succeeded) {
                        Messages.showErrorDialog(
                            project,
                            outcome.result.errorText(),
                            GhStackBundle.message("notification.error")
                        )
                    }
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
                    SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_BOLD,
                        JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
                    )
                } else {
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(layer.name, style)
                layer.pr?.let { append("  #${it.number}", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                if (layer.isCurrent) append(
                    "  ${GhStackBundle.message("branch.current")}",
                    SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES
                )
                if (layer.needsRebase) append(
                    "  ${GhStackBundle.message("branch.needs.rebase")}",
                    SimpleTextAttributes.ERROR_ATTRIBUTES
                )
                if (layer.isMerged) append(
                    "  ${GhStackBundle.message("branch.merged")}",
                    SimpleTextAttributes.GRAYED_ATTRIBUTES
                )
                if (layer.isQueued) append(
                    "  ${GhStackBundle.message("branch.queued")}",
                    SimpleTextAttributes.GRAYED_ATTRIBUTES
                )
            }

            null -> Unit
        }
    }
}
