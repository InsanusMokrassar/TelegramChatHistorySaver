package dev.inmo.tgchat_history_saver.common.models

import dev.inmo.tgbotapi.types.IdChatIdentifier
import kotlinx.serialization.Serializable

@Serializable
data class CommonConfig(
    val ownerChatId: IdChatIdentifier,
)
