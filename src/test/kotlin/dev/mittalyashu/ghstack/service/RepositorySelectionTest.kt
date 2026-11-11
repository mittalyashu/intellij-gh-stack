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

    @Test
    fun `auto mode picks the repository containing the selected file`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(base, base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = setOf(base.resolve("libs/alpha")),
        )

        assertEquals(base, resolveAutoRepository(choices, base.resolve("src/Main.kt"), null))
        assertEquals(
            base.resolve("libs/alpha"),
            resolveAutoRepository(choices, base.resolve("libs/alpha/src/Lib.kt"), null),
        )
    }

    @Test
    fun `auto mode picks the deepest repository for a nested file`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(base, base.resolve("libs/alpha"), base.resolve("libs/alpha/vendor/beta")),
            projectBaseDir = base,
            submoduleRoots = emptySet(),
        )

        assertEquals(
            base.resolve("libs/alpha/vendor/beta"),
            resolveAutoRepository(choices, base.resolve("libs/alpha/vendor/beta/src/Beta.kt"), null),
        )
    }

    @Test
    fun `auto mode keeps the previous repository for a file outside every repository`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(base, base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = setOf(base.resolve("libs/alpha")),
        )

        assertEquals(
            base.resolve("libs/alpha"),
            resolveAutoRepository(choices, Path.of("/elsewhere/Scratch.kt"), base.resolve("libs/alpha")),
        )
    }

    @Test
    fun `auto mode keeps the previous repository when no editor is open`() {
        val choices = buildRepositoryChoices(
            repositories = setOf(base, base.resolve("libs/alpha")),
            projectBaseDir = base,
            submoduleRoots = emptySet(),
        )

        assertEquals(base.resolve("libs/alpha"), resolveAutoRepository(choices, null, base.resolve("libs/alpha")))
        assertNull(resolveAutoRepository(choices, null, null))
    }

    @Test
    fun `auto mode has no repository without choices`() {
        assertNull(resolveAutoRepository(emptyList(), base.resolve("src/Main.kt"), null))
    }

    @Test
    fun `a running task's pinned repository wins over every panel selection`() {
        val pinned = Path.of("/work/pinned")
        val selected = Path.of("/work/selected")
        val auto = Path.of("/work/auto")

        assertEquals(pinned, effectiveRepositoryRoot(pinned, selected, auto))
        assertEquals(pinned, effectiveRepositoryRoot(pinned, null, auto))
    }

    @Test
    fun `explicit selection wins over the repository followed from the editor`() {
        val selected = Path.of("/work/selected")
        val auto = Path.of("/work/auto")

        assertEquals(selected, effectiveRepositoryRoot(null, selected, auto))
        assertEquals(auto, effectiveRepositoryRoot(null, null, auto))
        assertNull(effectiveRepositoryRoot(null, null, null))
    }
}
