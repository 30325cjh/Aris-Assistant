package com.arisassistant

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingAssistantService : Service() {
    companion object {
        const val ACTION_STOP = "com.arisassistant.action.STOP_OVERLAY"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "aris_assistant"
    }

    private var windowManager: WindowManager? = null
    private var appOpsManager: AppOpsManager? = null
    private val positionPrefs by lazy { getSharedPreferences("aris_overlay", MODE_PRIVATE) }
    private val overlayPermissionListener = AppOpsManager.OnOpChangedListener { op, _ ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && !Settings.canDrawOverlays(this)) {
            stopSelf()
        }
    }
    private var avatarView: TextView? = null
    private var chatPanel: LinearLayout? = null
    private var avatarParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var currentExpression = "기본표정"

    private var downWindowX = 0
    private var downWindowY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private var draggedThisGesture = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        watchOverlayPermission()
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

    private fun watchOverlayPermission() {
        val manager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        manager.startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            packageName,
            overlayPermissionListener
        )
        appOpsManager = manager
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aris AI", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aris AI floating assistant service status and stop control"
            }
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aris AI 실행 중")
            .setContentText("화면 위 캐릭터 도우미가 실행 중이에요.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "종료",
                stopPendingIntent
            )
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(color)
            setStroke(dp(1), 0xFF58D9F2.toInt())
        }

    private fun hasOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        stopSelf()
        return false
    }

    private fun showAvatar() {
        if (!hasOverlayPermission() || avatarView != null) return

        val avatar = TextView(this).apply {
            text = "ARIS"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(0xEE102B50.toInt())
            contentDescription = "Aris AI 실행 아이콘. 한 번 터치하면 캐릭터 표시, 펼쳐진 캐릭터를 두 번 터치하면 채팅"
            elevation = dp(10).toFloat()
        }
        val params = WindowManager.LayoutParams(
            dp(76),
            dp(60),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = positionPrefs.getInt("x", dp(18))
            y = positionPrefs.getInt("y", dp(180))
        }
        clampAvatarPosition(params)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val gestures = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!hasOverlayPermission()) return true
                    performGestureAction(resolveSingleTapAction(expanded, chatPanel != null))
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!hasOverlayPermission()) return true
                    performGestureAction(resolveDoubleTapAction(expanded, chatPanel != null))
                    return true
                }
            }
        )

        avatar.setOnTouchListener { view, event ->
            if (!hasOverlayPermission()) return@setOnTouchListener true
            gestures.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downWindowX = params.x
                    downWindowY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    draggedThisGesture = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        dragging = true
                        draggedThisGesture = true
                    }
                    if (dragging) {
                        // With TOP|END gravity, x is the distance from the right edge.
                        val metrics = resources.displayMetrics
                        val position = clampOverlayPosition(
                            x = downWindowX - deltaX.toInt(),
                            y = downWindowY + deltaY.toInt(),
                            screenWidth = metrics.widthPixels,
                            screenHeight = metrics.heightPixels,
                            windowWidth = params.width,
                            windowHeight = params.height
                        )
                        params.x = position.x
                        params.y = position.y
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                            stopSelf()
                        }
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (draggedThisGesture) persistAvatarPosition(params)
                    dragging = false
                    draggedThisGesture = false
                }
            }
            true
        }

        avatarView = avatar
        avatarParams = params
        try {
            windowManager?.addView(avatar, params)
        } catch (_: Exception) {
            avatarView = null
            avatarParams = null
            stopSelf()
        }
    }

    private fun performGestureAction(action: OverlayGestureAction) {
        when (action) {
            OverlayGestureAction.NONE -> Unit
            OverlayGestureAction.EXPAND -> expandCharacter()
            OverlayGestureAction.OPEN_CHAT,
            OverlayGestureAction.CLOSE_CHAT -> toggleChat()
        }
    }

    private fun clampAvatarPosition(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val position = clampOverlayPosition(
            x = params.x,
            y = params.y,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            windowWidth = params.width,
            windowHeight = params.height
        )
        params.x = position.x
        params.y = position.y
    }

    private fun persistAvatarPosition(params: WindowManager.LayoutParams) {
        positionPrefs.edit()
            .putInt("x", params.x)
            .putInt("y", params.y)
            .apply()
    }

    private fun updateAvatarPresentation() {
        val avatar = avatarView ?: return
        if (!expanded) {
            avatar.text = "ARIS"
            avatar.textSize = 17f
            avatar.background = roundedBackground(0xEE102B50.toInt())
            avatar.contentDescription = "Aris AI 실행 아이콘. 한 번 터치하면 캐릭터 표시"
            return
        }

        avatar.text = "ARIS\nMILLENNIUM\n\n캐릭터 미리보기\n테스트 표정: $currentExpression\n\n두 번 터치해서 채팅"
        avatar.textSize = 15f
        avatar.background = roundedBackground(0xF0102B50.toInt())
        avatar.contentDescription = "Aris AI 캐릭터 자리표시자. 두 번 터치하면 채팅"
    }

    private fun expandCharacter() {
        if (!hasOverlayPermission() || chatPanel != null || expanded) return
        val avatar = avatarView ?: return
        val params = avatarParams ?: return

        expanded = true
        params.width = dp(196)
        params.height = dp(250)
        clampAvatarPosition(params)
        updateAvatarPresentation()

        try {
            windowManager?.updateViewLayout(avatar, params)
            persistAvatarPosition(params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun toggleChat() {
        if (!hasOverlayPermission()) return
        if (chatPanel != null) {
            closeChat()
            return
        }
        if (!expanded) {
            expandCharacter()
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(0xF510203B.toInt())
            elevation = dp(16).toFloat()
        }
        val heading = TextView(this).apply {
            text = "ARIS AI  /  MILLENNIUM"
            textSize = 17f
            setTextColor(0xFFB8F5FF.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "텍스트 대화 UI 프로토타입 · 실제 AI 연결 전"
            textSize = 12f
            setTextColor(0xFFD5E6F5.toInt())
            setPadding(0, dp(8), 0, dp(10))
        }
        val output = TextView(this).apply {
            text = "표정 테스트 입력 대기 · 현재 값: $currentExpression"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        }
        val input = EditText(this).apply {
            hint = "테스트: 웃음, 슬픔, 놀람, 당황"
            setSingleLine(true)
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
                if (expression != null) {
                    currentExpression = expression
                    output.text = "표정 테스트 명령 감지: $currentExpression"
                    updateAvatarPresentation()
                } else {
                    output.text = "명령을 인식하지 못했어요. 웃음, 슬픔, 놀람, 당황, 질색, 무표정을 입력해 보세요."
                }
                input.text.clear()
            }
        }

        buttons.addView(close)
        buttons.addView(send)
        panel.addView(heading)
        panel.addView(subtitle)
        panel.addView(output)
        panel.addView(input)
        panel.addView(buttons)

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager?.addView(panel, params)
            chatPanel = panel
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
            try {
                windowManager?.removeView(panel)
            } catch (_: Exception) {
            }
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
        return expressions.entries.firstOrNull { (_, words) ->
            words.any { value.contains(it) }
        }?.key
    }

    private fun closeChat() {
        val panel = chatPanel ?: return
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(panel.windowToken, 0)
        } catch (_: Exception) {
        }
        try {
            windowManager?.removeView(panel)
        } catch (_: Exception) {
        }
        chatPanel = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val avatar = avatarView ?: return
        val params = avatarParams ?: return
        clampAvatarPosition(params)
        try {
            windowManager?.updateViewLayout(avatar, params)
            persistAvatarPosition(params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        appOpsManager?.stopWatchingMode(overlayPermissionListener)
        appOpsManager = null
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
