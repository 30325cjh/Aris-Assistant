package com.arisassistant.data

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
        return ConversationStore(context, databaseName) to databaseName
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
