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
 * Display dimensions and the safe insets occupied by system bars or a cutout.
 * Insets are expressed in physical pixels, matching WindowManager.LayoutParams.
 */
internal data class OverlayDisplayArea(
    val width: Int,
    val height: Int,
    val insetLeft: Int = 0,
    val insetTop: Int = 0,
    val insetRight: Int = 0,
    val insetBottom: Int = 0
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
 * Keep the window inside the display's safe rectangle so it does not disappear
 * behind status/navigation bars or a display cutout.
 */
internal fun clampOverlayPosition(
    x: Int,
    y: Int,
    screenWidth: Int,
    screenHeight: Int,
    windowWidth: Int,
    windowHeight: Int,
    insetLeft: Int = 0,
    insetTop: Int = 0,
    insetRight: Int = 0,
    insetBottom: Int = 0
): OverlayPosition {
    // For TOP|END gravity x is measured from the right edge, so the right inset
    // is the minimum x offset while the left inset limits the maximum offset.
    val minX = insetRight.coerceAtLeast(0)
    val maxX = (screenWidth - insetLeft.coerceAtLeast(0) - windowWidth).coerceAtLeast(minX)
    val minY = insetTop.coerceAtLeast(0)
    val maxY = (screenHeight - insetBottom.coerceAtLeast(0) - windowHeight).coerceAtLeast(minY)
    return OverlayPosition(
        x = x.coerceIn(minX, maxX),
        y = y.coerceIn(minY, maxY)
    )
}
