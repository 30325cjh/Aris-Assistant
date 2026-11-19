package com.arisassistant.ai

internal const val MAX_GEMINI_MESSAGE_CHARS = 4_000
internal const val MAX_GEMINI_HISTORY_TURNS = 20
internal const val MAX_GEMINI_MEMORY_ITEMS = 10
internal const val MAX_GEMINI_MEMORY_CHARS = 500

/**
 * Bounds user-controlled input, conversation history, and explicitly opted-in
 * memory context before any provider request is sent.
 */
internal fun AssistantRequest.normalizedForGemini(): AssistantRequest {
    val message = userMessage.trim().take(MAX_GEMINI_MESSAGE_CHARS)
    require(message.isNotEmpty()) { "Message must not be empty." }

    val history = recentHistory
        .asSequence()
        .filter { it.text.isNotBlank() }
        .toList()
        .takeLast(MAX_GEMINI_HISTORY_TURNS)
        .map { turn ->
            turn.copy(text = turn.text.trim().take(MAX_GEMINI_MESSAGE_CHARS))
        }

    val memories = savedMemories
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .take(MAX_GEMINI_MEMORY_ITEMS)
        .map { it.take(MAX_GEMINI_MEMORY_CHARS) }
        .toList()

    return copy(
        userMessage = message,
        recentHistory = history,
        savedMemories = memories
    )
}
