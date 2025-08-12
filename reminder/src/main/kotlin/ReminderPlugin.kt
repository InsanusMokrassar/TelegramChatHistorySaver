package dev.inmo.tgchat_history_saver.reminder

import dev.inmo.krontab.krontabConfig
import dev.inmo.micro_utils.coroutines.runCatchingLogging
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.micro_utils.koin.singleWithRandomQualifier
import dev.inmo.micro_utils.repos.add
import dev.inmo.micro_utils.repos.remove
import dev.inmo.plagubot.Plugin
import dev.inmo.plagubot.plugins.commands.full
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.libraries.resender.asMessageMetaInfos
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.commands.BotCommandScope
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgchat_history_saver.common.repo.TrackingChatsRepo
import dev.inmo.tgchat_history_saver.reminder.models.ReminderConfig
import dev.inmo.tgchat_history_saver.reminder.models.ReminderInfo
import dev.inmo.tgchat_history_saver.reminder.repo.CacheRemindersRepo
import dev.inmo.tgchat_history_saver.reminder.repo.ExposedRemindersRepo
import dev.inmo.tgchat_history_saver.reminder.repo.RemindersRepo
import dev.inmo.tgchat_history_saver.reminder.services.RemindsServices
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.binds

object ReminderPlugin : Plugin {
    override fun Module.setupDI(config: JsonObject) {
        single<ReminderConfig> {
            get<Json>().decodeFromJsonElement(ReminderConfig.serializer(), config["reminder"]!!.jsonObject)
        }

        single {
            ExposedRemindersRepo(database = get(), json = get())
        }
        single {
            CacheRemindersRepo(get<ExposedRemindersRepo>(), get(), )
        } binds arrayOf(RemindersRepo::class)

        single {
            RemindsServices(get(), get())
        }

        singleWithRandomQualifier {
            BotCommand("remind", "Set reminder for message, use with reply").full(
                BotCommandScope.AllGroupChats
            )
        }
        singleWithRandomQualifier {
            BotCommand("remove_reminders", "Remove all reminders for messages, use with reply").full(
                BotCommandScope.AllGroupChats
            )
        }
    }
    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val remindersRepo = koin.get<RemindersRepo>()
        val remindsServices = koin.get<RemindsServices>()
        val trackingRepo = koin.get<TrackingChatsRepo>()
        remindsServices.start(koin.get())

        // TODO: Add full management of reminders
        onCommandWithArgs(
            "remind",
            initialFilter = { it.chat.id.toChatId() in trackingRepo.getTrackingChats() }
        ) { it, args ->
            val subtext = args.joinToString(" ").trim().takeIf { it.isNotBlank() }

            val asKrontabTry = subtext ?.let { subtext ->
                runCatchingLogging {
                    subtext.krontabConfig().also { it.scheduler() /* checking compilation */ }
                }.getOrNull()
            }

            val messageInReply = it.replyTo

            when {
                asKrontabTry == null -> reply(it, "Use krontab as an argument for the command. Use https://insanusmokrassar.github.io/KrontabPredictor/ for more info")
                messageInReply == null || messageInReply !is AccessibleMessage -> reply(it, "You must send command as reply to some message")
                else -> {
                    remindersRepo.add(
                        asKrontabTry,
                        ReminderInfo(
                            asKrontabTry,
                            it.chat.id,
                            messageInReply.asMessageMetaInfos()
                        )
                    )
                    reply(
                        it,
                        "Saved"
                    )
                }
            }
        }
        onCommand(
            "remove_reminders",
            requireOnlyCommandInMessage = false,
            initialFilter = { it.chat.id.toChatId() in trackingRepo.getTrackingChats() }
        ) {
            val messageInReply = it.replyTo ?: return@onCommand
            val messageInfos = messageInReply.asMessageMetaInfos()

            val remindersToCleanup = remindersRepo.getAll().values.flatMap { it.filter { it.messagesInfos.any { it in messageInfos } } }

            remindersToCleanup.forEach {
                val newMessagesInfos = it.messagesInfos - messageInfos

                remindersRepo.remove(it.krontabConfig, it)
                if (newMessagesInfos.isNotEmpty()) {
                    remindersRepo.add(it.krontabConfig, it.copy(messagesInfos = newMessagesInfos))
                }
            }
        }
    }
}
