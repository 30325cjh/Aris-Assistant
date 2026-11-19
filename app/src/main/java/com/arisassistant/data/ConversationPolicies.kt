package com.arisassistant.data

internal const val MAX_CONVERSATION_TITLE_CHARS = 60
internal const val MAX_STORED_MESSAGE_CHARS = 8_000
internal const val MAX_SAVED_MEMORY_CHARS = 500
internal const val MAX_SAVED_MEMORIES = 100

internal fun normalizeConversationTitle(raw: String): String =
    raw.trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_CONVERSATION_TITLE_CHARS)
        .ifBlank { "새 대화" }

internal fun normalizeStoredMessage(raw: String): String {
    val value = raw.trim()
    require(value.isNotEmpty()) { "Message must not be blank." }
    require(value.length <= MAX_STORED_MESSAGE_CHARS) { "Message is too long." }
    return value
}

internal fun normalizeSavedMemory(raw: String): String {
    val value = raw.trim()
    require(value.isNotEmpty()) { "Memory must not be blank." }
    require(value.length <= MAX_SAVED_MEMORY_CHARS) { "Memory is too long." }
    return value
}
