package com.arisassistant

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingServiceDiagnosticsTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun userConsentCanBePersistedAndClearedAcrossStoreInstances() {
        val firstStore = FloatingServiceConsentStore(context)
        assertFalse(firstStore.isEnabled())

        assertTrue(firstStore.setEnabled(true))
        assertTrue(FloatingServiceConsentStore(context).isEnabled())

        assertTrue(firstStore.setEnabled(false))
        assertFalse(FloatingServiceConsentStore(context).isEnabled())
    }

    @Test
    fun diagnosticsPersistAllowlistedLifecycleAndRecoveryValues() {
        val diagnostics = FloatingServiceDiagnostics(context)

        assertTrue(diagnostics.record(FloatingServiceDiagnosticEvent.OVERLAY_ATTACHED))
        assertTrue(diagnostics.recordRecovery(StickyRecoveryOutcome.RESTORED))

        val snapshot = FloatingServiceDiagnostics(context).snapshot()
        assertEquals(FloatingServiceDiagnosticEvent.OVERLAY_ATTACHED, snapshot.lastEvent)
        assertEquals(StickyRecoveryOutcome.RESTORED, snapshot.recoveryOutcome)
        assertTrue(snapshot.lastEventAtMillis > 0L)
        assertTrue(snapshot.recoveryAtMillis > 0L)
    }

    @Test
    fun unknownStoredDiagnosticValuesFallBackWithoutExposingRawText() {
        context.getSharedPreferences("aris_overlay", Context.MODE_PRIVATE).edit()
            .putString("diagnostic_last_event", "private chat content must not be displayed")
            .putString("diagnostic_recovery_outcome", "unknown")
            .commit()

        val snapshot = FloatingServiceDiagnostics(context).snapshot()
        assertEquals(FloatingServiceDiagnosticEvent.NONE, snapshot.lastEvent)
        assertEquals(StickyRecoveryOutcome.NOT_RECORDED, snapshot.recoveryOutcome)
        assertFalse(snapshot.toString().contains("private chat content"))
    }
}
