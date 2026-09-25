package com.jcgrdev.picktracechallenge.core.model

/** Opaque id of the worker who recorded an event. Never interchangeable with [BlockId] (FR-003). */
@JvmInline
value class WorkerId(val value: String)

/** Opaque id of the physical area an event applies to. Never interchangeable with [WorkerId] (FR-003). */
@JvmInline
value class BlockId(val value: String)
