package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import javax.swing.JPopupMenu

/**
 * The plain-text tooltip: [description] followed by the command it runs, if any.
 * Used where the platform renders a tooltip as plain text, such as the status bar.
 */
internal fun plainTooltip(description: String, command: String?): String =
    if (command == null) description else "$description Runs: $command"

/**
 * The HTML tooltip: [description] followed by [command] in a monospace code block.
 * Tooltips render HTML, so the command reads like the shell line it represents.
 */
internal fun htmlTooltip(description: String, command: String?): String = buildString {
    append("<html>")
    append(StringUtil.escapeXmlEntities(description))
    if (command != null) {
        val bgColor = JBColor.namedColor(
            "ToolTip.infoBackground",
            JBColor(0xF5F5F5, 0x4d4e52)
        )
        val hexColor = ColorUtil.toHtmlColor(bgColor)

        append("<br><pre style=\"background-color:$hexColor;padding:2px;\">")
        append(StringUtil.escapeXmlEntities(command))
        append("</pre>")
    }
    append("</html>")
}

/**
 * Shows each action's tooltip on the popup's menu items. The platform's popup menu does not turn
 * `Presentation.description` into a menu-item tooltip by itself.
 */
internal fun installActionTooltips(menu: JPopupMenu) {
    for (component in menu.components) {
        val item = component as? ActionMenuItem ?: continue
        val presentation = item.anAction.templatePresentation
        item.toolTipText = presentation.getClientProperty(ActionUtil.TOOLTIP_TEXT) ?: presentation.description
    }
}
