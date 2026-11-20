package com.arisassistant

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.arisassistant.data.ConversationStore
import com.arisassistant.data.SavedMemory
import com.arisassistant.data.StoredConversation
import com.arisassistant.data.StoredMessage

class ConversationMemoryActivity : Activity() {
    private lateinit var store: ConversationStore
    private lateinit var status: TextView
    private lateinit var conversationList: LinearLayout
    private lateinit var memoryList: LinearLayout
    private lateinit var memoryInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            store = ConversationStore(this)
            buildScreen()
            refreshLists()
        } catch (_: Exception) {
            showStorageUnavailable()
        }
    }

    private fun showStorageUnavailable() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            setBackgroundColor(0xFF08172D.toInt())
        }
        val title = TextView(this).apply {
            text = "대화 저장소를 열 수 없어요"
            textSize = 22f
            setTextColor(Color.WHITE)
        }
        val message = TextView(this).apply {
            text = "저장된 대화와 기억을 안전하게 복호화하지 못했습니다. 기존 데이터를 자동으로 삭제하거나 새 키로 덮어쓰지 않았어요. Android Keystore 키가 손실되었거나 데이터가 손상된 경우 복구할 수 없으니, 앱 데이터 삭제나 재설치를 먼저 하지 마세요."
            textSize = 14f
            setTextColor(0xFFD5E6F5.toInt())
            setPadding(0, dp(16), 0, dp(20))
        }
        val close = actionButton("닫기") { finish() }
        content.addView(title, fullWidth())
        content.addView(message, fullWidth())
        content.addView(close, fullWidth())
        setContentView(content)
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(0xFF08172D.toInt())
        }
        val title = TextView(this).apply {
            text = "CONVERSATION  /  MEMORY"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = "대화와 기억 내용은 Android Keystore로 암호화해 이 기기에 저장됩니다."
            textSize = 13f
            setTextColor(0xFFB8F5FF.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF102B50.toInt())
        }

        content.addView(title, fullWidth())
        content.addView(status, fullWidth())
        content.addView(sectionTitle("대화"))
        content.addView(actionButton("새 대화 만들기") {
            store.createConversation()
            status.text = "새 대화를 만들고 현재 대화로 선택했어요."
            refreshLists()
        }, fullWidth())
        conversationList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(conversationList, fullWidth())

        content.addView(sectionTitle("저장된 기억"))
        val memoryDescription = TextView(this).apply {
            text = "기억은 자동으로 저장되지 않아요. 직접 추가·수정·삭제할 수 있습니다. 기본값은 Gemini에 전송하지 않음입니다."
            textSize = 12f
            setTextColor(0xFFD5E6F5.toInt())
        }
        content.addView(memoryDescription, fullWidth())
        memoryInput = EditText(this).apply {
            hint = "직접 저장할 기억 (최대 500자)"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9DB2C8.toInt())
            setBackgroundColor(0xFF1A3555.toInt())
        }
        content.addView(memoryInput, fullWidth())
        content.addView(actionButton("기억 저장") { addMemory() }, fullWidth())

        val memoryContextSwitch = Switch(this).apply {
            text = "Gemini 답변에 저장된 기억 사용"
            setTextColor(0xFFB8F5FF.toInt())
            isChecked = store.isMemoryContextEnabled()
            setOnCheckedChangeListener { _, enabled ->
                store.setMemoryContextEnabled(enabled)
                status.text = if (enabled) {
                    "저장된 기억이 다음 Gemini 요청부터 전송됩니다."
                } else {
                    "저장된 기억을 Gemini 요청에 더 이상 포함하지 않아요."
                }
            }
        }
        content.addView(memoryContextSwitch, fullWidth())
        val privacyNote = TextView(this).apply {
            text = "대화 제목·메시지·기억 내용은 Android Keystore AES-GCM으로 암호화해 기기에 저장하고 클라우드 동기화하지 않습니다. 이 옵션을 켜면 최근 10개 기억(각 최대 500자)이 Google Gemini 요청에 포함됩니다."
            textSize = 12f
            setTextColor(0xFFFFD28A.toInt())
        }
        content.addView(privacyNote, fullWidth())
        memoryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(memoryList, fullWidth())
        content.addView(actionButton("저장된 기억 모두 삭제") { confirmDeleteAllMemories() }, fullWidth())

        content.addView(actionButton("닫기") { finish() }, fullWidth())
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun refreshLists() {
        refreshConversations()
        refreshMemories()
    }

    private fun refreshConversations() {
        conversationList.removeAllViews()
        val activeId = store.activeConversationId()
        store.listConversations().forEach { conversation ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(if (conversation.id == activeId) 0xFF163A5E.toInt() else 0xFF102B50.toInt())
            }
            val label = TextView(this).apply {
                text = "${conversation.title}  ·  ${conversation.messageCount}개 메시지"
                textSize = 14f
                setTextColor(Color.WHITE)
            }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val select = actionButton(
                if (conversation.id == activeId) "사용 중" else "선택"
            ) {
                store.setActiveConversation(conversation.id)
                status.text = "선택한 대화를 플로팅 채팅에서 사용합니다."
                refreshLists()
            }
            select.isEnabled = conversation.id != activeId
            val manage = actionButton("관리") { showConversationActions(conversation) }
            actions.addView(select)
            actions.addView(manage)
            row.addView(label)
            row.addView(actions)
            conversationList.addView(row, fullWidth())
        }
    }

    private fun showConversationActions(conversation: StoredConversation) {
        AlertDialog.Builder(this)
            .setTitle(conversation.title)
            .setItems(arrayOf("내용 보기·메시지 수정", "이름 변경", "대화 삭제")) { _, which ->
                when (which) {
                    0 -> showMessages(conversation)
                    1 -> showRenameDialog(conversation)
                    2 -> confirmDeleteConversation(conversation)
                }
            }
            .show()
    }

    private fun showMessages(conversation: StoredConversation) {
        val messages = store.listMessages(conversation.id)
        if (messages.isEmpty()) {
            status.text = "이 대화에는 저장된 메시지가 없어요."
            return
        }
        val labels = messages.map { message ->
            val speaker = if (message.role.name == "USER") "나" else "아리스"
            "$speaker: ${message.text.take(120)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${conversation.title} · 메시지")
            .setItems(labels) { _, index -> showMessageEditor(messages[index]) }
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun showMessageEditor(message: StoredMessage) {
        val editor = EditText(this).apply {
            setText(message.text)
            setSelection(text.length)
            minLines = 3
            maxLines = 8
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF1A3555.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(if (message.role.name == "USER") "내 메시지 수정" else "아리스 답변 수정")
            .setView(editor)
            .setPositiveButton("저장") { _, _ ->
                try {
                    store.updateMessage(message.id, editor.text.toString())
                    status.text = "메시지를 수정했어요."
                    refreshLists()
                } catch (_: IllegalArgumentException) {
                    status.text = "메시지는 비워 둘 수 없고 8,000자를 넘을 수 없어요."
                }
            }
            .setNeutralButton("삭제") { _, _ ->
                store.deleteMessage(message.id)
                status.text = "메시지를 삭제했어요."
                refreshLists()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showRenameDialog(conversation: StoredConversation) {
        val editor = EditText(this).apply {
            setText(conversation.title)
            setSelection(text.length)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("대화 이름 변경")
            .setView(editor)
            .setPositiveButton("저장") { _, _ ->
                store.renameConversation(conversation.id, editor.text.toString())
                status.text = "대화 이름을 변경했어요."
                refreshLists()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteConversation(conversation: StoredConversation) {
        AlertDialog.Builder(this)
            .setTitle("대화를 삭제할까요?")
            .setMessage("'${conversation.title}'과 이 대화의 모든 메시지를 기기에서 삭제합니다. 이 작업은 되돌릴 수 없어요.")
            .setPositiveButton("삭제") { _, _ ->
                store.deleteConversation(conversation.id)
                status.text = "대화를 삭제했어요."
                refreshLists()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addMemory() {
        try {
            store.saveMemory(memoryInput.text.toString())
            memoryInput.text.clear()
            status.text = "기억을 기기에 저장했어요. Gemini 전송은 별도 옵션을 켜야 합니다."
            refreshMemories()
        } catch (_: IllegalArgumentException) {
            status.text = "기억은 비워 둘 수 없고 최대 500자까지 저장할 수 있어요."
        } catch (_: Exception) {
            status.text = "기억을 저장하지 못했어요. 저장 개수 제한(100개)을 확인해 주세요."
        }
    }

    private fun refreshMemories() {
        memoryList.removeAllViews()
        val memories = store.listMemories()
        if (memories.isEmpty()) {
            memoryList.addView(TextView(this).apply {
                text = "아직 저장된 기억이 없어요."
                setTextColor(0xFF9DB2C8.toInt())
            }, fullWidth())
            return
        }
        memories.forEach { memory ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(0xFF102B50.toInt())
            }
            val content = TextView(this).apply {
                text = memory.content
                textSize = 13f
                setTextColor(Color.WHITE)
            }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            actions.addView(actionButton("수정") { showMemoryEditor(memory) })
            actions.addView(actionButton("삭제") { confirmDeleteMemory(memory) })
            row.addView(content)
            row.addView(actions)
            memoryList.addView(row, fullWidth())
        }
    }

    private fun showMemoryEditor(memory: SavedMemory) {
        val editor = EditText(this).apply {
            setText(memory.content)
            setSelection(text.length)
            minLines = 2
            maxLines = 5
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF1A3555.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("기억 수정")
            .setView(editor)
            .setPositiveButton("저장") { _, _ ->
                try {
                    store.updateMemory(memory.id, editor.text.toString())
                    status.text = "기억을 수정했어요."
                    refreshMemories()
                } catch (_: IllegalArgumentException) {
                    status.text = "기억은 비워 둘 수 없고 최대 500자까지 저장할 수 있어요."
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteMemory(memory: SavedMemory) {
        AlertDialog.Builder(this)
            .setTitle("기억을 삭제할까요?")
            .setMessage("선택한 기억을 기기에서 삭제합니다.")
            .setPositiveButton("삭제") { _, _ ->
                store.deleteMemory(memory.id)
                status.text = "기억을 삭제했어요."
                refreshMemories()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteAllMemories() {
        AlertDialog.Builder(this)
            .setTitle("모든 기억을 삭제할까요?")
            .setMessage("저장된 기억 전체를 기기에서 삭제합니다.")
            .setPositiveButton("모두 삭제") { _, _ ->
                store.deleteAllMemories()
                status.text = "모든 기억을 삭제했어요."
                refreshMemories()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(0xFF83DDF2.toInt())
        setPadding(0, dp(22), 0, dp(4))
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

    override fun onDestroy() {
        if (::store.isInitialized) store.close()
        super.onDestroy()
    }
}
