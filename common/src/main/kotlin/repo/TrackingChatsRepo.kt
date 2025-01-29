package dev.inmo.tgchat_history_saver.common.repo

import dev.inmo.tgbotapi.types.ChatId

interface TrackingChatsRepo {
    suspend fun add(chatId: ChatId)
    suspend fun remove(chatId: ChatId)
    suspend fun getTrackingChats(): Set<ChatId>
}
