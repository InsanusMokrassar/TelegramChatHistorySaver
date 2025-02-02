package dev.inmo.tgchat_history_saver.common.services

import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MediaGroupId
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.MessageThreadId
import dev.inmo.tgbotapi.types.message.content.MessageContent
import korlibs.time.DateTime

interface SaverService {
    suspend fun save(chatId: IdChatIdentifier, messageId: MessageId, dateTime: DateTime, mediaGroupId: MediaGroupId?, content: MessageContent): Boolean
    suspend fun saveChatTitle(chatId: IdChatIdentifier, title: String)
    suspend fun saveThreadTitle(chatId: ChatId, threadId: MessageThreadId, title: String)
}
