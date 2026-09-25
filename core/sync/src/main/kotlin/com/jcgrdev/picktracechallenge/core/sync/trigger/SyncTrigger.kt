package com.jcgrdev.picktracechallenge.core.sync.trigger

/** Seam for anything that should cause a sync: connectivity today, FCM later. */
interface SyncTrigger {
    fun start()
    fun stop()
}
