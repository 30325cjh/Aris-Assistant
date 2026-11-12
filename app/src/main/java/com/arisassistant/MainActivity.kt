package com.arisassistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val pickFile = 1001
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("aris", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
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
            text = "화면 위에서 함께하는 나만의 AI 어시스턴트"
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
        val overlay = actionButton("화면 위 표시 권한 설정") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val start = actionButton("아리스 플로팅 실행") { startOverlay() }
        val stop = actionButton("플로팅 종료") {
            stopService(Intent(this, FloatingAssistantService::class.java))
            refreshStatus()
        }
        val import = actionButton("기존 Unity 파일 선택 (실험용)") { chooseBundle() }
        val note = TextView(this).apply {
            text = "현재 캐릭터 영역은 UI 프로토타입입니다. 실제 Live2D 모델과 AI 연결은 후속 개발 단계에서 추가됩니다."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF9DB2C8.toInt())
            setPadding(0, dp(22), 0, 0)
        }

        root.addView(eyebrow, fullWidth())
        root.addView(title, fullWidth())
        root.addView(intro, fullWidth())
        root.addView(status, fullWidth())
        root.addView(overlay, fullWidth())
        root.addView(start, fullWidth())
        root.addView(stop, fullWidth())
        root.addView(import, fullWidth())
        root.addView(note, fullWidth())
        setContentView(root)
        refreshStatus()
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun fullWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun chooseBundle() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, pickFile)
    }

    @Deprecated("Legacy callback retained for compatibility with the existing file picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFile && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    prefs.edit().putString("bundle_uri", uri.toString()).apply()
                    refreshStatus()
                } catch (_: SecurityException) {
                    status.text = "파일 접근 권한을 저장하지 못했어요. 다시 선택해 주세요."
                }
            }
        }
    }

    private fun refreshStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val bundleSelected = prefs.getString("bundle_uri", null) != null
        status.text = buildString {
            append(if (overlayGranted) "화면 위 표시 권한: 허용됨" else "화면 위 표시 권한: 필요")
            append("\n")
            append(if (bundleSelected) "Unity 파일: 선택됨 (아직 렌더링하지 않음)" else "Unity 파일: 미선택")
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "먼저 화면 위 표시 권한을 허용해 주세요."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            startForegroundService(Intent(this, FloatingAssistantService::class.java))
            status.text = "아리스 플로팅을 시작했어요. 한 번 터치하면 캐릭터가 펼쳐지고, 두 번 터치하면 채팅이 열려요."
        } catch (_: SecurityException) {
            status.text = "Android가 서비스 실행을 허용하지 않았어요. 권한과 알림 설정을 확인해 주세요."
        } catch (_: IllegalStateException) {
            status.text = "지금은 백그라운드 서비스를 시작할 수 없어요. 앱을 다시 열고 시도해 주세요."
        }
    }
}
