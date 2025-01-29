package dev.inmo.tgchat_history_saver.replier.models

import kotlinx.serialization.Serializable

@Serializable
data class ReplierConfig(
    val answer: String
)
