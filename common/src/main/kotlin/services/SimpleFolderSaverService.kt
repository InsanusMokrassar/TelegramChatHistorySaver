package dev.inmo.tgchat_history_saver.common.services

import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.e
import dev.inmo.kslog.common.logger
import dev.inmo.kslog.common.wtf
import dev.inmo.micro_utils.common.filesize
import dev.inmo.micro_utils.coroutines.runCatchingLogging
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.files.downloadFileStream
import dev.inmo.tgbotapi.extensions.api.get.getFileAdditionalInfo
import dev.inmo.tgbotapi.extensions.utils.textedMediaContentOrNull
import dev.inmo.tgbotapi.types.BusinessChatId
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ChatIdWithThreadId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MediaGroupId
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.MessageThreadId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.files.CustomNamedMediaFile
import dev.inmo.tgbotapi.types.files.TelegramMediaFile
import dev.inmo.tgbotapi.types.message.content.ContactContent
import dev.inmo.tgbotapi.types.message.content.DiceContent
import dev.inmo.tgbotapi.types.message.content.GameContent
import dev.inmo.tgbotapi.types.message.content.GiveawayContent
import dev.inmo.tgbotapi.types.message.content.GiveawayPublicResultsContent
import dev.inmo.tgbotapi.types.message.content.InvoiceContent
import dev.inmo.tgbotapi.types.message.content.LiveLocationContent
import dev.inmo.tgbotapi.types.message.content.MediaContent
import dev.inmo.tgbotapi.types.message.content.MediaGroupContent
import dev.inmo.tgbotapi.types.message.content.MessageContent
import dev.inmo.tgbotapi.types.message.content.PaidMediaInfoContent
import dev.inmo.tgbotapi.types.message.content.PollContent
import dev.inmo.tgbotapi.types.message.content.StaticLocationContent
import dev.inmo.tgbotapi.types.message.content.StoryContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.message.content.TextedContent
import dev.inmo.tgbotapi.types.message.content.VenueContent
import dev.inmo.tgbotapi.types.toChatId
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readBuffer
import korlibs.time.DateFormat
import korlibs.time.DateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.serialization.json.Json
import java.io.File

class SimpleFolderSaverService (
    private val folder: File,
    private val bot: TelegramBot,
    private val json: Json
) : SaverService {
    private val internalJson = Json(json) {
        prettyPrint = true
        prettyPrintIndent = "    "
    }
    private val Log = KSLog("SimpleFolderSaverService")
    private val dateTimeFormat = DateFormat("dd.MM.yyyy,HH.mm.ss")
    private val mutex = Mutex()

    private val chatsFiles = mutableMapOf<ChatId, File>()
    private val threadsFiles = mutableMapOf<MessageThreadId, File>()

    private fun getChatFolderWithoutLock(chatId: IdChatIdentifier, title: String?): File {
        val existsFolder = chatsFiles[chatId.toChatId()]
        if (existsFolder != null) return existsFolder

        return (if (title == null) {
            File(folder, "${chatId.chatId.long}")
        } else {
            File(folder, "${title}_${chatId.chatId.long}")
        }).also {
            chatsFiles[chatId.toChatId()] = it
        }
    }
    private fun getThreadFolderWithoutLock(chatId: ChatId, threadId: MessageThreadId, title: String?): File {
        val existsFolder = threadsFiles[threadId]
        if (existsFolder != null) return existsFolder

        val chatFolder = getChatFolderWithoutLock(chatId, title)

        return (if (title == null) {
            File(chatFolder, "${threadId.long}")
        } else {
            File(chatFolder, "${title}_${threadId.long}")
        }).also {
            threadsFiles[threadId] = it
        }
    }

    private fun getChatOrThreadFolderWithoutLock(chatId: IdChatIdentifier): File? {
        return when (chatId) {
            is BusinessChatId -> null
            is ChatId -> getChatFolderWithoutLock(chatId, null)
            is ChatIdWithThreadId -> getThreadFolderWithoutLock(chatId.toChatId(), chatId.threadId, null)
        }
    }

    init {
        when {
            folder.exists() && folder.isDirectory -> { /* do nothing */ }
            folder.isFile -> error("${folder.absolutePath} is supposed to be a directory")
            !folder.mkdirs() -> error("Unable to create directory ${folder.absolutePath}")
        }
        folder.listFiles() ?.forEach { file ->
            if (file.isFile) return@forEach

            val currentChatName = file.name
            val splitted = currentChatName.split("_")
            val chatId = splitted.lastOrNull() ?.toLongOrNull() ?.let(::RawChatId) ?.let(::ChatId) ?: return@forEach
            chatsFiles[chatId] = file

            file.listFiles() ?.forEach { threadFile ->
                if (threadFile.isFile) return@forEach

                val currentThreadName = threadFile.name
                val splittedThreadFileName = currentThreadName.split("_")
                val threadId = splittedThreadFileName.lastOrNull() ?.toLongOrNull() ?.let(::MessageThreadId) ?: return@forEach
                threadsFiles[threadId] = threadFile
            }
        }
    }


    override suspend fun save(
        chatId: IdChatIdentifier,
        messageId: MessageId,
        dateTime: DateTime,
        mediaGroupId: MediaGroupId?,
        content: MessageContent
    ): Boolean = mutex.withLock {
        val folder = getChatOrThreadFolderWithoutLock(chatId) ?: return false
        val folderIdentifier = mediaGroupId ?.string ?: messageId.long.toString()
        val messageFolder = File(folder, "${dateTime.format(dateTimeFormat)}_${folderIdentifier}")
        if ((folder.exists() || folder.mkdirs()) && (messageFolder.exists() || messageFolder.mkdirs())) {
            runCatchingLogging {
                when (content) {
                    is MediaContent,
                    is ContactContent,
                    is DiceContent,
                    is GameContent,
                    is GiveawayContent,
                    is GiveawayPublicResultsContent,
                    is InvoiceContent,
                    is LiveLocationContent,
                    is StaticLocationContent,
                    is PollContent,
                    is StoryContent,
                    is VenueContent -> { /* do nothing */ }
                    is TextedContent -> {
                        runCatching {
                            val text = content.text ?: return@runCatching

                            val textFile = File(messageFolder, "text.txt")
                            textFile.delete()
                            textFile.createNewFile()
                            textFile.writeText(text)
                        }.onFailure {
                            logger.e(it) { "Unable to save text in folder ${messageFolder.absolutePath}" }
                            return@runCatchingLogging false
                        }
                    }
                }
                val media: List<Triple<MessageId, TelegramMediaFile, String?>> = when (content) {
                    is MediaGroupContent<*> -> content.group.map {
                        Triple(
                            it.messageId,
                            it.content.media,
                            (content.text)
                        )
                    }
                    is MediaContent -> {
                        listOf(
                            Triple(
                                messageId,
                                content.media,
                                (content.textedMediaContentOrNull() ?.text)
                            )
                        )
                    }
                    is ContactContent,
                    is DiceContent,
                    is GameContent,
                    is GiveawayContent,
                    is GiveawayPublicResultsContent,
                    is InvoiceContent,
                    is LiveLocationContent,
                    is StaticLocationContent,
                    is PaidMediaInfoContent,
                    is PollContent,
                    is StoryContent,
                    is TextContent,
                    is VenueContent -> emptyList()
                }
                media.forEach { (messageId, media, text) ->
                    runCatchingLogging {
                        val filename = messageId.toString() + "_" + (if (media is CustomNamedMediaFile) {
                            media.fileName ?: media.fileId.fileId
                        } else {
                            runCatchingLogging {
                                bot.getFileAdditionalInfo(media).fileName
                            }.getOrElse {
                                media.fileId.fileId
                            }
                        })

                        val mediaFile = File(messageFolder, filename)
                        runCatchingLogging {
                            Log.wtf("Start downloading of ${media.fileId}")
                            mediaFile.outputStream().use {
                                val fileInfo = bot.getFileAdditionalInfo(media.fileId)
                                val downloadStream = if (fileInfo.filePath.startsWith("/")) { // if local
                                    File(fileInfo.filePath).inputStream().toByteReadChannel()
                                } else {
                                    bot.downloadFileStream(fileInfo)
                                }
                                downloadStream.readBuffer().transferTo(it.asSink())
                            }
                            Log.wtf("Successfully downloaded ${media.fileId} to ${mediaFile.canonicalPath}. File size: ${mediaFile.filesize}")
                        }.getOrThrow()
                    }.onFailure {
                        logger.e(it) { "Unable to save media in folder ${messageFolder.absolutePath}" }
                        return@runCatchingLogging false
                    }
                    text ?.let {
                        runCatchingLogging {
                            val filename = messageId.toString() + "_" + "caption.txt"

                            val textFile = File(messageFolder, filename)
                            textFile.delete()
                            textFile.createNewFile()
                            textFile.writeText(text)
                        }.onFailure {
                            logger.e(it) { "Unable to save media in folder ${messageFolder.absolutePath}" }
                            return@runCatchingLogging false
                        }
                    }
                }
                val specialDataFileName = when (content) {
                    is MediaContent,
                    is TextedContent -> null
                    is ContactContent -> "special.contact"
                    is DiceContent -> "special.dice"
                    is GameContent -> "special.game"
                    is GiveawayContent -> "special.giveaway"
                    is GiveawayPublicResultsContent -> "special.giveawaypublicresults"
                    is InvoiceContent -> "special.invoice"
                    is LiveLocationContent -> "special.livelocation"
                    is StaticLocationContent -> "special.staticlocation"
                    is PollContent -> "special.poll"
                    is StoryContent -> "special.story"
                    is VenueContent -> "special.venue"
                }
                if (specialDataFileName != null) {
                    runCatchingLogging {
                        val specialFile = File(messageFolder, specialDataFileName)
                        val stringifiedContent = when (content) {
                            is ContactContent -> internalJson.encodeToString(ContactContent.serializer(), content)
                            is DiceContent -> internalJson.encodeToString(DiceContent.serializer(), content)
                            is GameContent -> internalJson.encodeToString(GameContent.serializer(), content)
                            is GiveawayContent -> internalJson.encodeToString(GiveawayContent.serializer(), content)
                            is GiveawayPublicResultsContent -> internalJson.encodeToString(GiveawayPublicResultsContent.serializer(), content)
                            is InvoiceContent -> internalJson.encodeToString(InvoiceContent.serializer(), content)
                            is LiveLocationContent -> internalJson.encodeToString(LiveLocationContent.serializer(), content)
                            is StaticLocationContent -> internalJson.encodeToString(StaticLocationContent.serializer(), content)
                            is PollContent -> internalJson.encodeToString(PollContent.serializer(), content)
                            is StoryContent -> internalJson.encodeToString(StoryContent.serializer(), content)
                            is VenueContent -> internalJson.encodeToString(VenueContent.serializer(), content)
                            is MediaContent,
                            is TextedContent -> { /* unreachable branch */ error("Unreachable branch") }
                        }
                        specialFile.delete()
                        specialFile.createNewFile()
                        specialFile.writeText(stringifiedContent)
                    }.onFailure {
                        logger.e(it) { "Unable to save media in folder ${messageFolder.absolutePath}" }
                        return@runCatchingLogging false
                    }
                }

                true
            }.onFailure {
                logger.e(it) { "Unable to save content" }
            }.getOrElse {
                false
            }
        } else {
            false
        }
    }

    override suspend fun saveChatTitle(chatId: IdChatIdentifier, title: String) {
        mutex.withLock {
            val currentChatFile = getChatFolderWithoutLock(chatId, null)
            val newChatFile = File(currentChatFile.parentFile, "${title}_${chatId.chatId.long}")

            if (newChatFile.absolutePath == currentChatFile.absolutePath) return@withLock // nothing to change

            runCatching {
                newChatFile.mkdirs()
            }

            val renamed = runCatching {
                if (currentChatFile.renameTo(newChatFile)) {
                    true
                } else { // unable to just rename
                    runCatching {
                        currentChatFile.listFiles() ?.forEach {
                            val newFile = File(newChatFile, it.name)
                            it.renameTo(newFile)
                        }
                        true
                    }.onFailure {
                        logger.e(it) { "Unable to replace folders from chat folder (new title: $title, chatId: ${chatId.chatId.long})" }
                    }.getOrElse {
                        false
                    }
                }
            }.onFailure {
                logger.e(it) { "Unable to rename chat folder (new title: $title, chatId: ${chatId.chatId.long})" }
            }.getOrElse {
                false
            }

            if (renamed) {
                chatsFiles[chatId.toChatId()] = newChatFile
            }
        }
    }

    override suspend fun saveThreadTitle(chatId: ChatId, threadId: MessageThreadId, title: String) {
        mutex.withLock {
            val currentThreadFile = getThreadFolderWithoutLock(chatId, threadId, null)
            val chatFolder = getChatFolderWithoutLock(chatId, null)
            val newThreadFile = File(chatFolder, "${title}_${threadId.long}")

            if (newThreadFile.absolutePath == currentThreadFile.absolutePath) return@withLock // nothing to change

            runCatching {
                newThreadFile.mkdirs()
            }

            val renamed = runCatching {
                if (currentThreadFile.renameTo(newThreadFile)) {
                    true
                } else { // unable to just rename
                    runCatching {
                        currentThreadFile.listFiles() ?.forEach {
                            val newFile = File(newThreadFile, it.name)
                            it.renameTo(newFile)
                        }
                        true
                    }.onFailure {
                        logger.e(it) { "Unable to replace folders from chat folder (new title: $title, chatId: ${chatId.chatId.long})" }
                    }.getOrElse {
                        false
                    }
                }
            }.onFailure {
                logger.e(it) { "Unable to rename chat folder (new title: $title, chatId: ${chatId.chatId.long})" }
            }.getOrElse {
                false
            }

            if (renamed) {
                threadsFiles[threadId] = newThreadFile
            }
        }
    }

}
