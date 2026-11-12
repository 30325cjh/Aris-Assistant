package com.arisassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FloatingAssistantService : Service() {
    companion object {
        const val ACTION_STOP = "com.arisassistant.action.STOP_OVERLAY"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "aris_assistant"
    }

    private var windowManager: WindowManager? = null
    private var avatarView: TextView? = null
    private var chatPanel: LinearLayout? = null
    private var avatarParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var currentExpression = "기본표정"
    private var lastX = 0
    private var lastY = 0
    private var startRawX = 0f
    private var startRawY = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        showAvatar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (avatarView == null) showAvatar()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aris Assistant", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingAssistantService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aris AI 실행 중")
            .setContentText("화면 위 캐릭터 도우미가 실행 중이에요.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "종료", stopPendingIntent)
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showAvatar() {
        if (!Settings.canDrawOverlays(this) || avatarView != null) return
        val avatar = TextView(this).apply {
            text = "ARIS"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xEE102B50.toInt())
            contentDescription = "Aris AI. 한 번 터치하면 캐릭터 표시, 두 번 터치하면 채팅"
            elevation = dp(10).toFloat()
        }
        avatarView = avatar
        val params = WindowManager.LayoutParams(
            dp(76), dp(60), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(18)
            y = dp(180)
        }
        avatarParams = params

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleExpanded()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleChat()
                return true
            }
        })

        avatar.setOnTouchListener { view, event ->
            gestures.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = params.x
                    lastY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) {
                        val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                        params.x = (lastX - dx.toInt()).coerceIn(0, maxX)
                        params.y = (lastY + dy.toInt()).coerceIn(0, maxY)
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                            stopSelf()
                        }
                    }
                }
            }
            true
        }

        try {
            windowManager?.addView(avatar, params)
        } catch (_: Exception) {
            avatarView = null
            stopSelf()
        }
    }

    private fun toggleExpanded() {
        val avatar = avatarView ?: return
        val params = avatarParams ?: return
        expanded = !expanded
        if (expanded) {
            params.width = dp(196)
            params.height = dp(250)
            avatar.text = "ARIS\nMILLENNIUM\n\n캐릭터 미리보기\n\n두 번 터치해서 대화"
            avatar.textSize = 16f
            avatar.setBackgroundColor(0xF0102B50.toInt())
            avatar.contentDescription = "Aris AI 캐릭터 미리보기. 두 번 터치하면 채팅"
        } else {
            params.width = dp(76)
            params.height = dp(60)
            avatar.text = "ARIS"
            avatar.textSize = 17f
            avatar.setBackgroundColor(0xEE102B50.toInt())
            avatar.contentDescription = "Aris AI. 한 번 터치하면 캐릭터 표시, 두 번 터치하면 채팅"
        }
        try {
            windowManager?.updateViewLayout(avatar, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun toggleChat() {
        if (chatPanel != null) {
            closeChat()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xF510203B.toInt())
            elevation = dp(16).toFloat()
        }
        val heading = TextView(this).apply {
            text = "ARIS AI  /  MILLENNIUM"
            textSize = 17f
            setTextColor(0xFFB8F5FF.toInt())
        }
        val hint = TextView(this).apply {
            text = "텍스트 대화 인터페이스 준비 중"
            textSize = 12f
            setTextColor(0xFFD5E6F5.toInt())
            setPadding(0, dp(8), 0, dp(10))
        }
        val output = TextView(this).apply {
            text = "현재 표정: $currentExpression"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        }
        val input = EditText(this).apply {
            hint = "메시지 입력"
            singleLine = true
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9DB2C8.toInt())
            setBackgroundColor(0xFF1A3555.toInt())
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val close = Button(this).apply {
            text = "닫기"
            setOnClickListener { closeChat() }
        }
        val send = Button(this).apply {
            text = "테스트"
            setOnClickListener {
                val expression = matchExpression(input.text.toString())
                output.text = if (expression != null) {
                    currentExpression = expression
                    "현재 표정: $currentExpression"
                } else {
                    "아직 AI 연결 전이에요. 표정 테스트: 웃음, 슬픔, 놀람, 당황, 무표정"
                }
                input.text.clear()
            }
        }
        buttons.addView(close)
        buttons.addView(send)
        panel.addView(heading)
        panel.addView(hint)
        panel.addView(output)
        panel.addView(input)
        panel.addView(buttons)

        val params = WindowManager.LayoutParams(
            dp(300), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        chatPanel = panel
        try {
            windowManager?.addView(panel, params)
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
            chatPanel = null
        }
    }

    private fun matchExpression(text: String): String? {
        val value = text.trim().replace(" ", "")
        val expressions = linkedMapOf(
            "웃는표정" to listOf("웃는거", "웃음", "웃는표정", "미소"),
            "슬픈표정" to listOf("우는거", "울음", "우는표정", "슬픔"),
            "놀란표정" to listOf("놀란거", "놀람", "놀란표정"),
            "당황한표정" to listOf("당황한거", "당황", "당황한표정"),
            "질색하는표정" to listOf("질색하는표정", "질색", "싫어하는표정"),
            "무표정" to listOf("무표정", "덤덤한표정"),
            "기본표정" to listOf("기본표정", "기본", "초기화")
        )
        return expressions.entries.firstOrNull { (_, words) -> words.any { value.contains(it) } }?.key
    }

    private fun closeChat() {
        chatPanel?.let { view ->
            try {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        chatPanel = null
    }

    override fun onDestroy() {
        closeChat()
        avatarView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        avatarView = null
        avatarParams = null
        windowManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
