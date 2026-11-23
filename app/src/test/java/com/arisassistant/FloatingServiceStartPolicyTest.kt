package com.arisassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingServiceStartPolicyTest {
    private val startAction = "com.arisassistant.action.START_OVERLAY"
    private val stopAction = "com.arisassistant.action.STOP_OVERLAY"

    @Test
    fun explicitStartIsAcceptedOnlyAsTheUserInitiatedCommand() {
        assertEquals(
            FloatingServiceStartDecision.START,
            resolveFloatingServiceStartDecision(
                action = startAction,
                startAction = startAction,
                stopAction = stopAction,
                userEnabled = false
            )
        )
    }

    @Test
    fun explicitStopOverridesPreviouslySavedOptIn() {
        assertEquals(
            FloatingServiceStartDecision.STOP,
            resolveFloatingServiceStartDecision(
                action = stopAction,
                startAction = startAction,
                stopAction = stopAction,
                userEnabled = true
            )
        )
    }

    @Test
    fun nullIntentRestartsOnlyWhenTheUserPreviouslyEnabledTheAssistant() {
        assertEquals(
            FloatingServiceStartDecision.START,
            resolveFloatingServiceStartDecision(
                action = null,
                startAction = startAction,
                stopAction = stopAction,
                userEnabled = true
            )
        )
        assertEquals(
            FloatingServiceStartDecision.STOP,
            resolveFloatingServiceStartDecision(
                action = null,
                startAction = startAction,
                stopAction = stopAction,
                userEnabled = false
            )
        )
    }

    @Test
    fun unknownCommandsNeverStartTheFloatingService() {
        assertEquals(
            FloatingServiceStartDecision.STOP,
            resolveFloatingServiceStartDecision(
                action = "com.arisassistant.action.UNKNOWN",
                startAction = startAction,
                stopAction = stopAction,
                userEnabled = true
            )
        )
    }
}
