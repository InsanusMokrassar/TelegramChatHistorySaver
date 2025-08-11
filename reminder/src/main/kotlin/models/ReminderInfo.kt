package dev.inmo.tgchat_history_saver.reminder.models

import dev.inmo.krontab.KrontabConfig
import dev.inmo.tgbotapi.libraries.resender.MessageMetaInfo
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.message.abstracts.Message
import kotlinx.serialization.Serializable

@Serializable
data class ReminderInfo(
    val krontabConfig: KrontabConfig,
    val targetChatId: IdChatIdentifier,
    val messagesInfos: List<MessageMetaInfo>,
)
