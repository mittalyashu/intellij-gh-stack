package com.github.mittalyashu.ghstack.service

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GhStackServiceRebaseStateTest : BasePlatformTestCase() {
    fun testStartRejectsASecondRebaseUntilFinished() {
        val service = project.getService(GhStackService::class.java)
        val indicator = EmptyProgressIndicator()

        assertFalse(service.isRebaseRunning())
        val isCancelled = checkNotNull(service.startRebase(indicator))
        assertTrue(service.isRebaseRunning())
        assertFalse(service.isRebaseCancelling())

        // A queued action from another panel must not take over the running rebase's signal.
        assertNull(service.startRebase(EmptyProgressIndicator()))

        service.cancelRebase()
        assertTrue(indicator.isCanceled)
        assertTrue(isCancelled())
        assertTrue(service.isRebaseCancelling())

        service.finishRebase()
        assertFalse(service.isRebaseRunning())
        assertFalse(service.isRebaseCancelling())
    }

    fun testCancellingFromTheProgressIndicatorIsVisibleToTheToolbar() {
        val service = project.getService(GhStackService::class.java)
        val indicator = EmptyProgressIndicator()
        service.startRebase(indicator)

        indicator.cancel()

        assertTrue(service.isRebaseCancelling())
        service.finishRebase()
    }

    fun testCancelledBeforeLaunchDoesNotStartTheCli() {
        val service = project.getService(GhStackService::class.java)

        // There is no Git repository in this project, so touching the repo or gh would fail rather than return.
        val outcome = service.rebaseCancellable({ true })

        assertTrue(outcome.result.cancelled)
        assertFalse(outcome.rolledBack)
        assertFalse(outcome.cleanupFailed)
    }
}
