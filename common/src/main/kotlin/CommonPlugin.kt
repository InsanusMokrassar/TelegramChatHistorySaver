package dev.inmo.tgchat_history_saver.common

import dev.inmo.kslog.common.i
import dev.inmo.kslog.common.logger
import dev.inmo.micro_utils.fsm.common.State
import dev.inmo.plagubot.Plugin
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextWithFSM
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.core.Koin
import org.koin.core.module.Module
import dev.inmo.tgchat_history_saver.common.models.CommonConfig

object CommonPlugin : Plugin {
    override fun Module.setupDI(config: JsonObject) {
        single<CommonConfig> {
            get<Json>().decodeFromJsonElement(CommonConfig.serializer(), config["common"]!!.jsonObject)
        }
    }
    override suspend fun BehaviourContextWithFSM<State>.setupBotPlugin(koin: Koin) {
        val config = koin.get<CommonConfig>()
        logger.i(config.ownerChatId)
    }
}
