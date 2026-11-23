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
