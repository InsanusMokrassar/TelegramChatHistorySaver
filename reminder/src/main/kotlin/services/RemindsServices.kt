package dev.inmo.tgchat_history_saver.reminder.services

import dev.inmo.kslog.common.logger
import dev.inmo.kslog.common.w
import dev.inmo.micro_utils.coroutines.launchLoggingDropExceptions
import dev.inmo.micro_utils.coroutines.runCatchingLogging
import dev.inmo.micro_utils.coroutines.subscribeLoggingDropExceptions
import dev.inmo.tgbotapi.libraries.resender.MessagesResender
import dev.inmo.tgchat_history_saver.reminder.repo.RemindersRepo
import korlibs.time.DateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive

class RemindsServices(
    private val remindersRepo: RemindersRepo,
    private val resender: MessagesResender
) {
    private var job: Job? = null
    private val infosFlow = flow {
        emit(Unit)
        merge(
            remindersRepo.onNewValue,
            remindersRepo.onDataCleared,
            remindersRepo.onValueRemoved
        ).collect(this::emit)
    }.map {
        remindersRepo.getAll().values.flatten()
    }
    fun start(scope: CoroutineScope): Job {
        return infosFlow.subscribeLoggingDropExceptions(scope) {
            job ?.cancel()
            job = scope.launchLoggingDropExceptions {
                while (isActive) {
                    val now = DateTime.now()
                    val closestActions = it.mapNotNull {
                        (it.krontabConfig.scheduler().next(now) ?: return@mapNotNull null) to it
                    }.groupBy {
                        it.first
                    }.minByOrNull {
                        it.key
                    } ?: return@launchLoggingDropExceptions
                    delay(closestActions.key - DateTime.now())
                    closestActions.value.forEach {
                        runCatchingLogging {
                            resender.resend(it.second.targetChatId, it.second.messagesInfos).ifEmpty {
                                this@RemindsServices.logger.w("Unable to sent reminder for reminder info: $it")
                            }
                        }
                    }
                }
            }
        }
    }
}