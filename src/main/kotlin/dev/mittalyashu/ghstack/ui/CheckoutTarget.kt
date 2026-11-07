package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.model.StackLayer

/**
 * The branch to check out for the selected row, or null when there is nothing to check out.
 * The current layer is excluded, matching the native Git panel where "Check out" is disabled on the checked-out branch.
 */
internal fun checkoutTargetFor(layer: StackLayer?): String? =
    layer?.takeIf { !it.isCurrent }?.name

/** What the selected layer's "Checkout" action does, or null when there is nothing to check out. */
internal fun checkoutDescriptionFor(layer: StackLayer?): String? =
    checkoutTargetFor(layer)?.let { GhStackBundle.message("panel.checkout.description") }

/** The `gh stack checkout` command the selected layer's "Checkout" action runs, or null when there is nothing to check out. */
internal fun checkoutCommandFor(layer: StackLayer?): String? =
    checkoutTargetFor(layer)?.let { GhStackBundle.message("panel.checkout.command", it) }
