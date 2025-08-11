package dev.inmo.tgchat_history_saver.reminder.repo

import dev.inmo.krontab.KrontabConfig
import dev.inmo.micro_utils.repos.KeyValuesRepo
import dev.inmo.micro_utils.repos.cache.full.fullyCached
import dev.inmo.micro_utils.repos.exposed.ColumnAllocator
import dev.inmo.micro_utils.repos.exposed.onetomany.AbstractExposedKeyValuesRepo
import dev.inmo.micro_utils.repos.exposed.onetomany.ExposedKeyValuesRepo
import dev.inmo.micro_utils.repos.mappers.withMapper
import dev.inmo.tgchat_history_saver.reminder.models.ReminderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

interface RemindersRepo : KeyValuesRepo<KrontabConfig, ReminderInfo>

class ExposedRemindersRepo(
    database: Database,
    json: Json
) : RemindersRepo, KeyValuesRepo<KrontabConfig, ReminderInfo> by ExposedKeyValuesRepo(
    database,
    { text("schedule") },
    { text("info") },
    "reminders"
).withMapper(
    { json.encodeToString(KrontabConfig.serializer(), this) },
    { json.encodeToString(ReminderInfo.serializer(), this) },
    { json.decodeFromString(KrontabConfig.serializer(), this) },
    { json.decodeFromString(ReminderInfo.serializer(), this) },
)

class CacheRemindersRepo(
    remindersRepo: RemindersRepo,
    scope: CoroutineScope
) : RemindersRepo, KeyValuesRepo<KrontabConfig, ReminderInfo> by remindersRepo.fullyCached(scope = scope)
