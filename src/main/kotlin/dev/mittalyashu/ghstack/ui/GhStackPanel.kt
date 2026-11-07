package com.github.mittalyashu.ghstack.ui

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
import com.github.mittalyashu.ghstack.service.RepositoryChoice
import com.github.mittalyashu.ghstack.service.RepositorySelectionListener
import com.github.mittalyashu.ghstack.service.RerereAction
import com.github.mittalyashu.ghstack.service.decideRebaseAction
import com.github.mittalyashu.ghstack.service.decideRerereAction
import com.github.mittalyashu.ghstack.settings.GhStackSettings
import com.github.mittalyashu.ghstack.settings.StashMode
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.FlowLayout
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
    private val log = Logger.getInstance(GhStackPanel::class.java)
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
    private val repositoryCombo = ComboBox<RepositoryChoice>().apply {
        renderer = RepositoryRenderer()
        addActionListener { onRepositorySelected() }
    }
    private val repositoryRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        add(JBLabel(GhStackBundle.message("panel.repository.label")))
        add(repositoryCombo)
        border = JBUI.Borders.empty(0, 4, 6, 12)
        isVisible = false
    }
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var loading = false
    private var refreshQueued = false
    private var syncingRepository = false
    private lateinit var actionToolbar: ActionToolbar

    init {
        actionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, createActions(), true)
        actionToolbar.targetComponent = this

        add(JPanel(BorderLayout()).apply {
            add(actionToolbar.component, BorderLayout.NORTH)
            add(repositoryRow, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
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
        project.messageBus.connect(this).subscribe(
            VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
            VcsRepositoryMappingListener { scheduleRefresh() },
        )
        project.messageBus.connect(this).subscribe(
            GhStackService.REPOSITORY_SELECTED,
            RepositorySelectionListener {
                if (!syncingRepository) {
                    syncRepositorySelector()
                    scheduleRefresh()
                }
            },
        )

        scheduleRefresh()
    }

    fun component(): JComponent = this

    fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refresh() }, 250)
    }

    /**
     * Rebuilds the repository selector and loads the selected repository's stack. A refresh requested while a
     * load is in flight (or a selection made mid-load) is queued and run once the current load finishes, so a
     * quick repository switch is never dropped.
     */
    private fun refresh() {
        if (Disposer.isDisposed(this)) return
        syncRepositorySelector()
        if (loading) {
            refreshQueued = true
            return
        }
        loading = true
        val root = service.selectedRepository()?.root?.toNioPath()?.normalize()
        status.text = GhStackBundle.message("status.loading")
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GhStackBundle.message("progress.view"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                var result: StackLoadResult? = null
                try {
                    result = service.loadCurrentStack()
                } finally {
                    val loaded = result
                    ApplicationManager.getApplication().invokeLater({
                        loading = false
                        if (!Disposer.isDisposed(this@GhStackPanel)) {
                            val selectionChanged =
                                service.selectedRepository()?.root?.toNioPath()?.normalize() != root
                            // The in-flight result belongs to the repository captured above, so don't show it under
                            // another repository's selector; reload instead.
                            if (!selectionChanged) {
                                if (loaded != null) {
                                    render(loaded)
                                } else {
                                    // The load threw; don't leave the panel stuck on the loading placeholder.
                                    val message = GhStackBundle.message("status.load.failed")
                                    status.text = message
                                    list.emptyText.text = message
                                }
                            }
                            if (refreshQueued || selectionChanged) {
                                refreshQueued = false
                                refresh()
                            }
                        }
                    }, ModalityState.any())
                }
            }
        })
    }

    /** Mirrors the service's repository list and selection into the combo box. */
    private fun syncRepositorySelector() {
        val choices = service.repositoryChoices()
        val selectedRoot = service.selectedRepositoryRoot() ?: choices.firstOrNull()?.root
        syncingRepository = true
        try {
            val current = (0 until repositoryCombo.itemCount).map { repositoryCombo.getItemAt(it) }
            if (current != choices) {
                repositoryCombo.removeAllItems()
                choices.forEach { repositoryCombo.addItem(it) }
            }
            repositoryCombo.selectedItem = choices.firstOrNull { it.root == selectedRoot }
                ?: choices.firstOrNull()
        } finally {
            syncingRepository = false
        }
        repositoryRow.isVisible = choices.size > 1
    }

    /** Applies the user's repository choice to the service and reloads that repository's stack. */
    private fun onRepositorySelected() {
        if (syncingRepository) return
        val choice = repositoryCombo.selectedItem as? RepositoryChoice ?: return
        syncingRepository = true
        try {
            // The service publishes synchronously, so the flag keeps this panel from handling its own event twice.
            service.selectRepository(choice.root)
        } finally {
            syncingRepository = false
        }
        refresh()
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
        actionToolbar.updateActionsAsync()
    }

    private fun stackSummary(view: StackView): String {
        val names = buildList {
            add(view.trunk)
            addAll(view.branches.map { it.name })
        }
        return names.joinToString("  →  ")
    }

    private fun createPopupGroup(): DefaultActionGroup {
        val group = DefaultActionGroup()
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.checkout"),
            null,
            AllIcons.Actions.CheckOut,
        ) {
            override fun actionPerformed(e: AnActionEvent) = checkoutSelected()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = checkoutTargetFor(selectedLayer()) != null
            }
        })
        group.addSeparator()
        group.add(object : DumbAwareAction(
            GhStackBundle.message("panel.rebase.from.here"),
            GhStackBundle.message("panel.rebase.from.here.description"),
            AllIcons.Vcs.Merge,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                selectedLayer()?.let { rebaseFromHere(it) }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedLayer() != null
            }
        })
        return group
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
        val name = AddBranchDialog(project).showAndGetBranchName() ?: return
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
        val name = Messages.showInputDialog(
            project,
            GhStackBundle.message("dialog.init.prompt"),
            GhStackBundle.message("dialog.init.title"),
            Messages.getQuestionIcon(),
            null,
            null,
            null,
            GhStackBundle.message("dialog.init.description"),
        )?.trim().orEmpty()
        if (name.isEmpty()) return
        runTask(GhStackBundle.message("progress.init")) {
            ensureRerere()
            RebaseOutcome(service.initStack(name))
        }
    }

    private fun rebase(vararg flags: String) = runRebase(flags.toList())

    /** Rebases [layer] and every layer above it, without rebasing the layers below it. */
    private fun rebaseFromHere(layer: StackLayer) = runRebase(listOf(layer.name, "--upstack"))

    private fun runRebase(args: List<String>) {
        runTask(GhStackBundle.message("progress.rebase")) {
            val settings = GhStackSettings.getInstance()
            val stash = when (decideRebaseAction(settings.stashMode, service.isDirty())) {
                RebaseAction.RUN -> false
                RebaseAction.STASH -> true
                RebaseAction.PROMPT -> askToStash(settings)
            }
            ensureRerere()
            val flags = args.toTypedArray()
            if (stash) service.rebaseWithStash(*flags) else RebaseOutcome(service.rebase(*flags))
        }
    }

    /**
     * Ensures git rerere is available before a stack command that can conflict. Idempotent, so it is safe to
     * call from both [initStack] and [rebase]; it no-ops once rerere is enabled or was declined. Mirrors
     * `gh stack`'s own `ensureRerere`, which cannot run for plugin-driven commands because the CLI is invoked
     * non-interactively. Call from a background thread.
     */
    private fun ensureRerere() {
        val action = decideRerereAction(
            enabled = service.isRerereEnabled(),
            declined = service.isRerereDeclined(),
            interactive = !ApplicationManager.getApplication().isHeadlessEnvironment,
        )
        if (action == RerereAction.SKIP) return
        when (askToEnableRerere()) {
            true -> if (!service.enableRerere()) {
                log.warn("Could not enable git rerere; continuing without it")
            }

            false -> if (!service.declineRerere()) {
                log.warn("Could not record the declined git rerere prompt; it may be asked again")
            }

            null -> Unit
        }
    }

    /**
     * Returns whether to enable rerere, or null when the prompt was dismissed without an answer. A dismissal
     * is deliberately not a decline, so an accidental Esc doesn't suppress the prompt forever (matching
     * `gh stack`, which only persists an explicit "no"). Call from a background thread.
     */
    private fun askToEnableRerere(): Boolean? {
        var answer: Boolean? = null
        ApplicationManager.getApplication().invokeAndWait({
            val choice = MessageDialogBuilder
                .yesNoCancel(
                    GhStackBundle.message("dialog.rerere.title"),
                    GhStackBundle.message("dialog.rerere.message"),
                )
                .yesText(GhStackBundle.message("dialog.rerere.yes"))
                .noText(GhStackBundle.message("dialog.rerere.no"))
                .cancelText(GhStackBundle.message("dialog.rerere.cancel"))
                .show(project)
            answer = when (choice) {
                Messages.YES -> true
                Messages.NO -> false
                else -> null
            }
        }, ModalityState.defaultModalityState())
        return answer
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
        // Pin the repository for the whole task so switching to another repository mid-rebase cannot split the
        // stash, the gh command, and the stash restore across different repositories.
        val root = service.selectedRepository()?.root?.toNioPath()?.normalize()
        repositoryCombo.isEnabled = false
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val outcome = service.withPinnedRepository(root) {
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
                        outcome
                    }
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
                } finally {
                    ApplicationManager.getApplication().invokeLater({
                        repositoryCombo.isEnabled = true
                    }, ModalityState.any())
                }
            }
        })
    }

    override fun dispose() {}
}

private sealed class StackRow {
    data class Trunk(val name: String) : StackRow()
    data class Layer(val layer: StackLayer) : StackRow()
}

private class RepositoryRenderer : ColoredListCellRenderer<RepositoryChoice>() {
    override fun customizeCellRenderer(
        list: JList<out RepositoryChoice>,
        value: RepositoryChoice?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        ipad = JBUI.insets(2, 4)
        value ?: return
        icon = if (value.isSubmodule) AllIcons.Nodes.Module else AllIcons.Vcs.Branch
        append(value.label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (value.isSubmodule) {
            append("  ${GhStackBundle.message("panel.repository.submodule")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
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
