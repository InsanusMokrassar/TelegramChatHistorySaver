package dev.inmo.tgchat_history_saver.common.models

import dev.inmo.tgbotapi.types.IdChatIdentifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

@Serializable
data class CommonConfig(
    val ownerChatId: IdChatIdentifier,
    val savingFolder: String
) {
    @Transient
    val savingFolderFile: File by lazy {
        File(savingFolder).also {
            require((it.exists() && it.isDirectory) || it.mkdirs())
        }
    }
}
