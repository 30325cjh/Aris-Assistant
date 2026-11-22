package com.arisassistant

import com.arisassistant.ai.AssistantRequest
import com.arisassistant.ai.ConversationRole
import com.arisassistant.ai.ConversationTurn
import com.arisassistant.ai.GeminiApiKeyStore
import com.arisassistant.ai.GeminiProvider
import com.arisassistant.ai.GeminiProviderException
import com.arisassistant.ai.normalizedForGemini
import com.arisassistant.data.ConversationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import com.arisassistant.character.CharacterRenderer
import com.arisassistant.character.PlaceholderCharacterRenderer
import com.arisassistant.character.characterPresentationFor

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
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingAssistantService : Service() {
    companion object {
        const val ACTION_STOP = "com.arisassistant.action.STOP_OVERLAY"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "aris_assistant"
    }

    private var windowManager: WindowManager? = null
    private var appOpsManager: AppOpsManager? = null
    private var cachedOverlayDisplayArea: OverlayDisplayArea? = null
    private val positionPrefs by lazy { getSharedPreferences("aris_overlay", MODE_PRIVATE) }
    private lateinit var conversationStore: ConversationStore
    private var activeConversationId = ""
    private var conversationGeneration = 0
    private val overlayPermissionListener = AppOpsManager.OnOpChangedListener { op, _ ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && !Settings.canDrawOverlays(this)) {
            stopSelf()
        }
    }
    private var avatarView: View? = null
    private var characterRenderer: CharacterRenderer? = null
    private var characterRendererAttached = false
    private var chatPanel: LinearLayout? = null
    private var avatarParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var currentExpression = "기본표정"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chatHistory = mutableListOf<ConversationTurn>()

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
        try {
            conversationStore = ConversationStore(this)
            loadActiveConversation()
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "대화 저장소를 안전하게 열지 못해 플로팅을 시작하지 않았어요. 기존 데이터는 삭제하지 않았습니다.",
                Toast.LENGTH_LONG
            ).show()
            stopForeground(STOP_FOREGROUND_REMOVE)
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
        if (!::conversationStore.isInitialized) {
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
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "종료",
                    stopPendingIntent
                ).build()
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

        val renderer = PlaceholderCharacterRenderer(this)
        renderer.render(currentCharacterPresentation())
        val avatar = renderer.view
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
        var doubleTapDetected = false
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
                    // GestureDetector reports a double tap on the second ACTION_DOWN.
                    // Defer the action until ACTION_UP so a second touch that becomes
                    // a drag cannot accidentally open or close the chat.
                    doubleTapDetected = true
                    return true
                }
            }
        )

        avatar.setOnTouchListener { view, event ->
            if (!hasOverlayPermission()) return@setOnTouchListener true
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                // Reset before GestureDetector processes this down; onDoubleTap may
                // set it again during this same event.
                doubleTapDetected = false
            }
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
                        // Reuse the same safe-area clamp used at launch and expansion.
                        params.x = downWindowX - deltaX.toInt()
                        params.y = downWindowY + deltaY.toInt()
                        clampAvatarPosition(params)
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                            stopSelf()
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val wasDragged = draggedThisGesture
                    if (wasDragged) persistAvatarPosition(params)
                    val completedDoubleTap = resolveDoubleTapCompletionAction(
                        doubleTapDetected = doubleTapDetected,
                        dragged = wasDragged,
                        expanded = expanded,
                        chatOpen = chatPanel != null
                    )
                    doubleTapDetected = false
                    dragging = false
                    draggedThisGesture = false
                    if (completedDoubleTap != OverlayGestureAction.NONE) {
                        performGestureAction(completedDoubleTap)
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    // Keep the last drag position even when Android cancels the gesture.
                    if (draggedThisGesture) persistAvatarPosition(params)
                    doubleTapDetected = false
                    dragging = false
                    draggedThisGesture = false
                }
            }
            true
        }

        avatarView = avatar
        avatarParams = params
        characterRenderer = renderer
        try {
            val manager = windowManager ?: throw IllegalStateException("WindowManager is unavailable")
            manager.addView(avatar, params)
            characterRendererAttached = true
            renderer.onHostAttached()
        } catch (_: Exception) {
            if (characterRendererAttached) {
                try {
                    renderer.onHostDetached()
                } catch (_: Exception) {
                }
                try {
                    windowManager?.removeView(avatar)
                } catch (_: Exception) {
                }
            }
            characterRendererAttached = false
            try {
                renderer.dispose()
            } catch (_: Exception) {
            }
            avatarView = null
            avatarParams = null
            characterRenderer = null
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
        val area = overlayDisplayArea()
        val position = clampOverlayPosition(
            x = params.x,
            y = params.y,
            screenWidth = area.width,
            screenHeight = area.height,
            windowWidth = params.width,
            windowHeight = params.height,
            insetLeft = area.insetLeft,
            insetTop = area.insetTop,
            insetRight = area.insetRight,
            insetBottom = area.insetBottom
        )
        params.x = position.x
        params.y = position.y
    }

    /**
     * WindowMetrics includes the display bounds and the system-bar/cutout
     * insets. Cache it during gestures and refresh it after configuration
     * changes; API 26-29 retain the legacy full-display metrics fallback.
     */
    private fun overlayDisplayArea(): OverlayDisplayArea {
        cachedOverlayDisplayArea?.let { return it }

        val modernArea = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val metrics = (windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager)
                    .currentWindowMetrics
                val bounds = metrics.bounds
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                OverlayDisplayArea(
                    width = bounds.width(),
                    height = bounds.height(),
                    insetLeft = insets.left,
                    insetTop = insets.top,
                    insetRight = insets.right,
                    insetBottom = insets.bottom
                )
            }.getOrNull()
        } else {
            null
        }

        val area = modernArea ?: resources.displayMetrics.let { metrics ->
            OverlayDisplayArea(
                width = metrics.widthPixels,
                height = metrics.heightPixels
            )
        }
        cachedOverlayDisplayArea = area
        return area
    }

    private fun persistAvatarPosition(params: WindowManager.LayoutParams) {
        positionPrefs.edit()
            .putInt("x", params.x)
            .putInt("y", params.y)
            .apply()
    }

    private fun currentCharacterPresentation() =
        characterPresentationFor(expanded = expanded, expressionLabel = currentExpression)

    private fun updateAvatarPresentation() {
        characterRenderer?.render(currentCharacterPresentation())
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
        loadActiveConversation()

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
            text = "Gemini 텍스트 채팅 · 표정 테스트 명령도 지원"
            textSize = 12f
            setTextColor(0xFFD5E6F5.toInt())
            setPadding(0, dp(8), 0, dp(10))
        }
        val output = TextView(this).apply {
            text = renderChatHistory().ifBlank {
                if (GeminiApiKeyStore(this@FloatingAssistantService).hasSavedKey()) {
                    "메시지를 입력하면 Gemini가 답변해요."
                } else {
                    "먼저 앱에서 Gemini API 키를 설정해 주세요."
                }
            }
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        }
        val outputScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(output)
        }
        val input = EditText(this).apply {
            hint = "메시지 입력 · 웃음/슬픔 등 표정 테스트"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9DB2C8.toInt())
            setBackgroundColor(0xFF1A3555.toInt())
        }
        val historyActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val clearHistory = Button(this).apply {
            text = "현재 대화 비우기"
            setOnClickListener {
                conversationGeneration += 1
                conversationStore.clearConversation(activeConversationId)
                chatHistory.clear()
                output.text = "현재 대화의 메시지를 기기에서 삭제했어요."
            }
        }
        val newConversation = Button(this).apply {
            text = "새 대화"
            setOnClickListener {
                activeConversationId = conversationStore.createConversation()
                chatHistory.clear()
                output.text = "새 대화를 시작했어요."
            }
        }
        historyActions.addView(clearHistory)
        historyActions.addView(newConversation)

        val managementActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val manageMemory = Button(this).apply {
            text = "대화·기억 관리"
            setOnClickListener {
                closeChat()
                try {
                    startActivity(
                        Intent(this@FloatingAssistantService, ConversationMemoryActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    stopSelf()
                }
            }
        }
        managementActions.addView(manageMemory)

        val chatActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val close = Button(this).apply {
            text = "닫기"
            setOnClickListener { closeChat() }
        }
        val send = Button(this).apply {
            text = "보내기"
        }
        send.setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isEmpty()) {
                output.text = "메시지를 입력해 주세요."
                return@setOnClickListener
            }

            val expression = matchExpression(message)
            if (expression != null) {
                currentExpression = expression
                output.text = "로컬 표정 테스트: $currentExpression"
                updateAvatarPresentation()
            } else {
                sendGeminiMessage(message, output, send)
            }
            input.text.clear()
        }
        chatActions.addView(close)
        chatActions.addView(send)

        panel.addView(heading)
        panel.addView(subtitle)
        panel.addView(outputScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(160)
        ))
        panel.addView(input)
        panel.addView(historyActions)
        panel.addView(managementActions)
        panel.addView(chatActions)

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
            // Wait until WindowManager attaches the overlay before requesting the IME.
            panel.post {
                if (chatPanel === panel) {
                    input.requestFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        } catch (_: Exception) {
            try {
                windowManager?.removeView(panel)
            } catch (_: Exception) {
            }
            chatPanel = null
        }
    }

    private fun sendGeminiMessage(message: String, output: TextView, sendButton: Button) {
        val keyStore = GeminiApiKeyStore(this)
        if (!keyStore.hasSavedKey()) {
            output.text = "Gemini API 키가 없어요. 앱 홈 화면의 Gemini API 키 설정에서 먼저 등록해 주세요."
            return
        }

        val request = try {
            AssistantRequest(
                userMessage = message,
                recentHistory = chatHistory.toList(),
                savedMemories = conversationStore.enabledMemoryContext()
            ).normalizedForGemini()
        } catch (_: IllegalArgumentException) {
            output.text = "메시지가 비어 있어요. 다시 입력해 주세요."
            return
        }

        val requestConversationId = activeConversationId
        val requestGeneration = conversationGeneration
        val userTurn = ConversationTurn(ConversationRole.USER, request.userMessage)
        val storedUserMessage = try {
            conversationStore.appendMessage(
                requestConversationId,
                ConversationRole.USER,
                request.userMessage
            )
        } catch (_: Exception) {
            output.text = "대화를 기기에 저장하지 못했어요. 저장 공간을 확인한 뒤 다시 시도해 주세요."
            return
        }

        chatHistory.add(userTurn)
        sendButton.isEnabled = false
        output.text = renderChatHistory() + "\n\n아리스가 답변을 준비하고 있어요..."

        serviceScope.launch {
            try {
                val response = GeminiProvider(keyStore).generateReply(request)
                if (requestGeneration != conversationGeneration) {
                    conversationStore.deleteMessage(storedUserMessage.id)
                    return@launch
                }
                val assistantTurn = ConversationTurn(ConversationRole.ASSISTANT, response.message)
                var persistenceWarning = false
                try {
                    conversationStore.appendMessage(
                        requestConversationId,
                        ConversationRole.ASSISTANT,
                        response.message
                    )
                } catch (_: Exception) {
                    persistenceWarning = true
                }
                if (activeConversationId == requestConversationId) {
                    chatHistory.add(assistantTurn)
                    trimChatHistory()
                    output.text = renderChatHistory()
                    if (persistenceWarning) {
                        output.append("\n\n답변은 받았지만 기기에 저장하지 못했어요.")
                    }
                }
            } catch (cancelled: CancellationException) {
                removeFailedUserTurn(userTurn, storedUserMessage.id, requestConversationId)
                throw cancelled
            } catch (error: GeminiProviderException) {
                removeFailedUserTurn(userTurn, storedUserMessage.id, requestConversationId)
                if (activeConversationId == requestConversationId && requestGeneration == conversationGeneration) {
                    output.text = error.userMessage
                }
            } catch (_: Exception) {
                removeFailedUserTurn(userTurn, storedUserMessage.id, requestConversationId)
                if (activeConversationId == requestConversationId && requestGeneration == conversationGeneration) {
                    output.text = "답변을 가져오지 못했어요. 네트워크와 API 키를 확인한 뒤 다시 시도해 주세요."
                }
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun removeFailedUserTurn(
        turn: ConversationTurn,
        storedMessageId: String,
        requestConversationId: String
    ) {
        if (activeConversationId == requestConversationId && chatHistory.lastOrNull() == turn) {
            chatHistory.removeAt(chatHistory.lastIndex)
        }
        try {
            conversationStore.deleteMessage(storedMessageId)
        } catch (_: Exception) {
        }
    }

    private fun loadActiveConversation() {
        activeConversationId = conversationStore.activeConversationId()
        chatHistory.clear()
        chatHistory.addAll(
            conversationStore.listMessages(activeConversationId, 20).map { stored ->
                ConversationTurn(stored.role, stored.text)
            }
        )
    }

    private fun renderChatHistory(): String =
        chatHistory.takeLast(20).joinToString("\n\n") { turn ->
            val speaker = if (turn.role == ConversationRole.USER) "나" else "아리스"
            "$speaker: ${turn.text}"
        }

    private fun trimChatHistory() {
        while (chatHistory.size > 20) {
            chatHistory.removeAt(0)
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
        cachedOverlayDisplayArea = null
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
        serviceScope.cancel()
        appOpsManager?.stopWatchingMode(overlayPermissionListener)
        appOpsManager = null
        closeChat()
        if (::conversationStore.isInitialized) {
            try {
                conversationStore.close()
            } catch (_: Exception) {
            }
        }
        val renderer = characterRenderer
        if (characterRendererAttached) {
            try {
                renderer?.onHostDetached()
            } catch (_: Exception) {
            }
            characterRendererAttached = false
        }
        avatarView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        try {
            renderer?.dispose()
        } catch (_: Exception) {
        }
        characterRenderer = null
        avatarView = null
        avatarParams = null
        windowManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
