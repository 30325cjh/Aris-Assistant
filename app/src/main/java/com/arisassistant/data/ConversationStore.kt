package com.arisassistant.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.arisassistant.ai.ConversationRole
import java.util.UUID

internal data class StoredConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

internal data class StoredMessage(
    val id: String,
    val conversationId: String,
    val role: ConversationRole,
    val text: String,
    val createdAt: Long
)

internal data class SavedMemory(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * App-private, device-local conversation and explicitly saved-memory storage.
 * Nothing is synced. Saved memories are not sent to Gemini unless the user
 * explicitly enables that option in ConversationMemoryActivity.
 */
internal class ConversationStore(
    context: Context,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "${databaseName}_settings",
        Context.MODE_PRIVATE
    )

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                _id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                _id TEXT PRIMARY KEY NOT NULL,
                conversation_id TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('USER', 'ASSISTANT')),
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(conversation_id) REFERENCES conversations(_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX messages_by_conversation ON messages(conversation_id, created_at)"
        )
        db.execSQL(
            """
            CREATE TABLE memories (
                _id TEXT PRIMARY KEY NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first durable schema. Future schema changes must use
        // explicit migrations here; existing user data must never be dropped.
    }

    @Synchronized
    fun activeConversationId(): String {
        val existing = preferences.getString(KEY_ACTIVE_CONVERSATION, null)
        if (existing != null && conversationExists(existing)) return existing
        return createConversation()
    }

    @Synchronized
    fun createConversation(title: String = DEFAULT_TITLE): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("_id", id)
            put("title", normalizeConversationTitle(title))
            put("created_at", now)
            put("updated_at", now)
        }
        writableDatabase.insertOrThrow("conversations", null, values)
        preferences.edit().putString(KEY_ACTIVE_CONVERSATION, id).commit()
        return id
    }

    @Synchronized
    fun setActiveConversation(id: String) {
        require(conversationExists(id)) { "Conversation does not exist." }
        check(preferences.edit().putString(KEY_ACTIVE_CONVERSATION, id).commit()) {
            "Unable to select the active conversation."
        }
    }

    @Synchronized
    fun listConversations(): List<StoredConversation> {
        val result = mutableListOf<StoredConversation>()
        readableDatabase.rawQuery(
            """
            SELECT c._id, c.title, c.created_at, c.updated_at,
                (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c._id) AS message_count
            FROM conversations c
            ORDER BY c.updated_at DESC, c._id DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StoredConversation(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3),
                    messageCount = cursor.getInt(4)
                )
            }
        }
        return result
    }

    @Synchronized
    fun listMessages(conversationId: String, limit: Int = MAX_VISIBLE_MESSAGES): List<StoredMessage> {
        val safeLimit = limit.coerceIn(1, MAX_VISIBLE_MESSAGES)
        val result = mutableListOf<StoredMessage>()
        readableDatabase.query(
            "messages",
            arrayOf("_id", "conversation_id", "role", "text", "created_at"),
            "conversation_id = ?",
            arrayOf(conversationId),
            null,
            null,
            "created_at DESC, _id DESC",
            safeLimit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StoredMessage(
                    id = cursor.getString(0),
                    conversationId = cursor.getString(1),
                    role = ConversationRole.valueOf(cursor.getString(2)),
                    text = cursor.getString(3),
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return result.asReversed()
    }

    @Synchronized
    fun appendMessage(
        conversationId: String,
        role: ConversationRole,
        rawText: String
    ): StoredMessage {
        val text = normalizeStoredMessage(rawText)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(conversationExists(conversationId)) { "Conversation does not exist." }
            val values = ContentValues().apply {
                put("_id", id)
                put("conversation_id", conversationId)
                put("role", role.name)
                put("text", text)
                put("created_at", now)
            }
            db.insertOrThrow("messages", null, values)

            val currentTitle = db.query(
                "conversations",
                arrayOf("title"),
                "_id = ?",
                arrayOf(conversationId),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else DEFAULT_TITLE
            }
            val updatedTitle = if (role == ConversationRole.USER && currentTitle == DEFAULT_TITLE) {
                normalizeConversationTitle(text)
            } else {
                currentTitle
            }
            val conversationValues = ContentValues().apply {
                put("title", updatedTitle)
                put("updated_at", now)
            }
            db.update("conversations", conversationValues, "_id = ?", arrayOf(conversationId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return StoredMessage(id, conversationId, role, text, now)
    }

    @Synchronized
    fun updateMessage(messageId: String, rawText: String): Boolean {
        val text = normalizeStoredMessage(rawText)
        val db = writableDatabase
        val conversationId = db.query(
            "messages",
            arrayOf("conversation_id"),
            "_id = ?",
            arrayOf(messageId),
            null,
            null,
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: return false

        val changed = db.update(
            "messages",
            ContentValues().apply { put("text", text) },
            "_id = ?",
            arrayOf(messageId)
        ) > 0
        if (changed) {
            db.update(
                "conversations",
                ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
                "_id = ?",
                arrayOf(conversationId)
            )
        }
        return changed
    }

    @Synchronized
    fun deleteMessage(messageId: String): Boolean {
        val db = writableDatabase
        val conversationId = db.query(
            "messages",
            arrayOf("conversation_id"),
            "_id = ?",
            arrayOf(messageId),
            null,
            null,
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: return false
        val deleted = db.delete("messages", "_id = ?", arrayOf(messageId)) > 0
        if (deleted) {
            val remaining = DatabaseUtils.longForQuery(
                db,
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ?",
                arrayOf(conversationId)
            )
            val values = ContentValues().apply {
                put("updated_at", System.currentTimeMillis())
                if (remaining == 0L) put("title", DEFAULT_TITLE)
            }
            db.update("conversations", values, "_id = ?", arrayOf(conversationId))
        }
        return deleted
    }

    @Synchronized
    fun renameConversation(conversationId: String, title: String): Boolean {
        val normalized = normalizeConversationTitle(title)
        return writableDatabase.update(
            "conversations",
            ContentValues().apply {
                put("title", normalized)
                put("updated_at", System.currentTimeMillis())
            },
            "_id = ?",
            arrayOf(conversationId)
        ) > 0
    }

    @Synchronized
    fun clearConversation(conversationId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", "conversation_id = ?", arrayOf(conversationId))
            db.update(
                "conversations",
                ContentValues().apply {
                    put("title", DEFAULT_TITLE)
                    put("updated_at", System.currentTimeMillis())
                },
                "_id = ?",
                arrayOf(conversationId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun deleteConversation(conversationId: String): Boolean {
        val wasActive = preferences.getString(KEY_ACTIVE_CONVERSATION, null) == conversationId
        val deleted = writableDatabase.delete(
            "conversations",
            "_id = ?",
            arrayOf(conversationId)
        ) > 0
        if (deleted && wasActive) {
            val nextConversation = listConversations().firstOrNull()
            if (nextConversation != null) {
                preferences.edit().putString(KEY_ACTIVE_CONVERSATION, nextConversation.id).commit()
            } else {
                preferences.edit().remove(KEY_ACTIVE_CONVERSATION).commit()
                createConversation()
            }
        }
        return deleted
    }

    @Synchronized
    fun listMemories(): List<SavedMemory> {
        val result = mutableListOf<SavedMemory>()
        readableDatabase.query(
            "memories",
            arrayOf("_id", "content", "created_at", "updated_at"),
            null,
            null,
            null,
            null,
            "updated_at DESC, _id DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedMemory(
                    id = cursor.getString(0),
                    content = cursor.getString(1),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3)
                )
            }
        }
        return result
    }

    @Synchronized
    fun saveMemory(rawContent: String): SavedMemory {
        val content = normalizeSavedMemory(rawContent)
        val db = writableDatabase
        val count = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM memories", null)
        require(count < MAX_SAVED_MEMORIES) { "Memory limit reached." }
        val now = System.currentTimeMillis()
        val memory = SavedMemory(UUID.randomUUID().toString(), content, now, now)
        db.insertOrThrow(
            "memories",
            null,
            ContentValues().apply {
                put("_id", memory.id)
                put("content", memory.content)
                put("created_at", memory.createdAt)
                put("updated_at", memory.updatedAt)
            }
        )
        return memory
    }

    @Synchronized
    fun updateMemory(memoryId: String, rawContent: String): Boolean {
        val content = normalizeSavedMemory(rawContent)
        return writableDatabase.update(
            "memories",
            ContentValues().apply {
                put("content", content)
                put("updated_at", System.currentTimeMillis())
            },
            "_id = ?",
            arrayOf(memoryId)
        ) > 0
    }

    @Synchronized
    fun deleteMemory(memoryId: String): Boolean =
        writableDatabase.delete("memories", "_id = ?", arrayOf(memoryId)) > 0

    @Synchronized
    fun deleteAllMemories(): Int =
        writableDatabase.delete("memories", null, null)

    fun isMemoryContextEnabled(): Boolean =
        preferences.getBoolean(KEY_MEMORY_CONTEXT_ENABLED, false)

    fun setMemoryContextEnabled(enabled: Boolean) {
        check(
            preferences.edit()
                .putBoolean(KEY_MEMORY_CONTEXT_ENABLED, enabled)
                .commit()
        ) { "Unable to save the memory context preference." }
    }

    @Synchronized
    fun enabledMemoryContext(): List<String> {
        if (!isMemoryContextEnabled()) return emptyList()
        return listMemories().take(MAX_MEMORY_CONTEXT_ITEMS).map {
            it.content.take(MAX_MEMORY_CONTEXT_CHARS)
        }
    }

    private fun conversationExists(id: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM conversations WHERE _id = ? LIMIT 1",
            arrayOf(id)
        ).use { it.moveToFirst() }

    private companion object {
        const val DATABASE_NAME = "aris_conversations.db"
        const val DATABASE_VERSION = 1
        const val DEFAULT_TITLE = "새 대화"
        const val KEY_ACTIVE_CONVERSATION = "active_conversation_id"
        const val KEY_MEMORY_CONTEXT_ENABLED = "memory_context_enabled"
        const val MAX_VISIBLE_MESSAGES = 200
        const val MAX_MEMORY_CONTEXT_ITEMS = 10
        const val MAX_MEMORY_CONTEXT_CHARS = MAX_SAVED_MEMORY_CHARS
    }
}
