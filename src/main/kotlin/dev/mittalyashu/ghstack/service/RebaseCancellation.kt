package com.github.mittalyashu.ghstack.service

/**
 * The cancellation state of the rebase currently running. The progress popup and the toolbar's Cancel action both
 * end up here, so the cleanup path polls one signal no matter how the user cancelled.
 */
internal class RebaseCancellation(
    private val cancelIndicator: () -> Unit,
    private val isIndicatorCancelled: () -> Boolean,
) {
    @Volatile
    var cancelRequested = false
        private set

    val isCancelled: Boolean get() = cancelRequested || isIndicatorCancelled()

    fun cancel() {
        cancelRequested = true
        cancelIndicator()
    }
}
