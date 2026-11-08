package com.github.mittalyashu.ghstack.ui

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.project.DumbAwareAction
import javax.swing.Icon

/**
 * A [DumbAwareAction] that shows [description] and the [command] it runs as a tooltip wherever it appears.
 *
 * Toolbar buttons read the plain description through [TooltipDescriptionProvider]; popup items read the
 * HTML [ActionUtil.TOOLTIP_TEXT] from the template presentation. Both are derived from the same arguments,
 * so a new action only needs to pass its description and command to get a tooltip.
 */
internal abstract class TooltipAction(
    text: String,
    description: String?,
    command: String? = null,
    icon: Icon? = null,
) : DumbAwareAction(
    text,
    description?.let { plainTooltip(it, command) },
    icon,
), TooltipDescriptionProvider {
    init {
        templatePresentation.putClientProperty(
            ActionUtil.TOOLTIP_TEXT,
            description?.let { htmlTooltip(it, command) },
        )
    }
}

/** A popup [DefaultActionGroup] that shows its description and command as a tooltip on the toolbar button. */
internal class TooltipActionGroup(
    text: String,
    description: String,
    command: String? = null,
) : DefaultActionGroup(text, true), TooltipDescriptionProvider {
    init {
        templatePresentation.description = plainTooltip(description, command)
        templatePresentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, htmlTooltip(description, command))
    }
}
