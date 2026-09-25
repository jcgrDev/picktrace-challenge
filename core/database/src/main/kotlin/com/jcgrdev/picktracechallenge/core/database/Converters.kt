package com.jcgrdev.picktracechallenge.core.database

import androidx.room.TypeConverter
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.RunOutcome
import com.jcgrdev.picktracechallenge.core.model.FailureKind
import com.jcgrdev.picktracechallenge.core.model.SyncStatus

/** Enums are stored as their `name` (TEXT). */
internal class Converters {
    @TypeConverter fun syncStatusToText(value: SyncStatus?): String? = value?.name
    @TypeConverter fun textToSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)

    @TypeConverter fun failureKindToText(value: FailureKind?): String? = value?.name
    @TypeConverter fun textToFailureKind(value: String?): FailureKind? = value?.let(FailureKind::valueOf)

    @TypeConverter fun opStateToText(value: OpState?): String? = value?.name
    @TypeConverter fun textToOpState(value: String?): OpState? = value?.let(OpState::valueOf)

    @TypeConverter fun runOutcomeToText(value: RunOutcome?): String? = value?.name
    @TypeConverter fun textToRunOutcome(value: String?): RunOutcome? = value?.let(RunOutcome::valueOf)
}
