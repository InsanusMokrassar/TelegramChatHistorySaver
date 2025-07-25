package dev.inmo.tgchat_history_saver.common.repo

import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgchat_history_saver.common.models.ChatReactions

class KeyValueBasedChatsReactionsRepo(
    private val base: KeyValueRepo<ChatId, ChatReactions>
) : ChatsReactionsRepo, KeyValueRepo<ChatId, ChatReactions> by base
