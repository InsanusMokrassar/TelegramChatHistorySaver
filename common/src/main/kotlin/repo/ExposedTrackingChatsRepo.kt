package dev.inmo.tgchat_history_saver.common.repo

import dev.inmo.micro_utils.repos.exposed.ExposedRepo
import dev.inmo.micro_utils.repos.exposed.initTable
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.RawChatId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedTrackingChatsRepo(override val database: Database) : TrackingChatsRepo, ExposedRepo, Table(
    "tracking_repos"
) {
    private val chatIdColumn = long("chat_id")
    override val primaryKey: PrimaryKey = PrimaryKey(chatIdColumn)

    init {
        initTable()
    }

    override suspend fun add(chatId: ChatId) {
        transaction(database) {
            insert { it[chatIdColumn] = chatId.chatId.long }
        }
    }

    override suspend fun remove(chatId: ChatId) {
        transaction(database) {
            deleteWhere { with (it) { chatIdColumn.eq(chatId.chatId.long) } }
        }
    }

    override suspend fun getTrackingChats(): Set<ChatId> {
        return transaction(database) {
            selectAll().map { it[chatIdColumn] }
        }.map { ChatId(RawChatId(it)) }.toSet()
    }
}
