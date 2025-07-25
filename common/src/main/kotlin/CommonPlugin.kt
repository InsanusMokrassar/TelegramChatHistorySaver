package dev.inmo.tgchat_history_saver.common

import dev.inmo.kslog.common.i
import dev.inmo.kslog.common.logger
import dev.inmo.micro_utils.coroutines.runCatchingLogging
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithRandomQualifier
import dev.inmo.micro_utils.repos.cache.full.fullyCached
import dev.inmo.micro_utils.repos.exposed.keyvalue.ExposedKeyValueRepo
import dev.inmo.micro_utils.repos.mappers.withMapper
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.plugins.commands.full
import dev.inmo.tgbotapi.extensions.api.chat.forum.createForumTopic
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.api.send.setMessageReaction
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onEditedContentMessage
import dev.inmo.tgbotapi.extensions.utils.asTextedInput
import dev.inmo.tgbotapi.extensions.utils.chatEventMessageOrNull
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.forumTopicCreatedOrNull
import dev.inmo.tgbotapi.extensions.utils.fromUserMessageOrNull
import dev.inmo.tgbotapi.extensions.utils.internalOrNull
import dev.inmo.tgbotapi.extensions.utils.updates.hasCommands
import dev.inmo.tgbotapi.extensions.utils.updates.hasNoCommands
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.BusinessChatId
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ChatIdWithThreadId
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.ReplyParameters
import dev.inmo.tgbotapi.types.chat.BusinessChatImpl
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.chat.PublicChat
import dev.inmo.tgbotapi.types.chat.UnknownChatType
import dev.inmo.tgbotapi.types.commands.BotCommandScope
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.abstracts.Message
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.RGBColor
import dev.inmo.tgchat_history_saver.common.models.ChatReactions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.core.Koin
import org.koin.core.module.Module
import dev.inmo.tgchat_history_saver.common.models.CommonConfig
import dev.inmo.tgchat_history_saver.common.repo.CacheTrackingChatsRepo
import dev.inmo.tgchat_history_saver.common.repo.ChatsReactionsRepo
import dev.inmo.tgchat_history_saver.common.repo.ExposedTrackingChatsRepo
import dev.inmo.tgchat_history_saver.common.repo.KeyValueBasedChatsReactionsRepo
import dev.inmo.tgchat_history_saver.common.repo.TrackingChatsRepo
import dev.inmo.tgchat_history_saver.common.services.SaverService
import dev.inmo.tgchat_history_saver.common.services.SimpleFolderSaverService
import kotlinx.coroutines.delay

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

        single<ChatsReactionsRepo> {
            val json = get<Json>()
            KeyValueBasedChatsReactionsRepo(
                ExposedKeyValueRepo(
                    get(),
                    { long("chat_id") },
                    { text("chat_reactions") }
                ).withMapper<ChatId, ChatReactions, Long, String>(
                    { this.chatId.long },
                    { json.encodeToString(ChatReactions.serializer(), this) },
                    { ChatId(RawChatId(this)) },
                    { json.decodeFromString(ChatReactions.serializer(), this) },
                ).fullyCached()
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
        singleWithRandomQualifier {
            BotCommand("force_resave", "Force resave replied message").full(
                BotCommandScope.AllGroupChats
            )
        }
        singleWithRandomQualifier {
            BotCommand("force_full_resave", "Force full resave of all messages").full(
                BotCommandScope.AllGroupChats
            )
        }
    }
    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val config = koin.get<CommonConfig>()
        val trackingRepo = koin.get<TrackingChatsRepo>()
        val saverService = koin.get<SaverService>()


        val chatsReactionsRepo = koin.get<ChatsReactionsRepo>()
        suspend fun getSavingReaction(id: ChatId) = (chatsReactionsRepo.get(id) ?: ChatReactions.DEFAULT).savingReaction
        suspend fun getSavedReaction(id: ChatId) = (chatsReactionsRepo.get(id) ?: ChatReactions.DEFAULT).savedReaction
        suspend fun getSaveErrorReaction(id: ChatId) = (chatsReactionsRepo.get(id) ?: ChatReactions.DEFAULT).saveErrorReaction
        suspend fun setSavingReaction(message: AccessibleMessage) = runCatchingLogging { setMessageReaction(message, getSavingReaction(message.chat.id.toChatId())) }
        suspend fun setSavedReaction(message: AccessibleMessage) = runCatchingLogging { setMessageReaction(message, getSavedReaction(message.chat.id.toChatId())) }
        suspend fun setSaveErrorReaction(message: AccessibleMessage) = runCatchingLogging { setMessageReaction(message, getSaveErrorReaction(message.chat.id.toChatId())) }
        suspend fun withReactions(message: AccessibleMessage, block: suspend () -> Boolean?) {
            setSavingReaction(message)
            val success = runCatchingLogging {
                block()
            }.getOrElse { false }

            when (success) {
                true -> setSavedReaction(message)
                false -> setSaveErrorReaction(message)
                null -> runCatchingLogging { setMessageReaction(message) }
            }
        }

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

        onContentMessage(initialFilter = { it.chat.id.toChatId() in trackingRepo.getTrackingChats() && it.content.asTextedInput() ?.text ?.startsWith("/") != true }) {
            withReactions(it) {
                val chatId = it.chat.id
                val topicInfo = it.replyInfo ?.internalOrNull() ?.message ?.chatEventMessageOrNull() ?.chatEvent ?.forumTopicCreatedOrNull()
                val title = when (val chat = it.chat) {
                    is PublicChat -> chat.title
                    is PrivateChat -> "${chat.lastName} ${chat.firstName}"
                    is BusinessChatImpl,
                    is UnknownChatType -> return@withReactions null
                }

                saverService.saveChatTitle(chatId, title)
                when (chatId) {
                    is BusinessChatId -> return@withReactions null
                    is ChatId -> { /* do nothing */ }
                    is ChatIdWithThreadId -> {
                        topicInfo ?.let {
                            saverService.saveThreadTitle(chatId.toChatId(), chatId.threadId, it.name)
                        }
                    }
                }
                saverService.save(it.chat.id, it.messageId, it.date, it.mediaGroupId, it.content)
            }
        }
        onEditedContentMessage(initialFilter = { it.chat.id.toChatId() in trackingRepo.getTrackingChats() && it.content.asTextedInput() ?.text ?.startsWith("/") != true }) {
            withReactions(it) {

                val chatId = it.chat.id
                val topicInfo =
                    it.replyInfo?.internalOrNull()?.message?.chatEventMessageOrNull()?.chatEvent?.forumTopicCreatedOrNull()
                val title = when (val chat = it.chat) {
                    is PublicChat -> chat.title
                    is PrivateChat -> "${chat.lastName} ${chat.firstName}"
                    is BusinessChatImpl,
                    is UnknownChatType -> return@withReactions null
                }

                saverService.saveChatTitle(chatId, title)
                when (chatId) {
                    is BusinessChatId -> return@withReactions null
                    is ChatId -> { /* do nothing */
                    }

                    is ChatIdWithThreadId -> {
                        topicInfo?.let {
                            saverService.saveThreadTitle(chatId.toChatId(), chatId.threadId, it.name)
                        }
                    }
                }
                saverService.save(it.chat.id, it.messageId, it.date, it.mediaGroupId, it.content)
            }
        }

        onCommand("force_resave", initialFilter = { it.fromUserMessageOrNull() ?.from ?.id ?.toChatId() == config.ownerChatId }) {
            val messageInReply = it.replyTo
            when {
                messageInReply == null -> reply(it, "Reply some message to force its saving")
                messageInReply.chat.id.toChatId() !in trackingRepo.getTrackingChats() -> reply(it, "Chat is not tracked")
                messageInReply !is CommonMessage<*> -> reply(it, "Reply on message with content")
                else -> {
                    withReactions(messageInReply) {
                        saverService.save(
                            messageInReply.chat.id,
                            messageInReply.messageId,
                            messageInReply.date,
                            messageInReply.mediaGroupId,
                            messageInReply.content
                        )
                    }
                }
            }
        }

        onCommand("force_full_resave", initialFilter = { it.fromUserMessageOrNull() ?.from ?.id ?.toChatId() == config.ownerChatId }) {
            val topic = createForumTopic(
                it.chat,
                "Cache",
                RGBColor(0x00FF00)
            )
            for (i in 1L until it.messageId.long) {
                runCatchingLogging {
                    val sent = send(
                        it.chat.id,
                        "Cache",
                        threadId = topic.messageThreadId,
                        disableNotification = true,
                        replyParameters = ReplyParameters(
                            chatIdentifier = it.chat.id,
                            messageId = MessageId(i),
                            allowSendingWithoutReply = false
                        )
                    )
                    runCatchingLogging {
                        val messageInReply = (sent as? CommonMessage<TextContent>) ?.replyTo
                        when {
                            messageInReply == null || messageInReply !is CommonMessage<*> -> {}
                            else -> {
                                withReactions(messageInReply) {
                                    saverService.save(
                                        messageInReply.chat.id,
                                        messageInReply.messageId,
                                        messageInReply.date,
                                        messageInReply.mediaGroupId,
                                        messageInReply.content
                                    )
                                }
                            }
                        }
                    }
                    delete(sent)
                    delay(1000L)
                }
            }

        }
    }
}
