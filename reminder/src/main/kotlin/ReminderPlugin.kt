package dev.inmo.tgchat_history_saver.reminder

import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.Plugin
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgchat_history_saver.reminder.models.ReminderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.core.Koin
import org.koin.core.module.Module

object ReminderPlugin : Plugin {
    override fun Module.setupDI(config: JsonObject) {
        single<ReminderConfig> {
            get<Json>().decodeFromJsonElement(ReminderConfig.serializer(), config["reminder"]!!.jsonObject)
        }
    }
    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val config = koin.get<ReminderConfig>()

    }
}
