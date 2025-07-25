package dev.inmo.tgchat_history_saver.common.repo

import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgchat_history_saver.common.models.ChatReactions

interface ChatsReactionsRepo : KeyValueRepo<ChatId, ChatReactions> {
}