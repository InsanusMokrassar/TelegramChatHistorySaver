package dev.inmo.tgchat_history_saver.common

import dev.inmo.kslog.common.i
import dev.inmo.kslog.common.logger
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithRandomQualifier
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.plugins.commands.full
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.chatEventMessageOrNull
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.forumContentMessageOrNull
import dev.inmo.tgbotapi.extensions.utils.forumTopicCreatedOrNull
import dev.inmo.tgbotapi.extensions.utils.internalOrNull
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.BusinessChatId
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ChatIdWithThreadId
import dev.inmo.tgbotapi.types.chat.BusinessChatImpl
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.chat.PublicChat
import dev.inmo.tgbotapi.types.chat.UnknownChatType
import dev.inmo.tgbotapi.types.commands.BotCommandScope
import dev.inmo.tgbotapi.types.toChatId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.core.Koin
import org.koin.core.module.Module
import dev.inmo.tgchat_history_saver.common.models.CommonConfig
import dev.inmo.tgchat_history_saver.common.repo.CacheTrackingChatsRepo
import dev.inmo.tgchat_history_saver.common.repo.ExposedTrackingChatsRepo
import dev.inmo.tgchat_history_saver.common.repo.TrackingChatsRepo
import dev.inmo.tgchat_history_saver.common.services.SaverService
import dev.inmo.tgchat_history_saver.common.services.SimpleFolderSaverService

object CommonPlugin : Plugin {
    override fun Module.setupDI(config: JsonObject) {
        single<CommonConfig> {
            get<Json>().decodeFromJsonElement(CommonConfig.serializer(), config["common"]!!.jsonObject)
        }

        single { ExposedTrackingChatsRepo(get()) }
        single { CacheTrackingChatsRepo(trackingChatsRepo = get<ExposedTrackingChatsRepo>(), scope = get()) }
        single<TrackingChatsRepo> { get<CacheTrackingChatsRepo>() }

        single<SaverService> {
            SimpleFolderSaverService(
                folder = get<CommonConfig>().savingFolderFile,
                bot = get(),
                json = get()
            )
        }

        singleWithRandomQualifier {
            BotCommand("enable_chat_tracking", "Enable chat tracking").full(
                BotCommandScope.AllGroupChats
            )
        }
        singleWithRandomQualifier {
            BotCommand("disable_chat_tracking", "Disable chat tracking").full(
                BotCommandScope.AllGroupChats
            )
        }
    }
    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val config = koin.get<CommonConfig>()
        val trackingRepo = koin.get<TrackingChatsRepo>()
        val saverService = koin.get<SaverService>()
        logger.i(config.ownerChatId)

        onCommand("enable_chat_tracking", initialFilter = { it.from ?.id == config.ownerChatId }) {
            runCatching {
                trackingRepo.add(it.chat.id.toChatId())
            }
            if (trackingRepo.getTrackingChats().contains(it.chat.id.toChatId())) {
                reply(it, "Tracking has been enabled")
            } else {
                reply(it, "Tracking has not been enabled")
            }
        }
        onCommand("disable_chat_tracking", initialFilter = { it.from ?.id == config.ownerChatId }) {
            runCatching {
                trackingRepo.remove(it.chat.id.toChatId())
            }
            if (trackingRepo.getTrackingChats().contains(it.chat.id.toChatId()) == false) {
                reply(it, "Tracking has been disabled")
            } else {
                reply(it, "Tracking has not been disabled")
            }
        }

        onContentMessage(initialFilter = { it.chat.id.toChatId() in trackingRepo.getTrackingChats() }) {
            val chatId = it.chat.id
            val topicInfo = it.replyInfo ?.internalOrNull() ?.message ?.chatEventMessageOrNull() ?.chatEvent ?.forumTopicCreatedOrNull()
            val title = when (val chat = it.chat) {
                is PublicChat -> chat.title
                is PrivateChat -> "${chat.lastName} ${chat.firstName}"
                is BusinessChatImpl,
                is UnknownChatType -> return@onContentMessage
            }

            saverService.saveChatTitle(chatId, title)
            when (chatId) {
                is BusinessChatId -> return@onContentMessage
                is ChatId -> { /* do nothing */ }
                is ChatIdWithThreadId -> {
                    topicInfo ?.let {
                        saverService.saveThreadTitle(chatId.toChatId(), chatId.threadId, it.name)
                    }
                }
            }
            saverService.save(it.chat.id, it.messageId, it.date, it.content)
        }
    }
}
