package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.model.StackLayer

/**
 * The branch to check out for the selected row, or null when there is nothing to check out.
 * The current layer is excluded, matching the native Git panel where "Check out" is disabled on the checked-out branch.
 */
internal fun checkoutTargetFor(layer: StackLayer?): String? =
    layer?.takeIf { !it.isCurrent }?.name

/**
 * The hint shown for the selected layer's "Checkout" action: what it does and the `gh stack checkout` command it runs.
 * Null when there is nothing to check out, so the disabled action has no hint.
 */
internal fun checkoutDescriptionFor(layer: StackLayer?): String? =
    checkoutTargetFor(layer)?.let { GhStackBundle.message("panel.checkout.description", it) }
