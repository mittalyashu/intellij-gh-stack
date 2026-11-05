package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.model.StackLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun nonCurrentLayerDescriptionShowsWhatItDoesAndTheCommand() {
        val description = checkoutDescriptionFor(StackLayer(name = "feature-b")).orEmpty()
        assertTrue(description.contains("Check out this branch"))
        assertTrue(description.contains("gh stack checkout feature-b"))
    }

    @Test
    fun currentLayerHasNoDescription() {
        assertNull(checkoutDescriptionFor(StackLayer(name = "feature-b", isCurrent = true)))
    }

    @Test
    fun nothingSelectedHasNoDescription() {
        assertNull(checkoutDescriptionFor(null))
    }
}
