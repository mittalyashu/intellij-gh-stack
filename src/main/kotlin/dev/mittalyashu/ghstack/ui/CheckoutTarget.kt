package dev.mittalyashu.ghstack.ui

import dev.mittalyashu.ghstack.model.StackLayer

/**
 * The branch to check out for the selected row, or null when there is nothing to check out.
 * The current layer is excluded, matching the native Git panel where "Check out" is disabled on the checked-out branch.
 */
internal fun checkoutTargetFor(layer: StackLayer?): String? =
    layer?.takeIf { !it.isCurrent }?.name
