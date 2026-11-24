package com.arisassistant

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * Read-only diagnostics for the personal-use floating service.
 * No conversation, memory, API-key, or arbitrary payload is read or displayed.
 */
class FloatingServiceDiagnosticsActivity : Activity() {
    private lateinit var rows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        refreshDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        if (::rows.isInitialized) refreshDiagnostics()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(0xFF08172D.toInt())
        }
        val eyebrow = TextView(this).apply {
            text = "MILLENNIUM  /  SYSTEM STATUS"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(0xFF83DDF2.toInt())
        }
        val heading = TextView(this).apply {
            text = "플로팅 서비스 진단"
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        val description = TextView(this).apply {
            text = "Android 권한과 서비스 생명주기만 확인합니다. 대화, 기억, Gemini API 키는 진단 기록에 포함하지 않습니다."
            textSize = 13f
            setTextColor(0xFFD5E6F5.toInt())
            setPadding(0, 0, 0, dp(16))
        }
        rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val refresh = actionButton("상태 새로고침") { refreshDiagnostics() }
        val back = actionButton("돌아가기") { finish() }

        root.addView(eyebrow, fullWidth())
        root.addView(heading, fullWidth())
        root.addView(description, fullWidth())
        root.addView(rows, fullWidth())
        root.addView(refresh, fullWidth())
        root.addView(back, fullWidth())

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun refreshDiagnostics() {
        rows.removeAllViews()
        val snapshot = FloatingServiceDiagnostics(this).snapshot()
        val overlayGranted = Settings.canDrawOverlays(this)
        val userOptIn = FloatingServiceConsentStore(this).isEnabled()
        val serviceRunning = isServiceRunning()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationState = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                "Android 13+ 런타임 권한 미허용"
            !notificationManager.areNotificationsEnabled() -> "앱 알림 차단됨"
            else -> "알림 사용 가능"
        }

        addRow("Foreground Service", if (serviceRunning) "실행 중" else "현재 실행 중으로 확인되지 않음")
        addRow("사용자 실행 동의", if (userOptIn) "허용됨" else "해제됨")
        addRow("화면 위 표시 권한", if (overlayGranted) "허용됨" else "미허용")
        addRow("알림 상태", notificationState)
        addRow("최근 서비스 이벤트", snapshot.lastEvent.label + " · " + formatTime(snapshot.lastEventAtMillis))
        addRow("최근 Sticky 복구", snapshot.recoveryOutcome.label + " · " + formatTime(snapshot.recoveryAtMillis))
        addRow(
            "개인정보 보호",
            "고정된 이벤트 코드와 시각만 저장합니다. 대화 내용, 저장 기억, API 키, 메시지 본문은 기록하지 않습니다."
        )
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean = runCatching {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == FloatingAssistantService::class.java.name
        }
    }.getOrDefault(false)

    private fun formatTime(timestamp: Long): String =
        if (timestamp <= 0L) "기록 없음"
        else DateFormat.getDateTimeInstance().format(Date(timestamp))

    private fun addRow(label: String, value: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFF102B50.toInt())
                setStroke(dp(1), 0xFF245276.toInt())
            }
        }
        val title = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(0xFF83DDF2.toInt())
        }
        val detail = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, 0)
        }
        card.addView(title, fullWidth())
        card.addView(detail, fullWidth())
        rows.addView(card, fullWidth())
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun fullWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
