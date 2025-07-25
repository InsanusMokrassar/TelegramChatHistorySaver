package dev.inmo.tgchat_history_saver.common.models

import dev.inmo.tgbotapi.types.CustomEmojiId
import dev.inmo.tgbotapi.types.reactions.Reaction
import kotlinx.serialization.Serializable

@Serializable
data class ChatReactions(
    val savedReaction: Reaction = Reaction.Emoji("\uD83D\uDC4D"),
    val saveErrorReaction: Reaction = Reaction.Emoji("\uD83D\uDC4E"),
    val savingReaction: Reaction = Reaction.Emoji("✍"),
) {
    companion object {
        val DEFAULT = ChatReactions()
    }
}
