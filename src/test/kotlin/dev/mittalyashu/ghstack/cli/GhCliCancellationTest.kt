package com.github.mittalyashu.ghstack.cli

import com.intellij.execution.process.ProcessHandler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.OutputStream

class GhCliCancellationTest {
    @Test
    fun `a process that finishes on its own is reported as finished`() {
        val handler = FakeProcessHandler(terminateOnWait = 1)
        handler.startNotify()

        assertEquals(ProcessStop.FINISHED, awaitProcess(handler, 10 * 60_000) { true })
        assertEquals(0, handler.destroyCalls)
    }

    @Test
    fun `a process that already terminated is not destroyed`() {
        val handler = FakeProcessHandler(alreadyTerminated = true)
        handler.startNotify()

        assertEquals(ProcessStop.FINISHED, awaitProcess(handler, 10 * 60_000) { true })
        assertEquals(0, handler.destroyCalls)
    }

    @Test
    fun `cancellation destroys the process and reports cancelled`() {
        val handler = FakeProcessHandler()
        handler.startNotify()

        assertEquals(ProcessStop.CANCELLED, awaitProcess(handler, 10 * 60_000) { true })
        assertEquals(1, handler.destroyCalls)
    }

    @Test
    fun `timeout destroys the process and reports timed out`() {
        val handler = FakeProcessHandler()
        handler.startNotify()

        assertEquals(ProcessStop.TIMED_OUT, awaitProcess(handler, timeoutMs = 0) { false })
        assertEquals(1, handler.destroyCalls)
    }

    @Test
    fun `a successful process that finishes during the kill wins the race`() {
        val handler = FakeProcessHandler(exitCodeOnDestroy = 0)
        handler.startNotify()

        assertEquals(ProcessStop.FINISHED, awaitProcess(handler, 10 * 60_000) { true })
        assertEquals(1, handler.destroyCalls)
    }

    @Test
    fun `a failed process that finishes during the kill is still reported as cancelled`() {
        val handler = FakeProcessHandler(exitCodeOnDestroy = 3)
        handler.startNotify()

        assertEquals(ProcessStop.CANCELLED, awaitProcess(handler, 10 * 60_000) { true })
        assertEquals(1, handler.destroyCalls)
    }

    @Test
    fun `waiting stops once the process terminates during a poll`() {
        val handler = FakeProcessHandler(terminateOnWait = 2)
        handler.startNotify()

        var polls = 0
        val stop = awaitProcess(handler, 10 * 60_000) {
            polls++
            false
        }

        assertEquals(ProcessStop.FINISHED, stop)
        assertEquals(1, polls)
        assertEquals(0, handler.destroyCalls)
    }
}

private class FakeProcessHandler(
    private val terminateOnWait: Int = Int.MAX_VALUE,
    private val alreadyTerminated: Boolean = false,
    private val exitCodeOnDestroy: Int? = null,
) : ProcessHandler() {
    var destroyCalls = 0
        private set

    private var waits = 0
    private var destroyed = false

    override fun isProcessTerminated(): Boolean = alreadyTerminated || destroyed

    override fun waitFor(timeoutInMilliseconds: Long): Boolean {
        if (destroyed) return true
        if (alreadyTerminated) return false
        waits++
        return waits >= terminateOnWait
    }

    override fun destroyProcessImpl() {
        destroyCalls++
        destroyed = true
        exitCodeOnDestroy?.let { notifyProcessTerminated(it) }
    }

    override fun detachProcessImpl() = Unit

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}
