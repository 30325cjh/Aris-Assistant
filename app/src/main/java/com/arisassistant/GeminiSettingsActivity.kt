package com.arisassistant

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType
import android.content.Intent
import android.net.Uri
import com.arisassistant.ai.GeminiApiKeyStore

class GeminiSettingsActivity : Activity() {
    private val keyStore by lazy { GeminiApiKeyStore(this) }
    private lateinit var status: TextView
    private lateinit var keyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            setBackgroundColor(0xFF08172D.toInt())
        }

        val eyebrow = TextView(this).apply {
            text = "MILLENNIUM  /  PROVIDER SETTINGS"
            textSize = 11f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(0xFF83DDF2.toInt())
        }
        val title = TextView(this).apply {
            text = "GEMINI CONNECTION"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(10))
        }
        val description = TextView(this).apply {
            text = "개인 Gemini API 키를 이 기기에 저장해 텍스트 대화에 사용합니다. 키는 Android Keystore의 AES-GCM 키로 암호화하며, 앱 코드나 저장소에 포함하지 않습니다."
            textSize = 13f
            setTextColor(0xFFD5E6F5.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8F5FF.toInt())
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setBackgroundColor(0xFF102B50.toInt())
        }
        keyInput = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9DB2C8.toInt())
            setBackgroundColor(0xFF1A3555.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val save = actionButton("키 암호화 저장") { saveKey() }
        val clear = actionButton("저장된 키 삭제") { clearKey() }
        val getKey = actionButton("Google AI Studio에서 키 발급") {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://aistudio.google.com/app/apikey")
                )
            )
        }
        val warning = TextView(this).apply {
            text = "주의: 기기에 저장한 키는 암호화되지만, 루팅·디버깅·변조된 기기에서는 앱이 사용하는 키가 노출될 수 있습니다. 개인용으로 사용하고 Google 쪽에서 키 사용 범위를 제한해 주세요. 키를 채팅에 입력하지 마세요."
            textSize = 12f
            setTextColor(0xFFFFD28A.toInt())
            setPadding(0, dp(18), 0, 0)
        }

        root.addView(eyebrow, fullWidth())
        root.addView(title, fullWidth())
        root.addView(description, fullWidth())
        root.addView(status, fullWidth())
        root.addView(keyInput, fullWidth())
        root.addView(save, fullWidth())
        root.addView(clear, fullWidth())
        root.addView(getKey, fullWidth())
        root.addView(warning, fullWidth())

        setContentView(root)
        refreshStatus()
    }

    private fun saveKey() {
        try {
            keyStore.save(keyInput.text.toString())
            keyInput.text.clear()
            hideKeyboard()
            status.text = "Gemini API 키가 이 기기에 암호화 저장되었어요."
        } catch (_: IllegalArgumentException) {
            status.text = "API 키를 입력해 주세요."
        } catch (_: Exception) {
            status.text = "키를 안전하게 저장하지 못했어요. 다시 시도해 주세요."
        }
    }

    private fun clearKey() {
        try {
            keyStore.clear()
            keyInput.text.clear()
            hideKeyboard()
            status.text = "저장된 Gemini API 키를 삭제했어요."
        } catch (_: Exception) {
            status.text = "저장된 키를 삭제하지 못했어요. 다시 시도해 주세요."
        }
    }

    private fun refreshStatus() {
        status.text = if (keyStore.hasSavedKey()) {
            "Gemini API 키: 이 기기에 암호화 저장됨"
        } else {
            "Gemini API 키: 미등록"
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(keyInput.windowToken, 0)
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
}
