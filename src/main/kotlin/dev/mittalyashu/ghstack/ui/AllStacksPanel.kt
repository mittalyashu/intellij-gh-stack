package com.github.mittalyashu.ghstack.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.model.StackView
import com.github.mittalyashu.ghstack.model.TrackedStack
import com.github.mittalyashu.ghstack.model.TrackedStacks
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * The "All stacks" column of the Stack panel: every stack tracked in the selected repository, with the checked-out
 * stack highlighted. Double-click, Enter, or the header action switches to the selected stack; the checkout is
 * delegated to [onSwitch] because the panel owns the progress task and error reporting.
 */
internal class AllStacksPanel(private val onSwitch: (TrackedStack) -> Unit) : JPanel(BorderLayout()) {
    private val model = DefaultListModel<TrackedStack>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = TrackedStackRenderer()
        emptyText.text = GhStackBundle.message("panel.all.stacks.loading")
    }
    private var currentStack: TrackedStack? = null
    private var busy = false

    private val switchAction = object : TooltipAction(
        GhStackBundle.message("panel.switch.stack"),
        GhStackBundle.message("panel.switch.stack.description"),
        GhStackBundle.message("panel.switch.stack.command"),
        AllIcons.Actions.CheckOut,
    ) {
        override fun actionPerformed(e: AnActionEvent) = switchSelected()
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !busy && list.selectedValue != null
        }
    }
    private val switchToolbar: ActionToolbar = ActionManager.getInstance()
        .createActionToolbar(ActionPlaces.TOOLBAR, DefaultActionGroup(switchAction), true)
        .apply { targetComponent = list }

    init {
        add(JPanel(BorderLayout()).apply {
            add(JBLabel(GhStackBundle.message("panel.all.stacks.title")).apply {
                border = JBUI.Borders.empty(4, 8, 4, 4)
            }, BorderLayout.WEST)
            add(switchToolbar.component, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)

        list.addListSelectionListener { switchToolbar.updateActionsAsync() }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    activateRowAt(e.point)
                }
            }
        })
        list.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val point = Point(x, y)
                val index = list.locationToIndex(point)
                if (index < 0 || list.getCellBounds(index, index)?.contains(point) != true) return
                // Right-clicking a row selects it first, like the native Git panel, so the popup acts on that row.
                list.selectedIndex = index
                val popup = ActionManager.getInstance()
                    .createActionPopupMenu(ActionPlaces.POPUP, DefaultActionGroup(switchAction))
                popup.component.addPopupMenuListener(object : PopupMenuListener {
                    override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
                        installActionTooltips(popup.component)
                    }

                    override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = Unit

                    override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
                })
                popup.component.show(comp, x, y)
            }
        })
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) switchSelected()
            }
        })
    }

    fun component(): JComponent = this

    /** Blanks the column while a stack list for another repository is loading, so stale rows can't be switched to. */
    fun clear() {
        currentStack = null
        model.clear()
        list.emptyText.text = GhStackBundle.message("panel.all.stacks.loading")
        switchToolbar.updateActionsAsync()
    }

    /** Disables switching while a panel command is running, so two checkouts can't race each other. */
    fun setBusy(busy: Boolean) {
        if (this.busy == busy) return
        this.busy = busy
        list.isEnabled = !busy
        switchToolbar.updateActionsAsync()
    }

    /**
     * Replaces the list with [tracked]. [view] is the loaded current stack, used only to highlight which tracked
     * stack is checked out; a failed or missing load leaves no stack highlighted.
     */
    fun update(tracked: Result<TrackedStacks>?, view: StackView?) {
        val previous = list.selectedValue
        val stacks = tracked?.getOrNull()?.stacks.orEmpty()
        currentStack = currentTrackedStack(stacks, view)
        model.clear()
        stacks.forEach { model.addElement(it) }
        list.emptyText.text = tracked?.exceptionOrNull()?.message
            ?: GhStackBundle.message("panel.all.stacks.empty")
        // Keep the user's row across a refresh when it still exists, otherwise fall back to the checked-out stack.
        val selection = previous?.let { selected -> stacks.firstOrNull { it == selected } }
            ?: currentStack
            ?: stacks.firstOrNull()
        list.setSelectedValue(selection, true)
        list.repaint()
        switchToolbar.updateActionsAsync()
    }

    private fun activateRowAt(point: Point) {
        val index = list.locationToIndex(point)
        if (index < 0 || list.getCellBounds(index, index)?.contains(point) != true) return
        list.selectedIndex = index
        switchSelected()
    }

    private fun switchSelected() {
        if (busy) return
        list.selectedValue?.let(onSwitch)
    }

    private inner class TrackedStackRenderer : ColoredListCellRenderer<TrackedStack>() {
        override fun customizeCellRenderer(
            list: JList<out TrackedStack>,
            value: TrackedStack?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            ipad = JBUI.insets(6, 10)
            value ?: return
            icon = if (value.isLocal) AllIcons.Nodes.Desktop else AllIcons.Vcs.Branch
            val style = if (value == currentStack) {
                SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_BOLD,
                    JBColor.namedColor("Link.activeForeground", JBColor.BLUE),
                )
            } else {
                SimpleTextAttributes.REGULAR_ATTRIBUTES
            }
            append(value.displayLabel(), style)
        }
    }
}

/** The tracked stack that contains [view]'s current branch, or one whose trunk and branches match the view. */
internal fun currentTrackedStack(stacks: List<TrackedStack>, view: StackView?): TrackedStack? {
    view ?: return null
    return stacks.firstOrNull { view.currentBranch in it.branches }
        ?: stacks.firstOrNull {
            it.trunk == view.trunk && it.branches == view.branches.map { layer -> layer.name }
        }
}
