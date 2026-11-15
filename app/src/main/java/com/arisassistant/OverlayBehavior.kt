package com.arisassistant

internal enum class OverlayGestureAction {
    NONE,
    EXPAND,
    OPEN_CHAT,
    CLOSE_CHAT
}

internal data class OverlayPosition(
    val x: Int,
    val y: Int
)

/**
 * A single tap only expands the compact launcher. Once expanded, a single tap
 * is intentionally inert so the character cannot collapse accidentally.
 */
internal fun resolveSingleTapAction(
    expanded: Boolean,
    chatOpen: Boolean
): OverlayGestureAction =
    if (!expanded && !chatOpen) OverlayGestureAction.EXPAND else OverlayGestureAction.NONE

/**
 * A double tap on the compact launcher expands it. On the expanded character,
 * the same gesture opens or closes chat.
 */
internal fun resolveDoubleTapAction(
    expanded: Boolean,
    chatOpen: Boolean
): OverlayGestureAction = when {
    !expanded -> OverlayGestureAction.EXPAND
    chatOpen -> OverlayGestureAction.CLOSE_CHAT
    else -> OverlayGestureAction.OPEN_CHAT
}

/**
 * WindowManager uses a positive x offset to move a TOP|END window leftward.
 * Clamp both offsets so a resized window stays within the available display.
 */
internal fun clampOverlayPosition(
    x: Int,
    y: Int,
    screenWidth: Int,
    screenHeight: Int,
    windowWidth: Int,
    windowHeight: Int
): OverlayPosition {
    val maxX = (screenWidth - windowWidth).coerceAtLeast(0)
    val maxY = (screenHeight - windowHeight).coerceAtLeast(0)
    return OverlayPosition(
        x = x.coerceIn(0, maxX),
        y = y.coerceIn(0, maxY)
    )
}
