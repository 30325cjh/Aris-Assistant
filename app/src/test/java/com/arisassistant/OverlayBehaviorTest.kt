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
    fun doubleTapIsCommittedOnlyWhenSecondTouchDoesNotBecomeDrag() {
        assertEquals(
            OverlayGestureAction.NONE,
            resolveDoubleTapCompletionAction(
                doubleTapDetected = true,
                dragged = true,
                expanded = true,
                chatOpen = false
            )
        )
        assertEquals(
            OverlayGestureAction.NONE,
            resolveDoubleTapCompletionAction(
                doubleTapDetected = false,
                dragged = false,
                expanded = true,
                chatOpen = false
            )
        )
        assertEquals(
            OverlayGestureAction.OPEN_CHAT,
            resolveDoubleTapCompletionAction(
                doubleTapDetected = true,
                dragged = false,
                expanded = true,
                chatOpen = false
            )
        )
        assertEquals(
            OverlayGestureAction.CLOSE_CHAT,
            resolveDoubleTapCompletionAction(
                doubleTapDetected = true,
                dragged = false,
                expanded = true,
                chatOpen = true
            )
        )
        assertEquals(
            OverlayGestureAction.EXPAND,
            resolveDoubleTapCompletionAction(
                doubleTapDetected = true,
                dragged = false,
                expanded = false,
                chatOpen = false
            )
        )
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

    @Test
    fun overlayStaysInsideSystemBarAndCutoutSafeArea() {
        val insets = mapOf(
            "insetLeft" to 8,
            "insetTop" to 24,
            "insetRight" to 12,
            "insetBottom" to 48
        )
        assertEquals(
            OverlayPosition(x = 12, y = 24),
            clampOverlayPosition(
                x = 0,
                y = 0,
                screenWidth = 400,
                screenHeight = 800,
                windowWidth = 120,
                windowHeight = 100,
                insetLeft = insets.getValue("insetLeft"),
                insetTop = insets.getValue("insetTop"),
                insetRight = insets.getValue("insetRight"),
                insetBottom = insets.getValue("insetBottom")
            )
        )
        assertEquals(
            OverlayPosition(x = 272, y = 652),
            clampOverlayPosition(
                x = 999,
                y = 999,
                screenWidth = 400,
                screenHeight = 800,
                windowWidth = 120,
                windowHeight = 100,
                insetLeft = insets.getValue("insetLeft"),
                insetTop = insets.getValue("insetTop"),
                insetRight = insets.getValue("insetRight"),
                insetBottom = insets.getValue("insetBottom")
            )
        )
    }

    @Test
    fun oversizedWindowAnchorsAtSafeEdgeWithoutInvalidRange() {
        assertEquals(
            OverlayPosition(x = 20, y = 30),
            clampOverlayPosition(
                x = 0,
                y = 0,
                screenWidth = 300,
                screenHeight = 200,
                windowWidth = 400,
                windowHeight = 300,
                insetLeft = 10,
                insetTop = 30,
                insetRight = 20,
                insetBottom = 40
            )
        )
    }
}
