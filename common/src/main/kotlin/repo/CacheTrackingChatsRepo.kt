package dev.inmo.tgchat_history_saver.common.repo

import dev.inmo.micro_utils.coroutines.SmartRWLocker
import dev.inmo.micro_utils.coroutines.launchSafelyWithoutExceptions
import dev.inmo.micro_utils.coroutines.withReadAcquire
import dev.inmo.micro_utils.coroutines.withWriteLock
import dev.inmo.tgbotapi.types.ChatId
import kotlinx.coroutines.CoroutineScope

class CacheTrackingChatsRepo(
    private val trackingChatsRepo: TrackingChatsRepo,
    scope: CoroutineScope,
) : TrackingChatsRepo {
    private val locker: SmartRWLocker = SmartRWLocker(writeIsLocked = true)
    private var cache = emptySet<ChatId>()

    init {
        scope.launchSafelyWithoutExceptions {
            runCatching {
                cache = trackingChatsRepo.getTrackingChats()
            }
            locker.unlockWrite()
        }
    }

    override suspend fun add(chatId: ChatId) {
        locker.withWriteLock {
            trackingChatsRepo.add(chatId)
            cache = trackingChatsRepo.getTrackingChats()
        }
    }

    override suspend fun remove(chatId: ChatId) {
        locker.withWriteLock {
            trackingChatsRepo.remove(chatId)
            cache = trackingChatsRepo.getTrackingChats()
        }
    }

    override suspend fun getTrackingChats(): Set<ChatId> = locker.withReadAcquire {
        cache.toSet()
    }
}
