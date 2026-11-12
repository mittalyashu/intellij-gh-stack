package com.github.mittalyashu.ghstack.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebaseCancellationTest {
    @Test
    fun `a fresh rebase is not cancelled`() {
        var indicatorCancelled = false
        val cancellation = RebaseCancellation({ indicatorCancelled = true }, { indicatorCancelled })

        assertFalse(cancellation.isCancelled)
        assertFalse(cancellation.cancelRequested)
    }

    @Test
    fun `cancelling from the toolbar cancels both the handle and the indicator`() {
        var indicatorCancelled = false
        val cancellation = RebaseCancellation({ indicatorCancelled = true }, { indicatorCancelled })

        cancellation.cancel()

        assertTrue(cancellation.cancelRequested)
        assertTrue(indicatorCancelled)
        assertTrue(cancellation.isCancelled)
    }

    @Test
    fun `cancelling from the progress popup is observed without the toolbar`() {
        // The progress popup calls indicator.cancel() directly, so the CLI and the toolbar must see that signal.
        var indicatorCancelled = false
        val cancellation = RebaseCancellation({ indicatorCancelled = true }, { indicatorCancelled })

        indicatorCancelled = true

        assertTrue(cancellation.isCancelled)
        assertFalse(cancellation.cancelRequested)
    }
}
