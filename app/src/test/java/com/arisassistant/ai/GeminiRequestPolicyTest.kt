package com.arisassistant.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiRequestPolicyTest {
    @Test
    fun trimsAndBoundsUserMessage() {
        val normalized = AssistantRequest(
            userMessage = "  " + "x".repeat(MAX_GEMINI_MESSAGE_CHARS + 50) + "  "
        ).normalizedForGemini()

        assertEquals(MAX_GEMINI_MESSAGE_CHARS, normalized.userMessage.length)
    }

    @Test
    fun keepsOnlyMostRecentNonBlankHistoryTurns() {
        val history = (0 until MAX_GEMINI_HISTORY_TURNS + 5).map { index ->
            ConversationTurn(ConversationRole.USER, "turn-$index")
        } + ConversationTurn(ConversationRole.ASSISTANT, "   ")

        val normalized = AssistantRequest(
            userMessage = "hello",
            recentHistory = history
        ).normalizedForGemini()

        assertEquals(MAX_GEMINI_HISTORY_TURNS, normalized.recentHistory.size)
        assertEquals("turn-5", normalized.recentHistory.first().text)
        assertEquals("turn-24", normalized.recentHistory.last().text)
    }

    @Test
    fun trimsHistoryTextAndRejectsBlankCurrentMessage() {
        val normalized = AssistantRequest(
            userMessage = "  hello  ",
            recentHistory = listOf(
                ConversationTurn(ConversationRole.ASSISTANT, "  previous reply  ")
            )
        ).normalizedForGemini()

        assertEquals("hello", normalized.userMessage)
        assertEquals("previous reply", normalized.recentHistory.single().text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankCurrentMessage() {
        AssistantRequest(userMessage = "   ").normalizedForGemini()
    }

    @Test
    fun savedMemoriesAreTrimmedBoundedAndLimited() {
        val memories = (0 until MAX_GEMINI_MEMORY_ITEMS + 3).map { index ->
            " memory-$index "
        } + "x".repeat(MAX_GEMINI_MEMORY_CHARS + 10)

        val normalized = AssistantRequest(
            userMessage = "hello",
            savedMemories = memories
        ).normalizedForGemini()

        assertEquals(MAX_GEMINI_MEMORY_ITEMS, normalized.savedMemories.size)
        assertEquals("memory-0", normalized.savedMemories.first())
        assertEquals(MAX_GEMINI_MEMORY_CHARS, normalized.savedMemories.last().length)
    }

    @Test
    fun blankMemoriesAreNotSent() {
        val normalized = AssistantRequest(
            userMessage = "hello",
            savedMemories = listOf(" ", "  ")
        ).normalizedForGemini()

        assertEquals(emptyList<String>(), normalized.savedMemories)
    }
}
