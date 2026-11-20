package com.arisassistant.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.arisassistant.ai.ConversationRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationStoreTest {
    private fun newStore(): Pair<ConversationStore, String> {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "aris-test-${UUID.randomUUID()}.db"
        return ConversationStore(context, databaseName, TestConversationCipher()) to databaseName
    }

    @Test
    fun persistsMessagesAndSupportsConversationManagement() {
        val (store, databaseName) = newStore()
        try {
            val firstId = store.activeConversationId()
            val userMessage = store.appendMessage(firstId, ConversationRole.USER, "아리스 프로젝트")
            store.appendMessage(firstId, ConversationRole.ASSISTANT, "무엇을 도와드릴까요?")

            assertEquals("아리스 프로젝트", store.listConversations().first().title)
            assertEquals(2, store.listMessages(firstId).size)
            assertTrue(store.updateMessage(userMessage.id, "수정한 질문"))
            assertEquals("수정한 질문", store.listMessages(firstId).first().text)
            assertTrue(store.deleteMessage(userMessage.id))
            assertEquals(1, store.listMessages(firstId).size)

            val secondId = store.createConversation("새 작업")
            assertEquals(secondId, store.activeConversationId())
            assertEquals(2, store.listConversations().size)
            assertTrue(store.deleteConversation(secondId))
            assertEquals(firstId, store.activeConversationId())
            assertEquals(1, store.listConversations().size)
        } finally {
            store.close()
            RuntimeEnvironment.getApplication().deleteDatabase(databaseName)
        }
    }


    @Test
    fun migratesLegacyPlaintextAndKeepsContentEncryptedAfterReopen() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "aris-legacy-" + UUID.randomUUID() + ".db"
        val legacy = LegacyConversationDatabase(context, databaseName)
        val legacyDb = legacy.writableDatabase
        val now = 1_700_000_000_000L
        legacyDb.insertOrThrow(
            "conversations",
            null,
            ContentValues().apply {
                put("_id", "conversation-1")
                put("title", "기존 대화 제목")
                put("created_at", now)
                put("updated_at", now)
            }
        )
        legacyDb.insertOrThrow(
            "messages",
            null,
            ContentValues().apply {
                put("_id", "message-1")
                put("conversation_id", "conversation-1")
                put("role", ConversationRole.USER.name)
                put("text", "기존 대화의 비밀 내용")
                put("created_at", now)
            }
        )
        legacyDb.insertOrThrow(
            "memories",
            null,
            ContentValues().apply {
                put("_id", "memory-1")
                put("content", "사용자가 직접 저장한 기억")
                put("created_at", now)
                put("updated_at", now)
            }
        )
        legacy.close()

        val cipher = TestConversationCipher()
        val migrated = ConversationStore(context, databaseName, cipher)
        try {
            assertEquals("기존 대화 제목", migrated.listConversations().single().title)
            assertEquals(
                "기존 대화의 비밀 내용",
                migrated.listMessages("conversation-1").single().text
            )
            assertEquals("사용자가 직접 저장한 기억", migrated.listMemories().single().content)

            val rawTitle = migrated.writableDatabase.rawQuery(
                "SELECT title FROM conversations WHERE _id = ?",
                arrayOf("conversation-1")
            ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
            val rawMessage = migrated.writableDatabase.rawQuery(
                "SELECT text FROM messages WHERE _id = ?",
                arrayOf("message-1")
            ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
            val rawMemory = migrated.writableDatabase.rawQuery(
                "SELECT content FROM memories WHERE _id = ?",
                arrayOf("memory-1")
            ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

            assertTrue(rawTitle.startsWith(TestConversationCipher.PREFIX))
            assertTrue(rawMessage.startsWith(TestConversationCipher.PREFIX))
            assertTrue(rawMemory.startsWith(TestConversationCipher.PREFIX))
            assertFalse(rawTitle.contains("기존 대화 제목"))
            assertFalse(rawMessage.contains("기존 대화의 비밀 내용"))
            assertFalse(rawMemory.contains("사용자가 직접 저장한 기억"))

            migrated.appendMessage("conversation-1", ConversationRole.ASSISTANT, "새로 저장한 답변")
            migrated.saveMemory("새로 저장한 기억")
        } finally {
            migrated.close()
        }

        val reopened = ConversationStore(context, databaseName, cipher)
        try {
            assertEquals(
                listOf("기존 대화의 비밀 내용", "새로 저장한 답변"),
                reopened.listMessages("conversation-1").map { it.text }
            )
            assertEquals(
                listOf("사용자가 직접 저장한 기억", "새로 저장한 기억").sorted(),
                reopened.listMemories().map { it.content }.sorted()
            )
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
            context.getSharedPreferences(databaseName + "_settings", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    @Test
    fun savedMemoriesAreExplicitAndOnlyUsedWhenEnabled() {
        val (store, databaseName) = newStore()
        try {
            val memory = store.saveMemory("한국어로 답변해 주세요.")
            assertEquals(1, store.listMemories().size)
            assertTrue(store.enabledMemoryContext().isEmpty())

            store.setMemoryContextEnabled(true)
            assertEquals(listOf(memory.content), store.enabledMemoryContext())
            assertTrue(store.updateMemory(memory.id, "짧고 명확하게 설명해 주세요."))
            assertEquals("짧고 명확하게 설명해 주세요.", store.enabledMemoryContext().single())
            assertTrue(store.deleteMemory(memory.id))
            assertTrue(store.listMemories().isEmpty())
        } finally {
            store.close()
            RuntimeEnvironment.getApplication().deleteDatabase(databaseName)
        }
    }
}


private class LegacyConversationDatabase(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, 1) {
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
        db.execSQL("CREATE INDEX messages_by_conversation ON messages(conversation_id, created_at)")
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
