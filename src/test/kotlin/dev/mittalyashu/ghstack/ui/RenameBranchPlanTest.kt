package com.github.mittalyashu.ghstack.ui

import com.github.mittalyashu.ghstack.GhStackBundle
import com.github.mittalyashu.ghstack.model.PullRequestRef
import com.github.mittalyashu.ghstack.model.StackLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class RenameBranchPlanTest {
    @Test
    fun substitutesTheRenamedBranchAndKeepsStackOrder() {
        val plan = buildRenameBranchPlan("auth", "auth-v2", "main", listOf("auth", "api", "ui"))

        assertEquals(listOf("auth-v2", "api", "ui"), plan.branches)
    }

    @Test
    fun buildsTheUnstackRenameAndInitCommands() {
        val plan = buildRenameBranchPlan("auth", "auth-v2", "main", listOf("auth", "api"))

        assertEquals("gh stack unstack --local", plan.unstackCommand)
        assertEquals("git branch -m auth auth-v2", plan.renameCommand)
        assertEquals("git branch --unset-upstream auth-v2", plan.unsetUpstreamCommand)
        assertEquals("gh stack init --base main auth-v2 api", plan.initCommand)
        assertEquals(
            listOf(
                "gh stack unstack --local",
                "git branch -m auth auth-v2",
                "git branch --unset-upstream auth-v2",
                "gh stack init --base main auth-v2 api",
            ),
            plan.commands,
        )
    }

    @Test
    fun renamesOnlyTheSelectedLayer() {
        val plan = buildRenameBranchPlan("api", "api-v2", "main", listOf("auth", "api", "ui"))

        assertEquals(listOf("auth", "api-v2", "ui"), plan.branches)
        assertEquals("gh stack init --base main auth api-v2 ui", plan.initCommand)
    }

    @Test
    fun layerWithoutPullRequestGetsThePlainRenamePrompt() {
        assertEquals(
            GhStackBundle.message("panel.rename.branch.description"),
            renameBranchDescription(StackLayer("auth")),
        )
    }

    @Test
    fun layerWithPullRequestExplainsThatRenamingClosesIt() {
        val layer = StackLayer("auth", pr = PullRequestRef(number = 42))

        assertEquals(
            GhStackBundle.message("panel.rename.branch.pr", 42),
            renameBranchDescription(layer),
        )
    }

    @Test
    fun trunkRowAsksForALayerSelection() {
        assertEquals(
            GhStackBundle.message("panel.rename.branch.no.layer"),
            renameBranchDescription(null),
        )
    }
}
