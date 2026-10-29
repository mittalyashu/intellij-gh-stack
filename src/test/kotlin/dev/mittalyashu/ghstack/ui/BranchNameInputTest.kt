package com.github.mittalyashu.ghstack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BranchNameInputTest {
    private val removeSpaces: (String) -> String = { it.replace(" ", "") }

    @Test
    fun unchangedTextKeepsCaret() {
        assertEquals("feature" to 3, cleanUpBranchNameOnTyping("feature", 3, removeSpaces))
    }

    @Test
    fun caretBeforeRemovedCharacterStaysBeforeFollowingCharacter() {
        // "feat ure" -> "feature"; the caret was before 'u' and stays before 'u'.
        assertEquals("feature" to 4, cleanUpBranchNameOnTyping("feat ure", 5, removeSpaces))
    }

    @Test
    fun caretAfterRemovedCharacterStaysAfterIt() {
        // "feat ure" -> "feature"; the caret was before 'r' and stays before 'r'.
        assertEquals("feature" to 5, cleanUpBranchNameOnTyping("feat ure", 6, removeSpaces))
    }

    @Test
    fun caretAtStartStaysAtStart() {
        assertEquals("feature" to 0, cleanUpBranchNameOnTyping(" feat ure", 0, removeSpaces))
    }

    @Test
    fun caretAtEndStaysAtEnd() {
        assertEquals("feature" to 7, cleanUpBranchNameOnTyping("feat ure", 8, removeSpaces))
    }
}
