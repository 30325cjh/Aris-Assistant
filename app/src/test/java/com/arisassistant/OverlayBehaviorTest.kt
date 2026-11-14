package com.arisassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBehaviorTest {
    @Test
    fun singleTapOnlyExpandsCompactLauncher() {
        assertEquals(OverlayGestureAction.EXPAND, resolveSingleTapAction(expanded = false, chatOpen = false))
        assertEquals(OverlayGestureAction.NONE, resolveSingleTapAction(expanded = true, chatOpen = false))
        assertEquals(OverlayGestureAction.NONE, resolveSingleTapAction(expanded = true, chatOpen = true))
    }

    @Test
    fun doubleTapOnCompactLauncherOnlyExpands() {
        assertEquals(OverlayGestureAction.EXPAND, resolveDoubleTapAction(expanded = false, chatOpen = false))
    }

    @Test
    fun doubleTapOnExpandedCharacterTogglesChat() {
        assertEquals(OverlayGestureAction.OPEN_CHAT, resolveDoubleTapAction(expanded = true, chatOpen = false))
        assertEquals(OverlayGestureAction.CLOSE_CHAT, resolveDoubleTapAction(expanded = true, chatOpen = true))
    }

    @Test
    fun draggedWindowPositionIsClampedToDisplay() {
        assertEquals(
            OverlayPosition(x = 280, y = 520),
            clampOverlayPosition(
                x = 900,
                y = 900,
                screenWidth = 400,
                screenHeight = 800,
                windowWidth = 120,
                windowHeight = 280
            )
        )
        assertEquals(
            OverlayPosition(x = 0, y = 0),
            clampOverlayPosition(
                x = -20,
                y = -10,
                screenWidth = 400,
                screenHeight = 800,
                windowWidth = 500,
                windowHeight = 900
            )
        )
    }
}
