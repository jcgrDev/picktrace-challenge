package com.jcgrdev.picktracechallenge.core.network

import kotlinx.serialization.json.Json

/** The one wire format for the sync API. Also used by the codec and by FakeSyncServer. */
val SyncJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
