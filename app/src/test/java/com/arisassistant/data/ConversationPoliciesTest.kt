package com.arisassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationPoliciesTest {
    @Test
    fun normalizesConversationTitleAndCapsLength() {
        val title = normalizeConversationTitle("  Aris   project  " + "x".repeat(100))
        assertEquals(MAX_CONVERSATION_TITLE_CHARS, title.length)
        assertEquals("새 대화", normalizeConversationTitle("   "))
    }

    @Test
    fun trimsStoredMessageAndEnforcesLimit() {
        assertEquals("hello", normalizeStoredMessage("  hello  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankStoredMessage() {
        normalizeStoredMessage("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedStoredMessage() {
        normalizeStoredMessage("x".repeat(MAX_STORED_MESSAGE_CHARS + 1))
    }

    @Test
    fun normalizesExplicitMemory() {
        assertEquals("내가 선호하는 언어는 한국어", normalizeSavedMemory("  내가 선호하는 언어는 한국어  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedMemory() {
        normalizeSavedMemory("x".repeat(MAX_SAVED_MEMORY_CHARS + 1))
    }
}
