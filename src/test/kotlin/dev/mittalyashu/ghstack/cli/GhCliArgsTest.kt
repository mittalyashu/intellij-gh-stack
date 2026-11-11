package com.github.mittalyashu.ghstack.cli

import org.junit.Assert.assertEquals
import org.junit.Test

class GhCliArgsTest {
    @Test
    fun initArgsPutTheBaseBeforeTheBranches() {
        assertEquals(
            listOf("stack", "init", "--base", "main", "auth", "api"),
            initStackArgs(listOf("auth", "api"), "main"),
        )
    }

    @Test
    fun initArgsOmitABlankBase() {
        assertEquals(
            listOf("stack", "init", "auth"),
            initStackArgs(listOf("auth"), ""),
        )
    }

    @Test
    fun initArgsWithoutBaseUseTheDefaultTrunk() {
        assertEquals(
            listOf("stack", "init", "auth", "api"),
            initStackArgs(listOf("auth", "api"), null),
        )
    }
}
