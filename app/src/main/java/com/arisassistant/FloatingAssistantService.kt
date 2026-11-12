package com.arisassistant

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*

class FloatingAssistantService : Service() {
    private var windowManager: WindowManager? = null
    private var avatarView: TextView? = null
    private var chatPanel: LinearLayout? = null
    private var avatarParams: WindowManager.LayoutParams? = null
    private var chatParams: WindowManager.LayoutParams? = null
    private val channelId = "aris_assistant"
    private var currentExpression = "기본표정"
    private var scale = 1f
    private var lastX = 0
    private var lastY = 0
    private var startRawX = 0f
    private var startRawY = 0f

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Aris Assistant", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId).setContentTitle("Aris Assistant 실행 중").setContentText("플로팅 어시스턴트를 표시하고 있어요.").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        else Notification.Builder(this).setContentTitle("Aris Assistant 실행 중").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        startForeground(1, notification)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showAvatar()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showAvatar() {
        val avatar = TextView(this).apply {
            text = "아리스\n3D 모델 연결 대기"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xCC6750A4.toInt())
            contentDescription = "Aris Assistant 캐릭터 영역. 한 손가락으로 이동, 두 손가락으로 크기 조절, 두 번 눌러 채팅"
        }
        avatarView = avatar
        val params = WindowManager.LayoutParams(
            dp(132), dp(88), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(18); y = dp(180)
        }
        avatarParams = params

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleChat()
                return true
            }
        })
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = (scale * detector.scaleFactor).coerceIn(0.65f, 2.2f)
                avatar.scaleX = scale
                avatar.scaleY = scale
                return true
            }
        })
        avatar.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastX = params.x
                lastY = params.y
                startRawX = event.rawX
                startRawY = event.rawY
            } else if (event.action == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                params.x = (lastX + startRawX - event.rawX).toInt().coerceAtLeast(0)
                params.y = (lastY + event.rawY - startRawY).toInt().coerceAtLeast(0)
                try { windowManager?.updateViewLayout(avatar, params) } catch (_: Exception) {}
            }
            true
        }
        windowManager?.addView(avatar, params)
    }

    private fun toggleChat() {
        if (chatPanel != null) {
            closeChat()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xF5FFFFFF.toInt())
        }
        val heading = TextView(this).apply {
            text = "Aris Assistant 채팅"
            textSize = 18f
            setTextColor(0xFF34265E.toInt())
        }
        val hint = TextView(this).apply {
            text = "현재는 표정 명령 테스트 UI예요. 실제 3D 표정 연결은 다음 단계에서 진행해요."
            textSize = 12f
            setTextColor(0xFF555555.toInt())
            setPadding(0, dp(6), 0, dp(8))
        }
        val output = TextView(this).apply {
            text = "현재 표정: $currentExpression"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(8))
        }
        val input = EditText(this).apply {
            hint = "예: 웃는거, 놀란거, 무표정"
            setSingleLine(true)
            textSize = 14f
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val close = Button(this).apply { text = "닫기"; setOnClickListener { closeChat() } }
        val send = Button(this).apply {
            text = "보내기"
            setOnClickListener {
                val expression = matchExpression(input.text.toString())
                if (expression != null) {
                    currentExpression = expression
                    output.text = "현재 표정: $currentExpression"
                    avatarView?.setText("아리스\n$currentExpression")
                } else {
                    output.text = "표정 명령을 찾지 못했어요. 기본표정, 웃는거, 우는거, 놀란거, 당황한거, 질색하는 표정을 입력해 보세요."
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
            dp(300), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        chatPanel = panel
        chatParams = params
        try {
            windowManager?.addView(panel, params)
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
            chatPanel = null
            chatParams = null
        }
    }

    private fun matchExpression(text: String): String? {
        val value = text.trim().replace(" ", "")
        val expressions = linkedMapOf(
            "웃는거" to listOf("웃는거", "웃음", "웃는표정", "미소"),
            "우는거" to listOf("우는거", "울음", "우는표정", "슬픔"),
            "놀란거" to listOf("놀란거", "놀람", "놀란표정"),
            "당황한거" to listOf("당황한거", "당황", "당황한표정"),
            "질색하는표정" to listOf("질색하는표정", "질색", "싫어하는표정"),
            "무표정" to listOf("무표정", "덤덤한표정"),
            "기본표정" to listOf("기본표정", "기본", "초기화")
        )
        return expressions.entries.firstOrNull { (_, words) -> words.any { value.contains(it) } }?.key
    }

    private fun closeChat() {
        chatPanel?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        chatPanel = null
        chatParams = null
    }

    override fun onDestroy() {
        closeChat()
        avatarView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        avatarView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
