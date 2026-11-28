package com.arisassistant

/**
 * Decides whether the floating foreground service may run.
 *
 * An explicit start is always user initiated. A null intent is only the
 * system's sticky restart after ordinary process death, and is accepted only
 * while the persisted user opt-in remains true. Stop and unknown commands
 * never start the overlay.
 */
internal enum class FloatingServiceStartDecision {
    START,
    STOP
}

internal fun resolveFloatingServiceStartDecision(
    action: String?,
    startAction: String,
    stopAction: String,
    userEnabled: Boolean
): FloatingServiceStartDecision = when {
    action == stopAction -> FloatingServiceStartDecision.STOP
    action == startAction -> FloatingServiceStartDecision.START
    action == null && userEnabled -> FloatingServiceStartDecision.START
    else -> FloatingServiceStartDecision.STOP
}

/**
 * Enforces Android's foreground-start deadline before any synchronous
 * persistence that could delay startup. If promotion throws, consent is not
 * written; if persistence fails, the caller must stop the service.
 */
internal fun promoteForegroundBeforeConsent(
    promoteForeground: () -> Unit,
    persistConsent: () -> Boolean
): Boolean {
    promoteForeground()
    return persistConsent()
}

/**
 * Android 14 (API 34) requires the foreground-service type to be supplied
 * when promoting a service. Aris declares specialUse in its manifest.
 */
internal fun shouldPassSpecialUseForegroundServiceType(apiLevel: Int): Boolean =
    apiLevel >= 34

/**
 * A rejected null-intent sticky restart may still need to settle Android's
 * foreground-start deadline. Promote before synchronous diagnostic commits;
 * then record the rejection so the caller can clear consent and stop.
 *
 * Promotion failures are intentionally swallowed here because a rejected
 * restart is never allowed to attach an overlay and the caller stops it next.
 */
internal fun promoteBeforeRejectedStartDiagnostics(
    shouldPromote: Boolean,
    promoteForeground: () -> Unit,
    recordRejection: () -> Unit
) {
    if (shouldPromote) {
        try {
            promoteForeground()
        } catch (_: SecurityException) {
            // The caller immediately clears consent and stops the service.
        } catch (_: IllegalStateException) {
            // The caller immediately clears consent and stops the service.
        }
    }
    recordRejection()
}
