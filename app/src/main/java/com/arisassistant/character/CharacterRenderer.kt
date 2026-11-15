package com.arisassistant.character

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

internal enum class CharacterDisplayMode {
    LAUNCHER,
    EXPANDED
}

internal data class CharacterPresentationState(
    val mode: CharacterDisplayMode,
    val expressionLabel: String
)

/**
 * Contract between the floating host and a replaceable character renderer.
 *
 * The host owns the WindowManager window. A renderer owns only its view and
 * renderer-specific resources. Lifecycle callbacks bracket attachment to that
 * host; dispose is called once when the service releases the renderer.
 */
internal interface CharacterRenderer {
    val view: View

    fun render(state: CharacterPresentationState)
    fun onHostAttached()
    fun onHostDetached()
    fun dispose()
}

/**
 * Safe M2 fallback. This is deliberately a UI placeholder, not a Live2D model.
 * A verified runtime can implement CharacterRenderer without changing the
 * floating service's gesture, position, or chat-window code.
 */
internal class PlaceholderCharacterRenderer(
    private val context: Context
) : CharacterRenderer {
    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        elevation = dp(10).toFloat()
    }

    override val view: View
        get() = label

    override fun render(state: CharacterPresentationState) {
        val expanded = state.mode == CharacterDisplayMode.EXPANDED
        label.setText(
            if (expanded) {
                "ARIS\nMILLENNIUM\n\n캐릭터 렌더러 자리표시자\n표정 테스트: " +
                    state.expressionLabel +
                    "\n\n두 번 터치해서 채팅"
            } else {
                "ARIS"
            }
        )
        label.textSize = if (expanded) 15f else 17f
        label.setPadding(dp(12), dp(10), dp(12), dp(10))
        label.background = roundedBackground(
            if (expanded) 0xF0102B50.toInt() else 0xEE102B50.toInt()
        )
        label.contentDescription = if (expanded) {
            "Aris AI 캐릭터 렌더러 자리표시자. 두 번 터치하면 채팅"
        } else {
            "Aris AI 실행 아이콘. 한 번 터치하면 캐릭터 표시"
        }
    }

    override fun onHostAttached() = Unit

    override fun onHostDetached() = Unit

    override fun dispose() {
        label.setOnTouchListener(null)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(color)
            setStroke(dp(1), 0xFF58D9F2.toInt())
        }
}
