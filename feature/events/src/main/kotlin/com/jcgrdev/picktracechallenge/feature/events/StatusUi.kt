package com.jcgrdev.picktracechallenge.feature.events

import androidx.annotation.StringRes
import com.jcgrdev.picktracechallenge.core.designsystem.component.StatusTone
import com.jcgrdev.picktracechallenge.core.model.SyncStatus

@get:StringRes
internal val SyncStatus.labelRes: Int
    get() = when (this) {
        SyncStatus.PENDING -> R.string.events_status_pending
        SyncStatus.SYNCED -> R.string.events_status_synced
        SyncStatus.FAILED -> R.string.events_status_failed
    }

internal val SyncStatus.tone: StatusTone
    get() = when (this) {
        SyncStatus.PENDING -> StatusTone.Neutral
        SyncStatus.SYNCED -> StatusTone.Success
        SyncStatus.FAILED -> StatusTone.Error
    }
