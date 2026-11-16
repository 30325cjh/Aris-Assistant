package com.arisassistant.ai

/**
 * Provider-neutral conversation role. Device actions are intentionally not
 * represented in this response contract; they belong to a separate allowlisted
 * executor with explicit confirmation rules.
 */
internal enum class ConversationRole {
    USER,
    ASSISTANT
}

internal data class ConversationTurn(
    val role: ConversationRole,
    val text: String
)

internal data class AssistantRequest(
    val userMessage: String,
    val recentHistory: List<ConversationTurn> = emptyList()
)

/**
 * Raw presentation fields are untrusted provider output. Always call
 * [UntrustedPresentationDirective.validate] before passing them to a renderer.
 */
internal data class UntrustedPresentationDirective(
    val emotion: String? = null,
    val expression: String? = null,
    val motion: String? = null,
    val intensity: Float? = null
)

internal data class AssistantResponse(
    val message: String,
    val presentation: UntrustedPresentationDirective? = null
)

/**
 * Implementations may be Gemini or another provider. This interface does not
 * prescribe transport, key storage, streaming, or persistence.
 */
internal interface AssistantProvider {
    val providerId: String

    suspend fun generateReply(request: AssistantRequest): AssistantResponse
}

internal enum class AssistantEmotion(val wireValue: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    SAD("sad"),
    SURPRISED("surprised"),
    SERIOUS("serious"),
    CONFUSED("confused"),
    RELAXED("relaxed")
}

internal enum class CharacterExpression(val wireValue: String) {
    NEUTRAL("neutral"),
    SMILE("smile"),
    SAD("sad"),
    SURPRISED("surprised"),
    SERIOUS("serious"),
    CONFUSED("confused"),
    RELAXED("relaxed")
}

internal enum class CharacterMotion(val wireValue: String) {
    NONE("none"),
    IDLE("idle"),
    NOD("nod"),
    SHAKE("shake")
}

internal data class ValidatedPresentationDirective(
    val emotion: AssistantEmotion = AssistantEmotion.NEUTRAL,
    val expression: CharacterExpression = CharacterExpression.NEUTRAL,
    val motion: CharacterMotion = CharacterMotion.NONE,
    val intensity: Float = 0f
)

/**
 * Converts provider-controlled strings into a closed, renderer-safe set.
 * Unknown values fail closed to neutral/no-motion; intensity is finite and
 * clamped to [0, 1].
 */
internal fun UntrustedPresentationDirective.validate(): ValidatedPresentationDirective =
    ValidatedPresentationDirective(
        emotion = emotion.toAllowedValue(AssistantEmotion.entries, AssistantEmotion.NEUTRAL) { it.wireValue },
        expression = expression.toAllowedValue(
            CharacterExpression.entries,
            CharacterExpression.NEUTRAL
        ) { it.wireValue },
        motion = motion.toAllowedValue(CharacterMotion.entries, CharacterMotion.NONE) { it.wireValue },
        intensity = intensity
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: 0f
    )

private fun <T> String?.toAllowedValue(
    allowed: Iterable<T>,
    fallback: T,
    wireValue: (T) -> String
): T {
    val normalized = this?.trim()?.lowercase() ?: return fallback
    return allowed.firstOrNull { wireValue(it) == normalized } ?: fallback
}
