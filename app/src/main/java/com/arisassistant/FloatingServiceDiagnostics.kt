package com.arisassistant

import android.content.Context

/**
 * Device-local, privacy-safe diagnostics for the floating foreground service.
 *
 * Only enum names and timestamps are stored. This class deliberately has no
 * API for accepting arbitrary text, so chat content, memories, API keys, and
 * action payloads cannot accidentally enter diagnostic history.
 */
internal enum class FloatingServiceDiagnosticEvent(val label: String) {
    NONE("기록 없음"),
    USER_START_REQUESTED("사용자 실행 요청"),
    USER_START_ACCEPTED("사용자 실행 승인"),
    USER_STOP_REQUESTED("사용자 종료 요청"),
    STICKY_RESTART_ACCEPTED("Android 복구 요청 수락"),
    STICKY_RESTART_REJECTED("Android 복구 요청 거부"),
    OVERLAY_PERMISSION_MISSING("화면 위 표시 권한 없음"),
    OVERLAY_PERMISSION_REVOKED("화면 위 표시 권한 회수"),
    FOREGROUND_START_FAILED("Foreground Service 시작 실패"),
    CONSENT_PERSIST_FAILED("실행 동의 저장 실패"),
    ENCRYPTED_STORE_OPEN_FAILED("암호화 대화 저장소 열기 실패"),
    OVERLAY_ATTACHED("플로팅 창 표시 완료"),
    OVERLAY_ATTACH_FAILED("플로팅 창 생성 실패"),
    UNKNOWN_COMMAND_REJECTED("알 수 없는 서비스 명령 거부")
}

internal enum class StickyRecoveryOutcome(val label: String) {
    NOT_RECORDED("복구 기록 없음"),
    REQUESTED("복구 요청 수신"),
    RESTORED("복구 후 캐릭터 표시 완료"),
    REJECTED("복구 요청 거부"),
    FAILED("복구 중 오류")
}

internal data class FloatingServiceDiagnosticSnapshot(
    val lastEvent: FloatingServiceDiagnosticEvent,
    val lastEventAtMillis: Long,
    val recoveryOutcome: StickyRecoveryOutcome,
    val recoveryAtMillis: Long
)

internal class FloatingServiceConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean =
        preferences.getBoolean(KEY_ENABLED_BY_USER, false)

    /**
     * The consent flag is a tiny critical lifecycle value. Use commit() so
     * START_STICKY is not returned before the user's choice reaches disk.
     */
    fun setEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(KEY_ENABLED_BY_USER, enabled).commit()

    private companion object {
        const val PREFERENCES_NAME = "aris_overlay"
        const val KEY_ENABLED_BY_USER = "enabled_by_user"
    }
}

internal class FloatingServiceDiagnostics(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun record(event: FloatingServiceDiagnosticEvent): Boolean =
        preferences.edit()
            .putString(KEY_LAST_EVENT, event.name)
            .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())
            .commit()

    @Synchronized
    fun recordRecovery(outcome: StickyRecoveryOutcome): Boolean =
        preferences.edit()
            .putString(KEY_RECOVERY_OUTCOME, outcome.name)
            .putLong(KEY_RECOVERY_AT, System.currentTimeMillis())
            .commit()

    fun snapshot(): FloatingServiceDiagnosticSnapshot {
        val event = FloatingServiceDiagnosticEvent.entries.firstOrNull {
            it.name == preferences.getString(KEY_LAST_EVENT, null)
        } ?: FloatingServiceDiagnosticEvent.NONE
        val recovery = StickyRecoveryOutcome.entries.firstOrNull {
            it.name == preferences.getString(KEY_RECOVERY_OUTCOME, null)
        } ?: StickyRecoveryOutcome.NOT_RECORDED

        return FloatingServiceDiagnosticSnapshot(
            lastEvent = event,
            lastEventAtMillis = preferences.getLong(KEY_LAST_EVENT_AT, 0L),
            recoveryOutcome = recovery,
            recoveryAtMillis = preferences.getLong(KEY_RECOVERY_AT, 0L)
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "aris_overlay"
        const val KEY_LAST_EVENT = "diagnostic_last_event"
        const val KEY_LAST_EVENT_AT = "diagnostic_last_event_at"
        const val KEY_RECOVERY_OUTCOME = "diagnostic_recovery_outcome"
        const val KEY_RECOVERY_AT = "diagnostic_recovery_at"
    }
}
