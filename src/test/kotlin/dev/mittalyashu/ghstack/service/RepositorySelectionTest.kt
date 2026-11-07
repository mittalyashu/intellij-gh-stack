package com.github.mittalyashu.ghstack.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

class RepositorySelectionTest {
    private val base = Path.of("/work/project")

    @Test
    fun `project repository comes first and submodules follow by path`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(
                base.resolve("libs/zeta"),
                base.resolve("libs/alpha"),
                base,
            ),
            projectBaseDir = base,
            submoduleRoots = setOf(base.resolve("libs/zeta"), base.resolve("libs/alpha")),
        )

        assertEquals(listOf("project", "libs/alpha", "libs/zeta"), choices.map { it.label })
        assertEquals(listOf(false, true, true), choices.map { it.isSubmodule })
    }

    @Test
    fun `repositories outside the project sort after project roots and keep their absolute path`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(Path.of("/other/repo"), base.resolve("libs/alpha"), base),
            projectBaseDir = base,
            submoduleRoots = emptySet(),
        )

        assertEquals(listOf("project", "libs/alpha", "/other/repo"), choices.map { it.label })
    }

    @Test
    fun `repository containing the project is preferred over nested repositories`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(Path.of("/work"), base, base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = setOf(base.resolve("libs/alpha")),
        )

        assertEquals(listOf("project", "work", "libs/alpha"), choices.map { it.label })
        assertEquals(base, chooseRepository(choices, null)?.root)
    }

    @Test
    fun `nearest repository containing the project sorts first among ancestors`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(Path.of("/work"), Path.of("/work/project")),
            projectBaseDir = Path.of("/work/project/src"),
            submoduleRoots = emptySet(),
        )

        assertEquals(listOf(Path.of("/work/project"), Path.of("/work")), choices.map { it.root })
    }

    @Test
    fun `duplicate roots collapse to one choice`() {
        val choices = buildRepositoryChoices(
            repositories = listOf(base, base.normalize(), base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = emptySet(),
        )

        assertEquals(2, choices.size)
    }

    @Test
    fun `selected repository is kept while it exists`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(base, base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = setOf(base.resolve("libs/alpha")),
        )

        assertEquals(base.resolve("libs/alpha"), chooseRepository(choices, base.resolve("libs/alpha"))?.root)
    }

    @Test
    fun `selection falls back to the preferred repository when it disappears`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(base, base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = emptySet(),
        )

        assertEquals(base, chooseRepository(choices, base.resolve("libs/removed"))?.root)
        assertEquals(base, chooseRepository(choices, null)?.root)
    }

    @Test
    fun `no repositories means no choice`() {
        val choices = buildRepositoryChoices(emptySet(), base, emptySet())

        assertNull(chooseRepository(choices, base))
    }
}
