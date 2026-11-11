package com.arisassistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color

class MainActivity : Activity() {
    private val pickFile = 1001
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("aris", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(248, 247, 255))
        }
        val title = TextView(this).apply {
            text = "Aris Assistant"
            textSize = 28f
            setTextColor(Color.rgb(55, 45, 95))
            gravity = Gravity.CENTER
        }
        val intro = TextView(this).apply {
            text = "아리스 어시스턴트 초기 버전\nUnity AssetBundle 파일을 선택하고 화면 위에 떠 있는 실행 아이콘을 켤 수 있어요."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        status = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 20) }
        val import = Button(this).apply { text = " .unity3d 파일 불러오기"; setOnClickListener { chooseBundle() } }
        val overlay = Button(this).apply { text = "화면 위 표시 권한 설정"; setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } }
        val start = Button(this).apply { text = "플로팅 아이콘 실행"; setOnClickListener { startOverlay() } }
        root.addView(title); root.addView(intro); root.addView(status)
        root.addView(import); root.addView(overlay); root.addView(start)
        setContentView(root)
        refreshStatus()
    }

    private fun chooseBundle() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, pickFile)
    }

    @Deprecated("Legacy callback kept for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFile && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("bundle_uri", uri.toString()).apply()
                refreshStatus()
            }
        }
    }

    private fun refreshStatus() {
        status.text = if (prefs.getString("bundle_uri", null) != null)
            "Unity 파일이 선택되어 있어요.\n(현재 버전은 파일 보관 단계이며 실제 3D 렌더링은 아직 지원하지 않아요.)"
        else "아직 Unity 파일을 선택하지 않았어요."
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "먼저 화면 위 표시 권한을 허용해 주세요."
            return
        }
        startForegroundService(Intent(this, FloatingAssistantService::class.java))
        status.text = "플로팅 아이콘을 실행했어요."
    }
}
