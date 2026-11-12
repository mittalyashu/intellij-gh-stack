package com.github.mittalyashu.ghstack.ui

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.OnePixelSplitter
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
import com.github.mittalyashu.ghstack.model.TrackedStack
import com.github.mittalyashu.ghstack.model.TrackedStacks
import com.github.mittalyashu.ghstack.service.GhStackService
import com.github.mittalyashu.ghstack.service.GitService
import com.github.mittalyashu.ghstack.service.RebaseOutcome
import com.github.mittalyashu.ghstack.service.RepositoryChoice
import com.github.mittalyashu.ghstack.service.RepositorySelectionListener
import com.github.mittalyashu.ghstack.service.RerereAction
import com.github.mittalyashu.ghstack.service.StashAction
import com.github.mittalyashu.ghstack.service.decideRerereAction
import com.github.mittalyashu.ghstack.service.decideStashAction
import com.github.mittalyashu.ghstack.service.stashChoiceFromExitCode
import com.github.mittalyashu.ghstack.settings.GhStackSettings
import com.github.mittalyashu.ghstack.settings.StashMode
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Point
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

class GhStackPanel(private val project: Project) : JPanel(BorderLayout()), com.intellij.openapi.Disposable {
    private val ghstack = project.getService(GhStackService::class.java)
    private val git = project.getService(GitService::class.java)
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
    private val repositoryCombo = ComboBox<RepositoryOption>().apply {
        renderer = RepositoryRenderer()
        addActionListener { onRepositorySelected() }
    }
    private val repositoryRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        add(JBLabel(GhStackBundle.message("panel.repository.label")))
        add(repositoryCombo)
        border = JBUI.Borders.empty(0, 4, 6, 12)
        isVisible = false
    }
    private val allStacksPanel = AllStacksPanel(
        onSwitch = { switchToStack(it) },
        onPrune = { pruneStack(it) },
    )
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var loading = false
    private var refreshQueued = false
    private var syncingRepository = false
    private var runningTasks = 0
    private var displayedRoot: Path? = null
    private var currentView: StackView? = null
    private var localBranchNames: Set<String> = emptySet()

    /** Set in [dispose]; guards callbacks that were queued before the panel was disposed. */
    @Volatile
    private var disposed = false

    private lateinit var actionToolbar: ActionToolbar
    private lateinit var rebaseToolbar: ActionToolbar
    private val copyBranchNameAction = object : TooltipAction(
        GhStackBundle.message("panel.copy.branch"),
        GhStackBundle.message("panel.copy.branch.description"),
        icon = AllIcons.Actions.Copy,
    ) {
        override fun actionPerformed(e: AnActionEvent) = copyBranchName(list.selectedValue)
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = list.selectedValue != null
        }
    }

    init {
        actionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, createActions(), true)
        actionToolbar.targetComponent = this
        rebaseToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, createRebaseToolbarActions(), true)
            .apply { targetComponent = this@GhStackPanel }

        add(JPanel(BorderLayout()).apply {
            add(actionToolbar.component, BorderLayout.NORTH)
            add(repositoryRow, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(OnePixelSplitter(false, "ghstack.panel.splitter", 0.35f).apply {
            firstComponent = allStacksPanel.component()
            secondComponent = createCurrentStackColumn()
        }, BorderLayout.CENTER)

        list.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val point = Point(x, y)
                val index = list.locationToIndex(point)
                if (index < 0 || list.getCellBounds(index, index)?.contains(point) != true) return
                // Right-clicking a row selects it first, like the native Git panel, so the popup acts on that row.
                list.selectedIndex = index
                showLayerPopup(comp, x, y)
            }
        })
        copyBranchNameAction.registerCustomShortcutSet(CommonShortcuts.getCopy(), list)

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
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) = followEditorRepository()
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) = followEditorRepository()
            },
        )

        scheduleRefresh()
    }

    fun component(): JComponent = this

    /** The current stack column: the stack list with its status summary below it. */
    private fun createCurrentStackColumn(): JComponent = JPanel(BorderLayout()).apply {
        add(JPanel(BorderLayout()).apply {
            add(JBLabel(GhStackBundle.message("panel.current.stack.title")).apply {
                border = JBUI.Borders.empty(4, 8, 4, 4)
            }, BorderLayout.WEST)
            add(rebaseToolbar.component, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refresh() }, 250)
    }

    /**
     * Follows the repository of the newly selected editor file while the selector is in auto mode. A switch
     * within the same repository, or to a file outside every repository, does not reload the stack. When the
     * repository does change, its predecessor's rows are dropped before the debounced reload, so a click in that
     * window cannot run a row's command against the new repository.
     */
    private fun followEditorRepository() {
        if (disposed || !ghstack.isAutoRepository()) return
        val before = ghstack.selectedRepository()?.root?.toNioPath()?.normalize()
        ghstack.syncAutoRepository()
        if (ghstack.selectedRepository()?.root?.toNioPath()?.normalize() == before) return
        invalidateRows()
        scheduleRefresh()
    }

    /** Drops rows that belong to a repository the panel no longer targets, so they can't act on the new one. */
    private fun invalidateRows() {
        displayedRoot = null
        currentView = null
        localBranchNames = emptySet()
        listModel.clear()
        status.text = GhStackBundle.message("status.loading")
        list.emptyText.text = GhStackBundle.message("status.loading")
        allStacksPanel.clear()
    }

    /**
     * Rebuilds the repository selector and loads the selected repository's stack. A refresh requested while a
     * load is in flight (or a selection made mid-load) is queued and run once the current load finishes, so a
     * quick repository switch is never dropped.
     */
    private fun refresh() {
        if (disposed) return
        // Auto mode follows the editor, so re-resolve the context before snapshotting the root; the listener only
        // sees changes made after the panel was created.
        ghstack.syncAutoRepository()
        syncRepositorySelector()
        val root = ghstack.selectedRepository()?.root?.toNioPath()?.normalize()
        // The rows belong to the repository that produced them; blank them as soon as the selector points elsewhere
        // so a stale row can't be checked out in the newly selected repository.
        if (displayedRoot != root) invalidateRows()
        if (loading) {
            refreshQueued = true
            return
        }
        loading = true
        status.text = GhStackBundle.message("status.loading")
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GhStackBundle.message("progress.view"),
            false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                var result: StackLoadResult? = null
                var tracked: Result<TrackedStacks>? = null
                var localBranches: Set<String> = emptySet()
                try {
                    // Pin the captured root so a repository switch mid-load cannot mix this result with another
                    // repository's catalog or stack (the completion guard below only compares the final selection).
                    ghstack.withPinnedRepository(root) {
                        // Load the tracked catalog first: it is a local read and shouldn't wait on `gh stack view`.
                        tracked = runCatching { ghstack.loadTrackedStacks() }
                            .getOrElse { Result.failure(it) }
                        localBranches = ghstack.selectedRepository()?.let(git::localBranchNames) ?: emptySet()
                        result = ghstack.loadCurrentStack()
                    }
                } finally {
                    val loaded = result
                    val trackedResult = tracked
                    val branches = localBranches
                    ApplicationManager.getApplication().invokeLater({
                        loading = false
                        if (!disposed) {
                            val selectionChanged =
                                ghstack.selectedRepository()?.root?.toNioPath()?.normalize() != root
                            // The in-flight result belongs to the repository captured above, so don't show it under
                            // another repository's selector; reload instead.
                            if (!selectionChanged) {
                                render(loaded, trackedResult, branches)
                                displayedRoot = root
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

    /** Mirrors the ghstack service's repository list and selection into the combo box. */
    private fun syncRepositorySelector() {
        val choices = ghstack.repositoryChoices()
        val options = repositoryOptions(choices)
        val selectedRoot = ghstack.selectedRepositoryRoot()
        val selected = when {
            selectedRoot == null -> RepositoryOption.Auto
            // A pinned repository that disappeared falls back to the same entry the service resolves.
            else -> options.firstOrNull { it is RepositoryOption.Fixed && it.choice.root == selectedRoot }
                ?: choices.firstOrNull()?.let { RepositoryOption.Fixed(it) }
                ?: RepositoryOption.Auto
        }
        syncingRepository = true
        try {
            val current = (0 until repositoryCombo.itemCount).map { repositoryCombo.getItemAt(it) }
            if (current != options) {
                repositoryCombo.removeAllItems()
                options.forEach { repositoryCombo.addItem(it) }
            }
            repositoryCombo.selectedItem = selected
        } finally {
            syncingRepository = false
        }
        repositoryRow.isVisible = choices.size > 1
    }

    private fun repositoryOptions(choices: List<RepositoryChoice>): List<RepositoryOption> = buildList {
        add(RepositoryOption.Auto)
        choices.forEach { add(RepositoryOption.Fixed(it)) }
    }

    /** Applies the user's repository choice to the ghstack service and reloads that repository's stack. */
    private fun onRepositorySelected() {
        if (syncingRepository) return
        val option = repositoryCombo.selectedItem ?: return
        val root = (option as? RepositoryOption.Fixed)?.choice?.root
        syncingRepository = true
        try {
            // The ghstack service publishes synchronously, so the flag keeps this panel from handling its own event twice.
            ghstack.selectRepository(root)
        } finally {
            syncingRepository = false
        }
        refresh()
    }

    private fun render(result: StackLoadResult?, tracked: Result<TrackedStacks>?, localBranches: Set<String>) {
        listModel.clear()
        localBranchNames = localBranches
        var view: StackView? = null
        when (result) {
            null -> {
                // The load threw; don't leave the panel stuck on the loading placeholder.
                val message = GhStackBundle.message("status.load.failed")
                status.text = message
                list.emptyText.text = message
            }

            is StackLoadResult.Loaded -> {
                view = result.view
                stackRows(view).forEach(listModel::addElement)
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
        currentView = view
        allStacksPanel.update(tracked, view, localBranches)
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
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.checkout"),
            checkoutDescriptionFor(selectedLayer()),
            checkoutCommandFor(selectedLayer()),
            AllIcons.Actions.CheckOut,
        ) {
            override fun actionPerformed(e: AnActionEvent) = checkoutSelected()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = checkoutTargetFor(selectedLayer()) != null
            }
        })
        group.addSeparator()
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.rebase.from.here"),
            GhStackBundle.message("panel.rebase.from.here.description"),
            selectedLayer()?.let { GhStackBundle.message("panel.rebase.from.here.command", it.name) },
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
        group.addSeparator()
        group.add(copyBranchNameAction)
        group.addSeparator()
        val layer = selectedLayer()
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.rename.branch"),
            renameBranchDescription(layer),
            icon = AllIcons.Actions.Edit,
        ) {
            override fun actionPerformed(e: AnActionEvent) = renameSelectedBranch()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                // Renaming a branch with a pull request closes that pull request on GitHub, so the menu item is
                // disabled and its description explains the consequence instead.
                e.presentation.isEnabled = layer != null && layer.pr == null
            }
        })
        return group
    }

    private fun createActions(): DefaultActionGroup {
        val group = DefaultActionGroup()
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.refresh"),
            GhStackBundle.message("panel.refresh.description"),
            GhStackBundle.message("panel.refresh.command"),
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) = refresh()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.add.branch"),
            GhStackBundle.message("panel.add.branch.description"),
            GhStackBundle.message("panel.add.branch.command"),
            AllIcons.General.Add,
        ) {
            override fun actionPerformed(e: AnActionEvent) = addBranch()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.init.stack"),
            GhStackBundle.message("panel.init.stack.description"),
            GhStackBundle.message("panel.init.stack.command"),
            AllIcons.Actions.NewFolder,
        ) {
            override fun actionPerformed(e: AnActionEvent) = initStack()
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.rebase.continue"),
            GhStackBundle.message("panel.rebase.continue.description"),
            GhStackBundle.message("panel.rebase.continue.command"),
            AllIcons.Actions.Resume,
        ) {
            override fun actionPerformed(e: AnActionEvent) = rebaseContinue()
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(e: AnActionEvent) {
                e.presentation.isVisible = ghstack.selectedRepository()?.let(git::isRebaseInProgress) == true
            }
        })
        group.add(object : TooltipAction(
            GhStackBundle.message("panel.rebase.abort"),
            GhStackBundle.message("panel.rebase.abort.description"),
            GhStackBundle.message("panel.rebase.abort.command"),
            AllIcons.Actions.Cancel,
        ) {
            override fun actionPerformed(e: AnActionEvent) = rebaseAbort()
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(e: AnActionEvent) {
                e.presentation.isVisible = ghstack.selectedRepository()?.let(git::isRebaseInProgress) == true
            }
        })
        return group
    }

    /**
     * The rebase group wrapped in a parent group. A toolbar flattens its root group into separate buttons, so the
     * wrapper is what keeps the rebase variants behind a single dropdown button.
     */
    private fun createRebaseToolbarActions(): DefaultActionGroup = DefaultActionGroup().apply {
        add(createRebaseGroup())
    }

    private fun createRebaseGroup(): DefaultActionGroup {
        fun variant(messageKey: String, vararg flags: String) = object : TooltipAction(
            GhStackBundle.message(messageKey),
            GhStackBundle.message("$messageKey.description"),
            GhStackBundle.message("$messageKey.command"),
        ) {
            override fun actionPerformed(e: AnActionEvent) = rebase(*flags)
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }

        return TooltipActionGroup(
            GhStackBundle.message("panel.rebase"),
            GhStackBundle.message("panel.rebase.description"),
            GhStackBundle.message("panel.rebase.command"),
        ).apply {
            add(variant("panel.rebase.stack"))
            add(variant("panel.rebase.upstack", "--upstack"))
            add(variant("panel.rebase.downstack", "--downstack"))
            add(variant("panel.rebase.no.trunk", "--no-trunk"))
            templatePresentation.icon = AllIcons.Vcs.Merge
            templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, false)
        }
    }

    private fun selectedLayer(): StackLayer? {
        return (list.selectedValue as? StackRow.Layer)?.layer
    }

    private fun showLayerPopup(comp: Component, x: Int, y: Int) {
        val popup = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, createPopupGroup())
        val menu = popup.component
        menu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
                // The platform's popup menu does not turn action descriptions into menu-item tooltips by itself.
                installActionTooltips(menu)
            }

            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = Unit

            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
        })
        menu.show(comp, x, y)
    }

    private fun addBranch() {
        val name = AddBranchDialog(project).showAndGetBranchName() ?: return
        runGh(GhStackBundle.message("progress.add")) {
            ghstack.addBranch(name)
        }
    }

    private fun checkoutSelected() {
        val target = checkoutTargetFor(selectedLayer()) ?: return
        runGh(GhStackBundle.message("progress.checkout")) {
            ghstack.checkout(target)
        }
    }

    private fun renameSelectedBranch() {
        val layer = selectedLayer() ?: return
        if (layer.pr != null) return
        val view = currentView ?: return
        val branches = view.branches.map { it.name }
        val newName = RenameBranchDialog(
            project = project,
            oldName = layer.name,
            trunk = view.trunk,
            branches = branches,
            existingBranches = localBranchNames,
        ).showAndGetBranchName() ?: return
        runTask(GhStackBundle.message("progress.rename")) {
            val stash = stashBefore(StashPrompt.RENAME) ?: return@runTask null
            if (stash) {
                ghstack.renameLayerBranchWithStash(layer.name, newName, view.trunk, branches)
            } else {
                RebaseOutcome(ghstack.renameLayerBranch(layer.name, newName, view.trunk, branches))
            }
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
            RebaseOutcome(ghstack.initStack(name))
        }
    }

    private fun rebase(vararg flags: String) = runRebase(flags.toList())

    /** Rebases [layer] and every layer above it, without rebasing the layers below it. */
    private fun rebaseFromHere(layer: StackLayer) = runRebase(listOf(layer.name, "--upstack"))

    private fun runRebase(args: List<String>) {
        runTask(GhStackBundle.message("progress.rebase")) {
            val stash = stashBefore(StashPrompt.REBASE) ?: return@runTask null
            ensureRerere()
            val flags = args.toTypedArray()
            if (stash) ghstack.rebaseWithStash(*flags) else RebaseOutcome(ghstack.rebase(*flags))
        }
    }

    /**
     * Whether to stash local changes before a stack operation, following the configured [StashMode]. Returns null
     * when the user cancels the prompt. Call from a background thread.
     */
    private fun stashBefore(prompt: StashPrompt): Boolean? {
        val settings = GhStackSettings.getInstance()
        val dirty = ghstack.selectedRepository()?.let(git::isDirty) == true
        return when (decideStashAction(settings.stashMode, dirty)) {
            StashAction.RUN -> false
            StashAction.STASH -> true
            StashAction.PROMPT -> askToStash(settings, prompt)
        }
    }

    /**
     * Ensures git rerere is available before a stack command that can conflict. Idempotent, so it is safe to
     * call from both [initStack] and [rebase]; it no-ops once rerere is enabled or was declined. Mirrors
     * `gh stack`'s own `ensureRerere`, which cannot run for plugin-driven commands because the CLI is invoked
     * non-interactively. Call from a background thread.
     */
    private fun ensureRerere() {
        val repository = ghstack.selectedRepository() ?: return
        val action = decideRerereAction(
            enabled = git.isRerereEnabled(repository),
            declined = git.isRerereDeclined(repository),
            interactive = !ApplicationManager.getApplication().isHeadlessEnvironment,
        )
        if (action == RerereAction.SKIP) return
        when (askToEnableRerere()) {
            true -> if (!git.enableRerere(repository)) {
                log.warn("Could not enable git rerere; continuing without it")
            }

            false -> if (!git.declineRerere(repository)) {
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

    /**
     * Returns whether to stash, or null when the operation was cancelled. Persists the choice when "remember" is
     * ticked. Call from a background thread.
     */
    private fun askToStash(settings: GhStackSettings, prompt: StashPrompt): Boolean? {
        var answer: Boolean? = null
        ApplicationManager.getApplication().invokeAndWait({
            var remember = false
            val choice = MessageDialogBuilder
                .yesNoCancel(
                    GhStackBundle.message("dialog.stash.title"),
                    GhStackBundle.message(prompt.messageKey),
                )
                .yesText(GhStackBundle.message(prompt.yesKey))
                .noText(GhStackBundle.message("dialog.stash.no"))
                .cancelText(GhStackBundle.message("dialog.stash.cancel"))
                .doNotAsk(object : DoNotAskOption.Adapter() {
                    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                        remember = isSelected
                    }

                    override fun getDoNotShowMessage() = GhStackBundle.message("dialog.stash.remember")
                })
                .show(project)
            answer = stashChoiceFromExitCode(choice)
            // A Cancel press still reports the "remember" checkbox, but cancelling must not persist a stash mode.
            if (answer != null && remember) {
                settings.stashMode = if (answer == true) StashMode.ALWAYS else StashMode.NEVER
            }
        }, ModalityState.defaultModalityState())
        return answer
    }

    private fun rebaseContinue() {
        runGh(GhStackBundle.message("progress.rebase.continue")) {
            ghstack.rebaseContinue()
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
            ghstack.rebaseAbort()
        }
    }

    private fun switchToStack(stack: TrackedStack) {
        runGh(GhStackBundle.message("progress.checkout")) {
            ghstack.checkout(stack.checkoutTarget())
        }
    }

    private fun pruneStack(stack: TrackedStack) {
        val confirmed = Messages.showYesNoDialog(
            project,
            GhStackBundle.message("dialog.prune.confirm", stack.displayLabel()),
            GhStackBundle.message("dialog.prune.title"),
            Messages.getWarningIcon(),
        ) == Messages.YES
        if (!confirmed) return
        runGh(GhStackBundle.message("progress.prune")) {
            ghstack.pruneLocalStack(stack)
        }
    }

    private fun runGh(title: String, command: () -> GhResult) = runTask(title) { RebaseOutcome(command()) }

    private fun runTask(title: String, command: () -> RebaseOutcome?) {
        // Auto mode targets whatever the editor shows right now, even if no refresh ran since the last tab switch.
        ghstack.syncAutoRepository()
        // Pin the repository for the whole task so switching to another repository mid-rebase cannot split the
        // stash, the gh command, and the stash restore across different repositories.
        val root = ghstack.selectedRepository()?.root?.toNioPath()?.normalize()
        runningTasks++
        repositoryCombo.isEnabled = false
        allStacksPanel.setBusy(true)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val outcome = ghstack.withPinnedRepository(root) {
                        val outcome = command()
                        outcome?.stashToRestore?.let { stash ->
                            val repository = ghstack.selectedRepository()
                            if (repository == null || !git.restoreStash(repository, stash)) {
                                ApplicationManager.getApplication().invokeLater({
                                    Messages.showErrorDialog(
                                        project,
                                        GhStackBundle.message("stash.restore.missing", stash.message),
                                        GhStackBundle.message("notification.error"),
                                    )
                                }, ModalityState.any())
                            }
                        }
                        ghstack.selectedRepository()?.let(git::refresh)
                        outcome
                    }
                    ApplicationManager.getApplication().invokeLater({
                        if (outcome != null && !outcome.result.succeeded) {
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
                        runningTasks--
                        repositoryCombo.isEnabled = runningTasks == 0
                        allStacksPanel.setBusy(runningTasks > 0)
                    }, ModalityState.any())
                }
            }
        })
    }

    override fun dispose() {
        disposed = true
    }
}

/** The stash prompt wording for a stack operation. */
private enum class StashPrompt(val messageKey: String, val yesKey: String) {
    REBASE("dialog.stash.message", "dialog.stash.yes"),
    RENAME("dialog.stash.rename.message", "dialog.stash.rename.yes"),
}

/** An entry in the panel's repository selector: auto (follow the current file) or a concrete repository. */
private sealed interface RepositoryOption {
    data object Auto : RepositoryOption
    data class Fixed(val choice: RepositoryChoice) : RepositoryOption
}

private class RepositoryRenderer : ColoredListCellRenderer<RepositoryOption>() {
    override fun customizeCellRenderer(
        list: JList<out RepositoryOption>,
        value: RepositoryOption?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        ipad = JBUI.insets(2, 4)
        when (value) {
            null -> return
            RepositoryOption.Auto -> {
                icon = AllIcons.General.AutoscrollToSource
                append(GhStackBundle.message("panel.repository.auto"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

            is RepositoryOption.Fixed -> {
                icon = if (value.choice.isSubmodule) AllIcons.Nodes.Module else AllIcons.Vcs.Branch
                append(value.choice.label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (value.choice.isSubmodule) {
                    append(
                        "  ${GhStackBundle.message("panel.repository.submodule")}",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES,
                    )
                }
            }
        }
    }
}
