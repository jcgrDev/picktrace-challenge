package com.jcgrdev.picktracechallenge.core.sync

/** One sync run. [SyncEngine] in production; the worker depends on this so tests can fake it. */
fun interface SyncRunner {
    suspend fun run(): RunResult
}
