package com.arisassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test
    fun foregroundPromotionHappensBeforeConsentPersistence() {
        val events = mutableListOf<String>()

        val persisted = promoteForegroundBeforeConsent(
            promoteForeground = { events += "foreground" },
            persistConsent = {
                events += "consent"
                true
            }
        )

        assertTrue(persisted)
        assertEquals(listOf("foreground", "consent"), events)
    }

    @Test
    fun failedForegroundPromotionDoesNotPersistConsent() {
        var consentWasPersisted = false

        try {
            promoteForegroundBeforeConsent(
                promoteForeground = { throw IllegalStateException("foreground promotion failed") },
                persistConsent = {
                    consentWasPersisted = true
                    true
                }
            )
        } catch (_: IllegalStateException) {
            // Expected: the caller handles the Android foreground-start failure.
        }

        assertFalse(consentWasPersisted)
    }

    @Test
    fun consentPersistenceFailureIsReturnedOnlyAfterForegroundPromotion() {
        val events = mutableListOf<String>()

        val persisted = promoteForegroundBeforeConsent(
            promoteForeground = { events += "foreground" },
            persistConsent = {
                events += "consent"
                false
            }
        )

        assertFalse(persisted)
        assertEquals(listOf("foreground", "consent"), events)
    }


    @Test
    fun specialUseForegroundTypeIsPassedOnAndroid14AndLaterOnly() {
        assertFalse(shouldPassSpecialUseForegroundServiceType(26))
        assertFalse(shouldPassSpecialUseForegroundServiceType(33))
        assertTrue(shouldPassSpecialUseForegroundServiceType(34))
        assertTrue(shouldPassSpecialUseForegroundServiceType(35))
    }
}
