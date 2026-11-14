package com.arisassistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_PICK_BUNDLE = 1001
        private const val REQUEST_POST_NOTIFICATIONS = 2002
    }

    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("aris", MODE_PRIVATE) }
    private var pendingStartAfterOverlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        showFirstLaunchExplanationIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (!::status.isInitialized) return

        if (!Settings.canDrawOverlays(this)) {
            // Permission can be revoked while the service is running.
            stopService(Intent(this, FloatingAssistantService::class.java))
        }
        refreshStatus()

        if (pendingStartAfterOverlay) {
            pendingStartAfterOverlay = false
            if (Settings.canDrawOverlays(this)) {
                requestNotificationPermissionOrStart()
            } else {
                status.text = "화면 위 표시 권한이 허용되지 않아 실행하지 않았어요."
            }
        }
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            setBackgroundColor(0xFF08172D.toInt())
        }

        val eyebrow = TextView(this).apply {
            text = "MILLENNIUM  /  PERSONAL ASSISTANT"
            textSize = 11f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(0xFF83DDF2.toInt())
        }
        val title = TextView(this).apply {
            text = "ARIS AI"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(4))
        }
        val intro = TextView(this).apply {
            text = "다른 앱 위에 아리스 캐릭터를 띄우는 개인용 어시스턴트예요.\n캐릭터를 한 번 터치하면 펼쳐지고, 펼쳐진 캐릭터를 두 번 터치하면 채팅이 열려요."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFD5E6F5.toInt())
            setPadding(0, 0, 0, dp(24))
        }
        status = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8F5FF.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFF102B50.toInt())
        }

        val overlay = actionButton("화면 위 표시 권한 설정") { openOverlaySettings() }
        val start = actionButton("아리스 플로팅 실행") { startOverlay() }
        val stop = actionButton("플로팅 종료") {
            stopService(Intent(this, FloatingAssistantService::class.java))
            status.text = "플로팅 종료를 요청했어요."
            refreshStatus()
        }
        val import = actionButton("기존 Unity 파일 선택 (실험용)") { chooseBundle() }
        val note = TextView(this).apply {
            text = "현재 떠 있는 캐릭터는 동작 확인용 UI 자리표시자입니다. Live2D 모델, Gemini 채팅, 음성 및 기억 기능은 아직 연결되지 않았어요."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF9DB2C8.toInt())
            setPadding(0, dp(22), 0, 0)
        }

        content.addView(eyebrow, fullWidth())
        content.addView(title, fullWidth())
        content.addView(intro, fullWidth())
        content.addView(status, fullWidth())
        content.addView(overlay, fullWidth())
        content.addView(start, fullWidth())
        content.addView(stop, fullWidth())
        content.addView(import, fullWidth())
        content.addView(note, fullWidth())

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
        refreshStatus()
    }

    private fun showFirstLaunchExplanationIfNeeded() {
        if (prefs.getBoolean("overlay_explanation_seen", false)) return

        AlertDialog.Builder(this)
            .setTitle("아리스 플로팅 권한 안내")
            .setMessage(
                "아리스 캐릭터를 다른 앱 위에 표시하려면 Android의 '다른 앱 위에 표시' 권한이 필요해요. " +
                    "이 권한은 사용자가 시스템 설정에서 직접 허용해야 합니다. " +
                    "플로팅을 켜면 Android가 요구하는 실행 중 알림도 표시됩니다. " +
                    "언제든 앱의 '플로팅 종료' 버튼이나 알림의 종료 버튼으로 끌 수 있어요."
            )
            .setPositiveButton("권한 설정") { _, _ ->
                prefs.edit().putBoolean("overlay_explanation_seen", true).apply()
                openOverlaySettings()
            }
            .setNegativeButton("나중에") { _, _ ->
                prefs.edit().putBoolean("overlay_explanation_seen", true).apply()
            }
            .show()
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
        ).apply { topMargin = dp(10) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            status.text = "Android 화면 위 표시 설정을 열 수 없어요. 설정 앱에서 권한을 직접 확인해 주세요."
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStartAfterOverlay = true
            status.text = "화면 위 표시 권한을 허용한 뒤 이 앱으로 돌아와 주세요."
            openOverlaySettings()
            return
        }
        requestNotificationPermissionOrStart()
    }

    private fun requestNotificationPermissionOrStart() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("실행 중 알림 권한")
                .setMessage(
                    "Android 13 이상에서는 알림 권한을 허용하면 플로팅 서비스 알림을 알림 창에서 확인할 수 있어요. " +
                        "거부해도 Android의 시스템 작업 관리자에는 실행 중 서비스가 표시될 수 있습니다."
                )
                .setPositiveButton("알림 권한 요청") { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_POST_NOTIFICATIONS
                    )
                }
                .setNegativeButton("알림 없이 계속") { _, _ ->
                    startForegroundAssistant()
                }
                .show()
            return
        }
        startForegroundAssistant()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startForegroundAssistant()
        } else {
            AlertDialog.Builder(this)
                .setTitle("알림 권한이 허용되지 않았어요")
                .setMessage(
                    "알림 창에는 서비스 알림이 보이지 않을 수 있지만 Android 시스템 작업 관리자에는 실행 중 서비스가 표시됩니다. " +
                        "그래도 플로팅 어시스턴트를 시작할까요?"
                )
                .setPositiveButton("계속 실행") { _, _ -> startForegroundAssistant() }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun startForegroundAssistant() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "화면 위 표시 권한이 없어 실행하지 않았어요."
            return
        }
        try {
            startForegroundService(Intent(this, FloatingAssistantService::class.java))
            status.text = "아리스 플로팅 실행을 요청했어요. 캐릭터는 한 번, 채팅은 펼쳐진 캐릭터를 두 번 터치해 주세요."
        } catch (_: SecurityException) {
            status.text = "Android가 서비스 실행을 거부했어요. 화면 위 표시 및 알림 권한을 확인해 주세요."
        } catch (_: IllegalStateException) {
            status.text = "지금은 백그라운드 서비스를 시작할 수 없어요. 앱 화면에서 다시 시도해 주세요."
        }
    }

    private fun chooseBundle() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_PICK_BUNDLE)
    }

    @Deprecated("Legacy callback retained for the existing experimental file picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_BUNDLE || resultCode != RESULT_OK) return

        data?.data?.let { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                prefs.edit().putString("bundle_uri", uri.toString()).apply()
                refreshStatus()
            } catch (_: SecurityException) {
                status.text = "파일 접근 권한을 저장하지 못했어요. 다시 선택해 주세요."
            }
        }
    }

    private fun refreshStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val notificationState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "알림 권한: Android 런타임 요청 대상 아님"
        } else if (
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            "알림 권한: 허용됨"
        } else {
            "알림 권한: 미허용"
        }
        val bundleSelected = prefs.getString("bundle_uri", null) != null

        status.text = buildString {
            append(if (overlayGranted) "화면 위 표시: 허용됨" else "화면 위 표시: 미허용")
            append("\n")
            append(notificationState)
            append("\n")
            append(if (bundleSelected) "Unity 파일: 선택됨 (렌더링하지 않음)" else "Unity 파일: 미선택")
        }
    }
}
