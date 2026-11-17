package com.arisassistant.ai

internal const val MAX_GEMINI_MESSAGE_CHARS = 4_000
internal const val MAX_GEMINI_HISTORY_TURNS = 20

/**
 * Bounds user-controlled input and conversation context before it is sent
 * over the network. This policy does not execute or encode device actions.
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

    return copy(userMessage = message, recentHistory = history)
}
