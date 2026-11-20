package com.example.jarvis.conversations

import com.example.jarvis.database.ConversationDao
import com.example.jarvis.database.ConversationEntity
import com.example.jarvis.database.MessageEntity
import kotlinx.coroutines.flow.Flow

class ConversationRepository(private val conversationDao: ConversationDao) {

    val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        if (query.isBlank()) conversations else conversationDao.searchConversations(query.trim())

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        conversationDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity> =
        conversationDao.getMessagesList(conversationId)

    suspend fun createConversation(title: String = "New Session"): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insertConversation(
            ConversationEntity(
                title = title.ifBlank { "New Session" },
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun getConversation(id: Long): ConversationEntity? =
        conversationDao.getConversationById(id)

    suspend fun renameConversation(id: Long, newTitle: String) {
        if (newTitle.isNotBlank()) {
            conversationDao.renameConversation(id, newTitle.trim())
        }
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteConversation(id)
    }

    suspend fun clearAllConversations() {
        conversationDao.clearAllConversations()
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        toolName: String? = null,
        toolStatus: String? = null
    ): Long {
        val msgId = conversationDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                toolName = toolName,
                toolStatus = toolStatus,
                timestamp = System.currentTimeMillis()
            )
        )
        conversationDao.touchConversation(conversationId)
        return msgId
    }

    suspend fun deleteMessagesFrom(conversationId: Long, fromMessageId: Long) {
        conversationDao.deleteMessagesFrom(conversationId, fromMessageId)
    }

    suspend fun searchMessages(query: String): List<MessageEntity> =
        if (query.isBlank()) emptyList() else conversationDao.searchMessages(query.trim())
}
