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

    @Test
    fun nonCurrentLayerDescriptionShowsWhatItDoes() {
        assertEquals("Check out this branch.", checkoutDescriptionFor(StackLayer(name = "feature-b")))
    }

    @Test
    fun nonCurrentLayerCommandShowsTheCommand() {
        assertEquals("gh stack checkout feature-b", checkoutCommandFor(StackLayer(name = "feature-b")))
    }

    @Test
    fun currentLayerHasNoDescriptionOrCommand() {
        val layer = StackLayer(name = "feature-b", isCurrent = true)
        assertNull(checkoutDescriptionFor(layer))
        assertNull(checkoutCommandFor(layer))
    }

    @Test
    fun nothingSelectedHasNoDescriptionOrCommand() {
        assertNull(checkoutDescriptionFor(null))
        assertNull(checkoutCommandFor(null))
    }
}
