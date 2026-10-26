package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckoutTargetTest {
    @Test
    fun nonCurrentLayerIsCheckoutTarget() {
        assertEquals("feature-b", checkoutTargetFor(StackLayer(name = "feature-b")))
    }

    @Test
    fun currentLayerHasNoTarget() {
        assertNull(checkoutTargetFor(StackLayer(name = "feature-b", isCurrent = true)))
    }

    @Test
    fun nothingSelectedHasNoTarget() {
        assertNull(checkoutTargetFor(null))
    }
}
